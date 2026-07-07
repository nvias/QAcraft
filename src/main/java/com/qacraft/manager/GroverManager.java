package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import java.util.*;

/**
 * Grover's search — supports MULTIPLE independent instances at once.
 *
 * Every instance carries an integer id (gid). All of its entities (slot markers,
 * chest markers, labels) are tagged with that gid via {@link com.qacraft.util.QAcraftEntity#setGid}.
 * Commands target an instance by id, e.g. {@code /qacraft grover setup 1},
 * {@code /qacraft grover fillwool 1}. When only one instance exists the id may be
 * omitted. Throwing a Wind Charge iterates the nearest instance.
 */
public class GroverManager implements Listener {
    private final QAcraftPlugin plugin;

    /** Per-instance iteration count: gid -> k. */
    private final Map<Integer, Integer> iterations = new HashMap<>();

    /**
     * Current search target per instance: gid -> material.
     * Set when a player first observes an instance through the spyglass. If the
     * observed offhand item CHANGES, that instance's iteration count is reset to
     * zero — searching for a new item starts a fresh Grover run.
     */
    private final Map<Integer, Material> instanceSearch = new HashMap<>();

    /** Set true once a player observes an iterated grid through the spyglass (a column renders). */
    private boolean observed = false;
    public boolean wasObserved() { return observed; }
    public void resetObserved() { observed = false; }

    public static final String GROVER_CHEST = "q_grover_chest";
    public static final String GROVER_LABEL = "q_grover_label";
    public static final String GROVER_SLOT  = "q_grover_slot";

    /** The 8 distinct colors used by /qacraft grover fillwool */
    private static final Material[] FILL_COLORS = {
        Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
        Material.CYAN_WOOL, Material.MAGENTA_WOOL, Material.WHITE_WOOL, Material.BLACK_WOOL
    };

    // Ring pattern offsets (3×3 without center)
    // Layout:
    //   1 2 3
    //   8   4
    //   7 6 5
    private static final int[][] PATTERN = {
        {-2, 0, -2}, // 1: back-left
        { 0, 0, -2}, // 2: back-center
        { 2, 0, -2}, // 3: back-right
        { 2, 0,  0}, // 4: middle-right
        { 2, 0,  2}, // 5: front-right
        { 0, 0,  2}, // 6: front-center
        {-2, 0,  2}, // 7: front-left
        {-2, 0,  0}, // 8: middle-left
    };

