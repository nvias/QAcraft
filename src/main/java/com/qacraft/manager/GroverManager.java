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
import net.kyori.adventure.title.Title;
import java.time.Duration;
import java.util.*;

public class GroverManager implements Listener {
    private final QAcraftPlugin plugin;
    private int iteration = 0;
    private boolean setupActive = false;

    // Per-player search-color lock: locked permanently on first use after the
    // first iteration; only released on reset/clear.
    // If a player holds a DIFFERENT item afterwards, all columns show equal gray.
    private final Map<UUID, Material> lockedSearchColor = new HashMap<>();

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
    private double correctProbability() {
        double theta = Math.asin(1.0 / Math.sqrt(8.0));
        return Math.pow(Math.sin((2 * iteration + 1) * theta), 2);
    }

    private double wrongProbability() {
        return (1.0 - correctProbability()) / 7.0;
    }

    public void setup(Player p) {
        World w = p.getWorld();
        clear();
        iteration = 0;
        setupActive = true;

        Location center = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);

        for (int i = 0; i < 8; i++) {
            Location slotLoc = center.clone().add(PATTERN[i][0], PATTERN[i][1], PATTERN[i][2]);

            Entity marker = w.spawnEntity(slotLoc, EntityType.MARKER);
            marker.addScoreboardTag(GROVER_SLOT);
            setNum(marker, i + 1);

            TextDisplay label = (TextDisplay) w.spawnEntity(slotLoc.clone().add(0, 1.5, 0), EntityType.TEXT_DISPLAY);
            label.text(Component.text((i + 1) + "", NamedTextColor.GOLD));
            label.setBillboard(Display.Billboard.CENTER);
            label.setShadowed(true);
            label.setTransformation(new Transformation(
                new Vector3f(), new Quaternionf(), new Vector3f(1.0f), new Quaternionf()));
            label.addScoreboardTag(GROVER_LABEL);
            setNum(label, i + 1);
        }