    public GroverManager(QAcraftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Grover probability — physically accurate
     * P(correct) = sin²((2k+1)θ)  where θ = arcsin(1/√N), N=8
     */
    private double correctProbability(int iteration) {
        double theta = Math.asin(1.0 / Math.sqrt(8.0));
        return Math.pow(Math.sin((2 * iteration + 1) * theta), 2);
    }

    private double wrongProbability(int iteration) {
        return (1.0 - correctProbability(iteration)) / 7.0;
    }

    // =========================================================================
    // Instance helpers
    // =========================================================================

    /** All instance ids currently present in the world (slots + chests). */
    private Set<Integer> activeGids(World w) {
        Set<Integer> gids = new TreeSet<>();
        for (Entity e : tagged(w, GROVER_SLOT))  gids.add(gid(e));
        for (Entity e : tagged(w, GROVER_CHEST)) gids.add(gid(e));
        return gids;
    }

    private int nextFreeGid(Set<Integer> active) {
        for (int i = 1; i <= 999; i++) if (!active.contains(i)) return i;
        return 1;
    }

    /**
     * Resolve a command-supplied id (-1 = not specified). Returns a valid active
     * id, or -1 after sending an explanatory message to the player.
     */
    private int resolve(Player p, int gid) {
        Set<Integer> active = activeGids(p.getWorld());
        if (gid > 0) {
            if (!active.contains(gid)) {
                p.sendMessage(Component.text("No Grover instance #" + gid + ". Active: "
                    + (active.isEmpty() ? "none" : active), NamedTextColor.RED));
                return -1;
            }
            return gid;
        }
        if (active.isEmpty()) {
            p.sendMessage(Component.text("No active Grover instances. Use ", NamedTextColor.RED)
                .append(Component.text("/qacraft grover setup <id>", NamedTextColor.YELLOW)));
            return -1;
        }
        if (active.size() == 1) return active.iterator().next();
        p.sendMessage(Component.text("Multiple instances active: " + active
            + " — specify which, e.g. ", NamedTextColor.YELLOW)
            .append(Component.text("... " + active.iterator().next(), NamedTextColor.GOLD)));
        return -1;
    }

    private Entity findGroverLabel(World w, int instanceId, int slotNum) {
        for (Entity e : tagged(w, GROVER_LABEL)) {
            if (gid(e) == instanceId && num(e) == slotNum) return e;
        }
        return null;
    }

    /** Geometric centre of an instance (average of its slot/chest entity positions). */
    private Location centerOf(World w, int instanceId) {
        double x = 0, y = 0, z = 0; int n = 0;
        for (String tag : new String[]{GROVER_SLOT, GROVER_CHEST}) {
            for (Entity e : tagged(w, tag)) {
                if (gid(e) != instanceId) continue;
                Location l = e.getLocation();
                x += l.getX(); y += l.getY(); z += l.getZ(); n++;
            }
        }
        if (n == 0) return null;
        return new Location(w, x / n, y / n, z / n);
    }

    // =========================================================================
    // Setup
    // =========================================================================

    public void setup(Player p, int gid) {
        World w = p.getWorld();
        Set<Integer> active = activeGids(w);
        if (gid <= 0) gid = nextFreeGid(active);
        if (active.contains(gid)) {
            p.sendMessage(Component.text("Grover instance #" + gid + " already exists — clear it first: ",
                NamedTextColor.RED).append(Component.text("/qacraft grover clear " + gid, NamedTextColor.YELLOW)));
            return;
        }
        iterations.put(gid, 0);

        Location center = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);

        for (int i = 0; i < 8; i++) {
            Location slotLoc = center.clone().add(PATTERN[i][0], PATTERN[i][1], PATTERN[i][2]);

            Entity marker = w.spawnEntity(slotLoc, EntityType.MARKER);
            marker.addScoreboardTag(GROVER_SLOT);
            setNum(marker, i + 1);
            setGid(marker, gid);

            TextDisplay label = (TextDisplay) w.spawnEntity(slotLoc.clone().add(0, 1.5, 0), EntityType.TEXT_DISPLAY);
            label.text(Component.text((i + 1) + "", NamedTextColor.GOLD));
            label.setBillboard(Display.Billboard.CENTER);
            label.setShadowed(true);
            label.setTransformation(new Transformation(
                new Vector3f(), new Quaternionf(), new Vector3f(1.0f), new Quaternionf()));
            label.addScoreboardTag(GROVER_LABEL);
            setNum(label, i + 1);
            setGid(label, gid);
        }

        // Big, bold instance-id display floating in the centre block of the ring
        TextDisplay idDisplay = (TextDisplay) w.spawnEntity(center.clone().add(0, 1.5, 0), EntityType.TEXT_DISPLAY);
        idDisplay.text(Component.text(String.valueOf(gid), NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
        idDisplay.setBillboard(Display.Billboard.CENTER);
        idDisplay.setShadowed(true);
        idDisplay.setTransformation(new Transformation(
            new Vector3f(), new Quaternionf(), new Vector3f(2.8f), new Quaternionf()));
        idDisplay.addScoreboardTag(GROVER_LABEL);
        setNum(idDisplay, 0); // 0 = centre id display (slots are 1-8)
        setGid(idDisplay, gid);

        p.sendMessage(Component.text("Grover #" + gid + " — ", NamedTextColor.GOLD)
            .append(Component.text("8 positions marked in ring pattern!", NamedTextColor.WHITE)));
        p.sendMessage(Component.text("Place chests or use ", NamedTextColor.GRAY)
            .append(Component.text("/qacraft grover placechests " + gid, NamedTextColor.YELLOW))
            .append(Component.text(" to auto-place.", NamedTextColor.GRAY)));
        w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
    }

    // =========================================================================
    // Place chests
    // =========================================================================

    public void placeChests(Player p, int gid) {
        gid = resolve(p, gid);
        if (gid < 0) return;
        World w = p.getWorld();
        int placed = 0;

        for (Entity slot : new ArrayList<>(tagged(w, GROVER_SLOT))) {
            if (gid(slot) != gid) continue;
            Location loc = slot.getLocation();
            loc.getBlock().setType(Material.CHEST);
            placed++;

            int slotNum = num(slot);
            slot.getScoreboardTags().remove(GROVER_SLOT);
            slot.addScoreboardTag(GROVER_CHEST);
            slot.teleport(loc.clone().add(0, 1, 0));

            Entity label = findGroverLabel(w, gid, slotNum);
            if (label instanceof TextDisplay td) {
                td.text(Component.text("" + slotNum, NamedTextColor.GREEN));
                td.teleport(loc.clone().add(0, 1.5, 0));
            }
        }

        if (placed > 0) {
            p.sendMessage(Component.text(placed + " chests placed for Grover #" + gid + "!", NamedTextColor.GREEN)
                .append(Component.text(" Put any item in each chest to search for it.", NamedTextColor.GRAY)));
            w.playSound(p.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.0f);
        } else {
            p.sendMessage(Component.text("No empty slots for Grover #" + gid + ".", NamedTextColor.RED));
        }
    }

    // =========================================================================
    // Fill wool
    // =========================================================================

    public void fillWool(Player p, int gid) {
        gid = resolve(p, gid);
        if (gid < 0) return;
        World w = p.getWorld();

        List<Entity> chests = new ArrayList<>();
        for (Entity e : tagged(w, GROVER_CHEST)) if (gid(e) == gid) chests.add(e);

        if (chests.isEmpty()) {
            p.sendMessage(Component.text("No chests for Grover #" + gid + ". Run setup + placechests first.",
                NamedTextColor.RED));
            return;
        }

        List<Material> shuffled = new ArrayList<>(Arrays.asList(FILL_COLORS));
        Collections.shuffle(shuffled);
        chests.sort((a, b) -> num(a) - num(b));

        int filled = 0;
        for (int i = 0; i < chests.size() && i < shuffled.size(); i++) {
            Block chestBlock = chests.get(i).getLocation().getBlock().getRelative(0, -1, 0);
            if (chestBlock.getType() != Material.CHEST) continue;
            Container container = (Container) chestBlock.getState();
            Inventory inv = container.getInventory();
            inv.clear();
            inv.setItem(0, new ItemStack(shuffled.get(i), 1));
            filled++;
        }

        p.sendMessage(Component.text("Filled " + filled + " chests for Grover #" + gid + " with shuffled wool!",
            NamedTextColor.GREEN)
            .append(Component.text(" Hold wool (offhand) + spyglass to search.", NamedTextColor.GRAY)));
        w.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
    }

    // =========================================================================
    // Iterate
    // =========================================================================

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof WindCharge wc)) return;
        if (!(wc.getShooter() instanceof Player p)) return;