        p.sendMessage(Component.text("Grover's Search — ", NamedTextColor.GOLD)
            .append(Component.text("8 positions marked in ring pattern!", NamedTextColor.WHITE)));
        p.sendMessage(Component.text("Place chests or use ", NamedTextColor.GRAY)
            .append(Component.text("/qacraft grover placechests", NamedTextColor.YELLOW))
            .append(Component.text(" to auto-place.", NamedTextColor.GRAY)));
        w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
    }

    /**
     * Auto-place chests on all marked slots.
     */
    public void placeChests(Player p) {
        World w = p.getWorld();
        int placed = 0;

        for (Entity slot : new ArrayList<>(tagged(w, GROVER_SLOT))) {
            Location loc = slot.getLocation();
            Block b = loc.getBlock();

            b.setType(Material.CHEST);
            placed++;

            int slotNum = num(slot);

            slot.getScoreboardTags().remove(GROVER_SLOT);
            slot.addScoreboardTag(GROVER_CHEST);
            slot.teleport(loc.clone().add(0, 1, 0));

            Entity label = findLabel(w, GROVER_LABEL, slotNum);
            if (label instanceof TextDisplay td) {
                td.text(Component.text("" + slotNum, NamedTextColor.GREEN));
                td.teleport(loc.clone().add(0, 1.5, 0));
            }
        }

        if (placed > 0) {
            p.sendMessage(Component.text(placed + " chests placed!", NamedTextColor.GREEN)
                .append(Component.text(" Put any item in each chest to search for it.", NamedTextColor.GRAY)));
            w.playSound(p.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.0f);
        } else {
            p.sendMessage(Component.text("No empty slots found. Run /qacraft grover setup first.", NamedTextColor.RED));
        }
    }

    /**
     * Randomly distribute the 8 fill colors (one per chest) into the chests.
     */
    public void fillWool(Player p) {
        World w = p.getWorld();
        List<Entity> chests = new ArrayList<>(tagged(w, GROVER_CHEST));

        if (chests.isEmpty()) {
            p.sendMessage(Component.text("No Grover chests found. Run setup + placechests first.", NamedTextColor.RED));
            return;
        }

        // Shuffle 8 colors
        List<Material> shuffled = new ArrayList<>(Arrays.asList(FILL_COLORS));
        Collections.shuffle(shuffled);

        // Sort markers by slot number so index → color is deterministic
        chests.sort((a, b) -> num(a) - num(b));

        int filled = 0;
        for (int i = 0; i < chests.size() && i < shuffled.size(); i++) {
            Entity marker = chests.get(i);
            Block chestBlock = marker.getLocation().getBlock().getRelative(0, -1, 0);
            if (chestBlock.getType() != Material.CHEST) continue;

            Container container = (Container) chestBlock.getState();
            Inventory inv = container.getInventory();
            inv.clear();
            inv.setItem(0, new ItemStack(shuffled.get(i), 1));
            filled++;
        }

        p.sendMessage(Component.text("Filled " + filled + " chests with shuffled wool colors!", NamedTextColor.GREEN)
            .append(Component.text(" Hold any wool (offhand) + spyglass to search.", NamedTextColor.GRAY)));
        w.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof WindCharge wc)) return;
        if (!(wc.getShooter() instanceof Player p)) return;
        if (!setupActive && tagged(p.getWorld(), GROVER_CHEST).isEmpty()) return;

        e.setCancelled(true);
        iterate(p);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!setupActive) return;
        if (e.getBlock().getType() != Material.CHEST) return;

        Location placed = e.getBlock().getLocation().add(0.5, 0, 0.5);
        World w = e.getBlock().getWorld();

        for (Entity marker : tagged(w, GROVER_SLOT)) {
            if (marker.getLocation().distance(placed) < 1.5) {
                int slotNum = num(marker);
                marker.getScoreboardTags().remove(GROVER_SLOT);
                marker.addScoreboardTag(GROVER_CHEST);
                marker.teleport(placed.clone().add(0, 1, 0));

                Entity label = findLabel(w, GROVER_LABEL, slotNum);
                if (label instanceof TextDisplay td) {
                    td.text(Component.text("" + slotNum, NamedTextColor.GREEN));
                    td.teleport(placed.clone().add(0, 1.5, 0));
                }

                e.getPlayer().sendMessage(Component.text("Chest placed at slot " + slotNum, NamedTextColor.GREEN));
                w.playSound(placed, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);

                if (tagged(w, GROVER_CHEST).size() >= 8) {
                    e.getPlayer().sendMessage(Component.text("All 8 chests placed! ", NamedTextColor.GOLD)
                        .append(Component.text("Throw Wind Charge to iterate.", NamedTextColor.GRAY)));
                }
                return;
            }
        }
    }

    public void iterate(Player p) {
        iteration++;
        lockedSearchColor.clear(); // release lock — player can target a new block this iteration

        World w = p.getWorld();
        Location loc = p.getLocation();

        w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 80, 4, 2, 4, 0.05);
        w.spawnParticle(Particle.REVERSE_PORTAL, loc.clone().add(0, 1, 0), 120, 5, 3, 5, 0.1);
        w.spawnParticle(Particle.ENCHANT, loc.clone().add(0, 2, 0), 60, 3, 2, 3, 1.0);

        w.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
        w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.6f);

        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(800), Duration.ofMillis(200));

        p.showTitle(Title.title(
            Component.text("Oracle Applied", NamedTextColor.GOLD),
            Component.text("Marking the target state...", NamedTextColor.GRAY),
            times
        ));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            p.showTitle(Title.title(
                Component.text("Diffusion Applied", NamedTextColor.AQUA),
                Component.text("Iteration " + iteration + " — P(correct) = " +
                    String.format("%.1f%%", correctProbability() * 100), NamedTextColor.GRAY),
                times
            ));
            w.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
            w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
        }, 25L);

        double prob = correctProbability();
        if (prob > 0.9) {
            p.sendMessage(Component.text("Grover iteration " + iteration, NamedTextColor.GOLD)
                .append(Component.text(String.format(" — P(correct) = %.1f%% ✓ optimal!", prob * 100), NamedTextColor.GREEN)));
        } else if (iteration > 2) {
            p.sendMessage(Component.text("Grover iteration " + iteration, NamedTextColor.GOLD)
                .append(Component.text(String.format(" — P(correct) = %.1f%% — over-rotation!", prob * 100), NamedTextColor.RED)));
        } else {
            p.sendMessage(Component.text("Grover iteration " + iteration, NamedTextColor.GOLD)
                .append(Component.text(String.format(" — P(correct) = %.1f%%", prob * 100), NamedTextColor.GRAY)));
        }
    }

    public void reset(Player p) {
        iteration = 0;
        lockedSearchColor.clear();
        p.sendMessage(Component.text("Grover reset — all amplitudes equal (P = 12.5% each)", NamedTextColor.AQUA));
        p.showTitle(Title.title(
            Component.text("Reset", NamedTextColor.WHITE),
            Component.text("All amplitudes equal", NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(600), Duration.ofMillis(200))
        ));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
    }

    public void clear() {
        iteration = 0;
        setupActive = false;
        lockedSearchColor.clear();
        for (World w : plugin.getServer().getWorlds()) {
            killAll(w, GROVER_CHEST);
            killAll(w, GROVER_LABEL);
            killAll(w, GROVER_SLOT);
        }
    }

    public void tick() {
        // Slot outlines always visible (pre- and post-iteration)
        for (World w : plugin.getServer().getWorlds()) {
            for (Entity slot : tagged(w, GROVER_SLOT)) {
                drawSlotOutline(w, slot.getLocation());
            }
        }

        // Probability columns only after the first Wind Charge iteration
        if (iteration == 0) return;

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            ItemStack mainHand = p.getInventory().getItemInMainHand();
            if (mainHand.getType() != Material.SPYGLASS) continue;

            ItemStack offHand = p.getInventory().getItemInOffHand();
            if (offHand.getType() == Material.AIR) continue;

            UUID uid = p.getUniqueId();
            // Lock the search material ONCE — never changes until reset/clear.
            // Any subsequent offhand swap shows equal gray (no amplification visible).
            if (!lockedSearchColor.containsKey(uid)) {
                lockedSearchColor.put(uid, offHand.getType());
            }
            Material searchMat  = lockedSearchColor.get(uid);
            boolean  showReal   = offHand.getType() == searchMat;

            World w = p.getWorld();

            for (Entity marker : tagged(w, GROVER_CHEST)) {
                Location mLoc = marker.getLocation();
                if (mLoc.distance(p.getLocation()) > 20) continue;

                Block chestBlock = mLoc.getBlock().getRelative(0, -1, 0);
                if (chestBlock.getType() != Material.CHEST) continue;

                if (showReal) {
                    Container container = (Container) chestBlock.getState();
                    boolean match = chestContainsItem(container.getInventory(), searchMat);
                    spawnProbabilityColumn(w, mLoc, match, marker);
                } else {
                    // Wrong item in offhand — show all equal gray (no amplification)
                    spawnProbabilityColumn(w, mLoc, false, marker);
                }
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

    private void spawnProbabilityColumn(World w, Location loc, boolean match, Entity marker) {
        double maxHeight  = 3.5;
        double matchHeight = correctProbability() * maxHeight;

        double columnHeight;
        if (match) {
            columnHeight = matchHeight;
        } else {
            double raw = wrongProbability() * maxHeight;
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
            // Glow dot at top
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
        int n = num(marker);
        Entity label = findLabel(w, GROVER_LABEL, n);
        if (label instanceof TextDisplay td) {
            td.text(Component.text(text, color));
        }
    }

    public int getIteration() { return iteration; }

    // =========================================================================
    // State persistence
    // =========================================================================

    public void saveState(ConfigurationSection s) {
        s.set("iteration",   iteration);
        s.set("setupActive", setupActive);
        Map<String, String> lock = new HashMap<>();
        lockedSearchColor.forEach((u, m) -> lock.put(u.toString(), m.name()));
        s.set("lockedSearchColor", lock);
    }

    public void loadState(ConfigurationSection s) {
        iteration   = s.getInt    ("iteration",   0);
        setupActive = s.getBoolean("setupActive", false);
        ConfigurationSection lock = s.getConfigurationSection("lockedSearchColor");
        if (lock != null) {
            for (String k : lock.getKeys(false)) {
                try {
                    lockedSearchColor.put(UUID.fromString(k), Material.valueOf(lock.getString(k)));
                } catch (Exception ignored) { /* skip malformed entries */ }
            }
        }
    }
}