        int gid = nearestGid(p);
        if (gid < 0) return; // no Grover instances — let the charge fly normally

        e.setCancelled(true);
        iterate(p, gid);
    }

    /** Find the instance whose nearest entity is closest to the player. */
    private int nearestGid(Player p) {
        World w = p.getWorld();
        Location pl = p.getLocation();
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int gid : activeGids(w)) {
            for (String tag : new String[]{GROVER_SLOT, GROVER_CHEST}) {
                for (Entity e : tagged(w, tag)) {
                    if (gid(e) != gid) continue;
                    double d = e.getLocation().distanceSquared(pl);
                    if (d < bestDist) { bestDist = d; best = gid; }
                }
            }
        }
        return best;
    }

    /** Place chests for the Grover instance nearest the player (used by the Place Chests tool). */
    public void placeChestsNearest(Player p) {
        int gid = nearestGid(p);
        if (gid < 0) {
            p.sendMessage(Component.text("No Grover grid nearby. Use the Grover Setup tool first.", NamedTextColor.RED));
            return;
        }
        placeChests(p, gid);
    }

    public void iterate(Player p, int gid) {
        gid = resolve(p, gid);
        if (gid < 0) return;

        int iteration = iterations.getOrDefault(gid, 0) + 1;
        iterations.put(gid, iteration);

        World w = p.getWorld();
        Location loc = centerOf(w, gid);
        if (loc == null) loc = p.getLocation();
        final Location fx = loc;
        final int fIter = iteration;
        final int fGid = gid;

        w.spawnParticle(Particle.END_ROD, fx.clone().add(0, 1, 0), 80, 4, 2, 4, 0.05);
        w.spawnParticle(Particle.REVERSE_PORTAL, fx.clone().add(0, 1, 0), 120, 5, 3, 5, 0.1);
        w.spawnParticle(Particle.ENCHANT, fx.clone().add(0, 2, 0), 60, 3, 2, 3, 1.0);

        w.playSound(fx, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
        w.playSound(fx, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.6f);

        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(800), Duration.ofMillis(200));
        p.showTitle(Title.title(
            Component.text("Oracle Applied", NamedTextColor.GOLD),
            Component.text("Grover #" + gid + " — marking the target state...", NamedTextColor.GRAY),
            times));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            p.showTitle(Title.title(
                Component.text("Diffusion Applied", NamedTextColor.AQUA),
                Component.text("Grover #" + fGid + " — iteration " + fIter + " — P(correct) = "
                    + String.format("%.1f%%", correctProbability(fIter) * 100), NamedTextColor.GRAY),
                times));
            w.spawnParticle(Particle.SONIC_BOOM, fx.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
            w.playSound(fx, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
        }, 25L);

        double prob = correctProbability(iteration);
        if (prob > 0.9) {
            p.sendMessage(Component.text("Grover #" + gid + " iteration " + iteration, NamedTextColor.GOLD)
                .append(Component.text(String.format(" — P(correct) = %.1f%% ✓ optimal!", prob * 100), NamedTextColor.GREEN)));
        } else if (iteration > 2) {
            p.sendMessage(Component.text("Grover #" + gid + " iteration " + iteration, NamedTextColor.GOLD)
                .append(Component.text(String.format(" — P(correct) = %.1f%% — over-rotation!", prob * 100), NamedTextColor.RED)));
        } else {
            p.sendMessage(Component.text("Grover #" + gid + " iteration " + iteration, NamedTextColor.GOLD)
                .append(Component.text(String.format(" — P(correct) = %.1f%%", prob * 100), NamedTextColor.GRAY)));
        }
    }

    // =========================================================================
    // Reset / clear / list
    // =========================================================================

    public void reset(Player p, int gid) {
        gid = resolve(p, gid);
        if (gid < 0) return;
        iterations.put(gid, 0);
        instanceSearch.remove(gid);
        restoreLabels(p.getWorld(), gid);
        p.sendMessage(Component.text("Grover #" + gid + " reset — all amplitudes equal (P = 12.5% each)",
            NamedTextColor.AQUA));
        p.showTitle(Title.title(
            Component.text("Reset", NamedTextColor.WHITE),
            Component.text("Grover #" + gid + " — all amplitudes equal", NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(600), Duration.ofMillis(200))));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
    }

    /** Remove a single instance's entities (chest blocks are left in place). */
    public void clearOne(Player p, int gid) {
        gid = resolve(p, gid);
        if (gid < 0) return;
        World w = p.getWorld();
        int removed = 0;
        for (String tag : new String[]{GROVER_SLOT, GROVER_CHEST, GROVER_LABEL}) {
            for (Entity e : new ArrayList<>(tagged(w, tag))) {
                if (gid(e) == gid) { e.remove(); removed++; }
            }
        }
        iterations.remove(gid);
        instanceSearch.remove(gid);
        p.sendMessage(Component.text("Grover #" + gid + " cleared (" + removed + " entities).", NamedTextColor.GRAY));
    }

    /**
     * Deactivate the Grover instance whose EXACT centre block equals {@code loc}
     * (the block directly under the instance's floating id label), freeing its id.
     * Returns true if one was cleared. Used by the Grover Reset TNT tool.
     */
    public boolean clearInstanceAt(Location loc) {
        World w = loc.getWorld();
        int target = -1;
        for (int gid : activeGids(w)) {
            Location c = centerOf(w, gid);
            if (c == null) continue;
            if (c.getBlockX() == loc.getBlockX() && c.getBlockZ() == loc.getBlockZ()) { target = gid; break; }
        }
        if (target < 0) return false;
        // Remove the physical chest blocks under this grid's chest markers
        for (Entity e : tagged(w, GROVER_CHEST)) {
            if (gid(e) != target) continue;
            Block below = e.getLocation().getBlock().getRelative(0, -1, 0);
            if (below.getType() == Material.CHEST) below.setType(Material.AIR, false);
        }
        for (String tag : new String[]{GROVER_SLOT, GROVER_CHEST, GROVER_LABEL}) {
            for (Entity e : new ArrayList<>(tagged(w, tag))) if (gid(e) == target) e.remove();
        }
        iterations.remove(target);
        instanceSearch.remove(target);
        w.spawnParticle(Particle.EXPLOSION, loc, 1);
        w.spawnParticle(Particle.LARGE_SMOKE, loc, 25, 0.6, 0.6, 0.6, 0.05);
        w.playSound(loc, Sound.ENTITY_TNT_PRIMED, 1f, 1.2f);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.3f);
        return true;
    }

    /** Remove ALL instances in every world. Used by the global /qacraft clear and onDisable. */
    public void clear() {
        iterations.clear();
        instanceSearch.clear();
        observed = false;
        for (World w : plugin.getServer().getWorlds()) {
            killAll(w, GROVER_CHEST);
            killAll(w, GROVER_LABEL);
            killAll(w, GROVER_SLOT);
        }
    }

    public void list(Player p) {
        Set<Integer> active = activeGids(p.getWorld());
        if (active.isEmpty()) {
            p.sendMessage(Component.text("No active Grover instances.", NamedTextColor.GRAY));
            return;
        }
        p.sendMessage(Component.text("== Active Grover instances ==", NamedTextColor.GOLD));
        World w = p.getWorld();
        for (int gid : active) {
            int chests = 0, slots = 0;
            for (Entity e : tagged(w, GROVER_CHEST)) if (gid(e) == gid) chests++;
            for (Entity e : tagged(w, GROVER_SLOT))  if (gid(e) == gid) slots++;
            int it = iterations.getOrDefault(gid, 0);
            p.sendMessage(Component.text("#" + gid, NamedTextColor.YELLOW)
                .append(Component.text(" — iteration " + it + ", chests " + chests + "/8"
                    + (slots > 0 ? ", " + slots + " empty slots" : ""), NamedTextColor.GRAY)));
        }
    }

    // =========================================================================
    // Block placement — convert chest placed on a slot marker into a Grover chest
    // =========================================================================

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (e.getBlock().getType() != Material.CHEST) return;

        Location placed = e.getBlock().getLocation().add(0.5, 0, 0.5);
        World w = e.getBlock().getWorld();

        for (Entity marker : tagged(w, GROVER_SLOT)) {
            if (marker.getLocation().distance(placed) >= 1.5) continue;

            int gid = gid(marker);
            int slotNum = num(marker);
            marker.getScoreboardTags().remove(GROVER_SLOT);
            marker.addScoreboardTag(GROVER_CHEST);
            marker.teleport(placed.clone().add(0, 1, 0));

            Entity label = findGroverLabel(w, gid, slotNum);
            if (label instanceof TextDisplay td) {
                td.text(Component.text("" + slotNum, NamedTextColor.GREEN));
                td.teleport(placed.clone().add(0, 1.5, 0));
            }

            e.getPlayer().sendMessage(Component.text("Chest placed at Grover #" + gid + " slot " + slotNum,
                NamedTextColor.GREEN));
            w.playSound(placed, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);

            int chests = 0;
            for (Entity c : tagged(w, GROVER_CHEST)) if (gid(c) == gid) chests++;
            if (chests >= 8) {
                e.getPlayer().sendMessage(Component.text("Grover #" + gid + " — all 8 chests placed! ",
                    NamedTextColor.GOLD).append(Component.text("Throw Wind Charge to iterate.", NamedTextColor.GRAY)));
            }
            return;
        }
    }

    // =========================================================================
    // Tick — outlines for every instance; probability columns per instance
    // =========================================================================

    public void tick() {
        // Slot outlines always visible (pre- and post-iteration), all instances
        for (World w : plugin.getServer().getWorlds()) {
            for (Entity slot : tagged(w, GROVER_SLOT)) {
                drawSlotOutline(w, slot.getLocation());
            }
            // If a placed chest was destroyed, show the outline again on its spot
            for (Entity marker : tagged(w, GROVER_CHEST)) {
                org.bukkit.block.Block below = marker.getLocation().getBlock().getRelative(0, -1, 0);
                if (below.getType() != Material.CHEST) drawSlotOutline(w, below.getLocation());
            }
        }

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            ItemStack mainHand = p.getInventory().getItemInMainHand();
            if (mainHand.getType() != Material.SPYGLASS) continue;

            ItemStack offHand = p.getInventory().getItemInOffHand();
            if (offHand.getType() == Material.AIR) continue;

            World w = p.getWorld();
            Material held = offHand.getType();
            Set<Integer> handledThisTick = new HashSet<>();

            for (Entity marker : tagged(w, GROVER_CHEST)) {
                Location mLoc = marker.getLocation();
                if (mLoc.distance(p.getLocation()) > 20) continue;

                int gid = gid(marker);

                // Once per instance per tick: if the searched item changed, start over
                if (handledThisTick.add(gid)) {
                    Material prev = instanceSearch.get(gid);
                    if (prev == null) {
                        instanceSearch.put(gid, held);
                    } else if (prev != held) {
                        instanceSearch.put(gid, held);
                        iterations.put(gid, 0);          // new item → fresh search
                        restoreLabels(w, gid);
                    }
                }

                int iteration = iterations.getOrDefault(gid, 0);
                if (iteration == 0) continue; // no columns until first iteration

                Block chestBlock = mLoc.getBlock().getRelative(0, -1, 0);
                if (chestBlock.getType() != Material.CHEST) continue;

                Container container = (Container) chestBlock.getState();
                boolean match = chestContainsItem(container.getInventory(), held);
                spawnProbabilityColumn(w, mLoc, match, marker, iteration);
                observed = true; // a probability column was shown → player has observed
            }
        }
    }

    /** Restore an instance's chest labels back to their green slot numbers. */
    private void restoreLabels(World w, int gid) {
        for (Entity marker : tagged(w, GROVER_CHEST)) {
            if (gid(marker) != gid) continue;
            Entity label = findGroverLabel(w, gid, num(marker));
            if (label instanceof TextDisplay td) {
                td.text(Component.text("" + num(marker), NamedTextColor.GREEN));
            }
        }
    }

    /** Check if the inventory contains at least one stack of the target item type. */
    private boolean chestContainsItem(Inventory inv, Material target) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == target) return true;
        }
        return false;
    }

    private void drawSlotOutline(World w, Location center) {
        if (w.getFullTime() % 5 != 0) return;

        double x = center.getBlockX();
        double y = center.getBlockY();
        double z = center.getBlockZ();
        double step = 0.25;

        for (double d = 0; d <= 1; d += step) {
            w.spawnParticle(Particle.END_ROD, x + d, y + 0.01, z,     1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x + d, y + 0.01, z + 1, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x,     y + 0.01, z + d, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x + 1, y + 0.01, z + d, 1, 0, 0, 0, 0);
        }

        for (double dy = 0; dy <= 1; dy += step) {
            w.spawnParticle(Particle.END_ROD, x,     y + dy, z,     1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x + 1, y + dy, z,     1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x,     y + dy, z + 1, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x + 1, y + dy, z + 1, 1, 0, 0, 0, 0);
        }
    }

    private void spawnProbabilityColumn(World w, Location loc, boolean match, Entity marker, int iteration) {
        double maxHeight  = 3.5;
        double matchHeight = correctProbability(iteration) * maxHeight;

        double columnHeight;
        if (match) {
            columnHeight = matchHeight;
        } else {
            double raw = wrongProbability(iteration) * maxHeight;
            // Boost gray columns so they are always at least 10% of the green column height
            // (makes them visible at high iteration counts), but hard-cap below green.
            columnHeight = Math.min(Math.max(raw, matchHeight * 0.10), matchHeight - 0.3);
            if (columnHeight < 0) columnHeight = raw; // safety: matchHeight very small
        }

        double step = 0.3;
        if (match) {
            for (double y = 0; y < columnHeight; y += step) {
                w.spawnParticle(Particle.HAPPY_VILLAGER,
                    loc.getX(), loc.getY() + y, loc.getZ(), 1, 0.02, 0, 0.02, 0);
            }
            if (columnHeight > 0.5) {
                w.spawnParticle(Particle.END_ROD,
                    loc.getX(), loc.getY() + columnHeight, loc.getZ(),
                    2, 0.05, 0.05, 0.05, 0.005);
            }
            updateLabel(w, marker, "✓", NamedTextColor.GREEN);
        } else {
            Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.SILVER, 0.8f);
            for (double y = 0; y < columnHeight; y += step) {
                w.spawnParticle(Particle.DUST,
                    loc.getX(), loc.getY() + y, loc.getZ(), 1, 0.02, 0, 0.02, 0, dust);
            }
            updateLabel(w, marker, "·", NamedTextColor.GRAY);
        }
    }

    private void updateLabel(World w, Entity marker, String text, NamedTextColor color) {
        Entity label = findGroverLabel(w, gid(marker), num(marker));
        if (label instanceof TextDisplay td) {
            td.text(Component.text(text, color));
        }
    }

    /** Highest iteration count across all active instances (0 = none iterated). */
    public int highestIteration() {
        int max = 0;
        for (int v : iterations.values()) max = Math.max(max, v);
        return max;
    }

    // =========================================================================
    // State persistence
    // =========================================================================

    public void saveState(ConfigurationSection s) {
        Map<String, Integer> it = new HashMap<>();
        iterations.forEach((g, i) -> it.put(String.valueOf(g), i));
        s.set("iterations", it);
    }

    public void loadState(ConfigurationSection s) {
        ConfigurationSection it = s.getConfigurationSection("iterations");
        if (it != null) {
            for (String k : it.getKeys(false)) {
                try { iterations.put(Integer.parseInt(k), it.getInt(k)); }
                catch (Exception ignored) { /* skip malformed entries */ }
            }
        }
    }
}
