package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Interactive, gated, step-by-step walkthrough inside the built tutorial hall.
 * See {@link TutorialWorldBuilder}. Single global run.
 */
public class InteractiveTutorial implements Listener {

    private final QAcraftPlugin plugin;

    private boolean active = false;
    private World world;
    private int ox, oy, oz;
    private UUID starter;
    private int currentRoom = 0;                 // 1=BB84, 2=Grover, 3=E91
    private int subState = 0;                    // 0=WAITING_ENTRY, 1=DOING_STEPS, 2=WAIT_BUTTON
    private List<Step> steps = new ArrayList<>();
    private int stepIndex = 0;
    private int pendingTicks = 0;                // >0 = a delayed completion is counting down (throttled ticks)
    private boolean allDoneCounting = false;     // true while the last-step delay before the button is running
    private boolean roomCompletedOnce = false;   // once true, re-completing this room shows the button instantly
    private boolean senderStarted = false;
    private final Set<String> padFaded = new HashSet<>(); // E91 Alice/Bob spot labels already green-faded
    private TextDisplay activeLabel;
    private TextDisplay missingDisplay;
    private Location buttonLoc;
    private int tick = 0;

    private static final int ZL = TutorialWorldBuilder.ZONE_L;
    private static final int HW = TutorialWorldBuilder.HALF_W;

    // Double-chest slot groups (same layout the Sender/Gate use)
    private static final int[] BASIS_SLOTS = {0,1,2,3,4,5,6,7,8, 18,19,20,21,22,23,24,25,26, 36,37,38,39,40,41,42,43,44};
    private static final int[] BIT_SLOTS   = {9,10,11,12,13,14,15,16,17, 27,28,29,30,31,32,33,34,35, 45,46,47,48,49,50,51,52,53};

    public InteractiveTutorial(QAcraftPlugin plugin) { this.plugin = plugin; }

    private record Step(String title, String sub, String labelText, int lx, int lz,
                        int delaySec, String guideTag, BooleanSupplier done) {}

    // =========================================================================
    // Start / stop / finish
    // =========================================================================

    public void start(Player p) {
        Entity origin = null;
        for (Entity e : tagged(p.getWorld(), Q_WORLD)) {
            if (e.getType() == EntityType.MARKER) { origin = e; break; }
        }
        if (origin == null) {
            p.sendMessage(Component.text("Build the tutorial hall first: ", NamedTextColor.RED)
                .append(Component.text("/q world build", NamedTextColor.YELLOW)));
            return;
        }
        world = p.getWorld();
        ox = origin.getLocation().getBlockX();
        oy = origin.getLocation().getBlockY();
        oz = origin.getLocation().getBlockZ();
        starter = p.getUniqueId();
        active = true;
        currentRoom = 1;
        subState = 0;
        pendingTicks = 0;
        senderStarted = false;
        cleanupRuntime();

        setEntrance(Material.IRON_BARS);
        openDoor(0);
        closeDoor(1);
        closeDoor(2);

        p.getInventory().clear();
        p.teleport(new Location(world, ox + 2.5, oy, oz + 0.5, -90f, 0f));
        showTitle("Welcome to QAcraft", "Walk east into the BB84 room to begin");
        world.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.3f);
    }

    public void stop(Player p) {
        clearApparatus();
        removeInteriorChests();
        finishCommon();
        if (p != null) p.sendMessage(Component.text("Walkthrough stopped — apparatus and chests cleared.", NamedTextColor.GRAY));
    }

    public boolean isActive() { return active; }

    private void finish(Player p) {
        showTitle("Tutorial complete!", "You tried all three quantum protocols — great job!");
        if (p != null) {
            Location c = p.getLocation();
            for (int i = 0; i < 3; i++) launchFirework(c.clone().add((i - 1) * 2.0, 0.5, 0));
        }
        clearApparatus();
        removeInteriorChests(); // restore the hall to its built state (also clears placed chests)
        finishCommon();
    }

    private void clearApparatus() {
        plugin.getPhotonManager().clearAll();
        plugin.getWaypointManager().clearAll();
        plugin.getGateManager().clearAll();
        plugin.getSenderManager().clearSender();
        plugin.getGroverManager().clear();
        plugin.getE91Manager().clear();
    }

    private void finishCommon() {
        if (world != null) { openDoor(0); openDoor(1); openDoor(2); setEntrance(Material.AIR); }
        cleanupRuntime();
        active = false;
        subState = 0;
        currentRoom = 0;
        pendingTicks = 0;
        senderStarted = false;
    }

    private void cleanupRuntime() {
        activeLabel = null;
        missingDisplay = null;
        buttonLoc = null;
        if (world != null) for (Entity e : tagged(world, Q_TUT)) e.remove();
    }

    private void removeInteriorChests() {
        if (world == null) return;
        int total = ZL * 4;
        for (int x = ox; x < ox + total; x++)
            for (int z = oz - HW; z <= oz + HW; z++) {
                Material m = world.getBlockAt(x, oy, z).getType();
                if (m == Material.CHEST || m == Material.TRAPPED_CHEST)
                    world.getBlockAt(x, oy, z).setType(Material.AIR, false);
            }
    }

    // =========================================================================
    // Tick
    // =========================================================================

    public void tick() {
        if (!active || world == null) return;
        if (++tick % 5 != 0) return;
        Player p = starter != null ? plugin.getServer().getPlayer(starter) : null;
        if (p == null) return;

        if (currentRoom == 1 && plugin.getSenderManager().isSending()) senderStarted = true;

        switch (subState) {
            case 0 -> { if (p.getLocation().getBlockX() >= ox + currentRoom * ZL) enterRoom(p); }
            case 1 -> stepTick();
            case 2 -> { // WAIT_BUTTON — if a completed step got undone, drop the button and go back
                if (firstIncomplete() < steps.size()) { removeButton(); subState = 1; allDoneCounting = false; }
            }
        }
    }

    /** The lowest step index that is not yet satisfied, or steps.size() if all done. */
    private int firstIncomplete() {
        for (int i = 0; i < steps.size(); i++) if (!steps.get(i).done().getAsBoolean()) return i;
        return steps.size();
    }

    /**
     * Re-evaluate every step each tick so completions AND undos are handled: if a
     * player removes something they placed, the step reverts and its label returns.
     */
    private void stepTick() {
        // (The "Missing: …" display above BB84 chests is handled always-on by
        //  SenderManager / GateManager, so it works outside the tutorial too.)
        if (currentRoom == 3) {
            int cx = ox + 3 * ZL + ZL / 2;
            checkPadSpot("alice", cx - 4, oz - 3, E91_PARK_A);
            checkPadSpot("bob",   cx + 4, oz - 3, E91_PARK_B);
        }
        int fi = firstIncomplete();
        if (fi < steps.size()) {
            allDoneCounting = false;
            pendingTicks = 0;
            if (fi != stepIndex) {
                if (fi > stepIndex && activeLabel != null && activeLabel.isValid()) {
                    flashFx(activeLabel.getLocation());
                    fadeLabelGreen(activeLabel);
                    activeLabel = null;
                }
                stepIndex = fi;
                showStep();
            }
        } else {
            if (!allDoneCounting) {
                allDoneCounting = true;
                // First completion waits the full delay; after that, re-completing is instant
                pendingTicks = roomCompletedOnce ? 1 : Math.max(1, steps.get(steps.size() - 1).delaySec() * 4);
                if (activeLabel != null && activeLabel.isValid()) {
                    flashFx(activeLabel.getLocation());
                    fadeLabelGreen(activeLabel);
                    activeLabel = null;
                }
            }
            if (--pendingTicks <= 0) {
                roomCompletedOnce = true;
                spawnNextButton();
                subState = 2;
                allDoneCounting = false;
            }
        }
    }

    private void enterRoom(Player p) {
        world.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
        switch (currentRoom) {
            case 1 -> { plugin.getToolManager().giveToolsBB84(p);   showTitle("BB84 — Quantum Key Distribution", "Build the experiment step by step"); steps = bb84Steps(); }
            case 2 -> { plugin.getToolManager().giveToolsGrover(p); plugin.getGroverManager().resetObserved(); showTitle("Grover's Search", "Run a search step by step"); steps = groverSteps(); }
            case 3 -> { plugin.getToolManager().giveToolsE91(p);    showTitle("E91 — Entanglement", "Share a key step by step"); steps = e91Steps(); }
        }
        stepIndex = 0;
        pendingTicks = 0;
        roomCompletedOnce = false;
        padFaded.clear();
        subState = 1;
        showStep();
    }

    private void showStep() {
        Step s = steps.get(stepIndex);
        showTitle(s.title(), s.sub());
        if (activeLabel != null && activeLabel.isValid()) activeLabel.remove();
        activeLabel = null;
        if (s.labelText() != null) {
            double lx = s.lx() + 0.5, lz = s.lz() + 0.5;
            double ly = oy + 0.9;
            if (s.guideTag() != null) {
                double[] mid = guideMidpoint(s.guideTag(), s.lx(), s.lz(), 5);
                if (mid != null) { lx = mid[0]; lz = mid[1]; }
                ly = oy + 1.6; // raise above the white chest guide so it doesn't overlap
            }
            activeLabel = spawnLabelAt(s.labelText(), lx, ly, lz);
        }
    }

    private void removeButton() {
        if (buttonLoc != null) {
            int bx = buttonLoc.getBlockX(), by = buttonLoc.getBlockY(), bz = buttonLoc.getBlockZ();
            world.getBlockAt(bx, by, bz).setType(Material.AIR, false);       // the button
            world.getBlockAt(bx, by - 1, bz).setType(Material.AIR, false);   // the quartz pillar
            buttonLoc = null;
        }
        for (Entity ent : tagged(world, Q_TUT)) if (ent instanceof TextDisplay) ent.remove();
        activeLabel = null;
    }

    // =========================================================================
    // Steps
    // =========================================================================

    private List<Step> bb84Steps() {
        int zx = ox + ZL;
        List<Step> l = new ArrayList<>();
        l.add(new Step("Place the Sender", "Right-click the Nether Star on the yellow spot", "Sender here", zx + 4, oz, 0, null,
            () -> entityAt(SENDER, zx + 4, oz)));
        l.add(new Step("Place a double chest", "Put chests on BOTH highlighted blocks", "Double chest here", zx + 4, oz, 0, SENDER_CHEST,
            () -> doubleChestNearEntity(SENDER, 1, 3.0) != null));
        l.add(new Step("Fill the chest", "Run /q sender fill, then fill every slot with red or blue glass panes", null, zx + 4, oz, 0, null,
            () -> senderChestReady(zx + 4, oz)));
        l.add(new Step("Place the Gate", "Right-click the Eye of Ender on the purple spot", "Gate here", zx + 8, oz, 0, null,
            () -> entityAt(GATE, zx + 8, oz)));
        l.add(new Step("Place a double chest by the Gate", "Put chests on BOTH highlighted blocks", "Double chest here", zx + 8, oz, 0, GATE_CHEST_GUIDE,
            () -> doubleChestNearEntity(GATE, 1, 3.0) != null));
        l.add(new Step("Add measurement filters", "Fill every basis slot of the Gate chest with Compasses", null, zx + 8, oz, 0, null,
            () -> gateChestReady(zx + 8, oz)));
        l.add(new Step("Place the Quantum Cable", "Right-click the Quantum Cable on the marked spot", "Cable here", zx + 11, oz, 0, null,
            () -> entityAt(WAYPOINT, zx + 11, oz)));
        l.add(new Step("Start the transmission", "Run /q sender start, then wait for all 27 photons", null, zx + 11, oz, 10, null,
            () -> senderStarted && !plugin.getSenderManager().isSending()));
        return l;
    }

    private List<Step> groverSteps() {
        int cx = ox + 2 * ZL + ZL / 2;
        List<Step> l = new ArrayList<>();
        l.add(new Step("Set up Grover", "Stand on the centre, run /q grover setup 1", "Setup here", cx, oz, 0, null,
            () -> anyInZone(GroverManager.GROVER_SLOT, 2) || anyInZone(GroverManager.GROVER_CHEST, 2)));
        l.add(new Step("Place the chests", "Run /q grover placechests 1", null, cx, oz, 0, null,
            () -> anyInZone(GroverManager.GROVER_CHEST, 2)));
        l.add(new Step("Fill with wool", "Run /q grover fillwool 1", null, cx, oz, 0, null,
            this::groverHasWool));
        l.add(new Step("Search & observe", "Hold Spyglass + wool, throw a Wind Charge, then look at the chests", null, cx, oz, 10, null,
            () -> plugin.getGroverManager().wasObserved()));
        return l;
    }

    private List<Step> e91Steps() {
        int cx = ox + 3 * ZL + ZL / 2;
        List<Step> l = new ArrayList<>();
        l.add(new Step("Place the EPR Source", "Right-click the Amethyst Shard on the purple spot", "Source here", cx, oz, 0, null,
            () -> anyInZone(E91_SRC, 3)));
        l.add(new Step("Add Alice & Bob pads", "Diamond Axe on the cyan pad, Gold Axe on the orange pad", null, cx, oz, 0, null,
            () -> anyInZone(E91_PARK_A, 3) && anyInZone(E91_PARK_B, 3)));
        l.add(new Step("Generate a pair", "Run /q e91 generate", null, cx, oz, 0, null,
            () -> anyInZone(E91_PHOTON, 3)));
        l.add(new Step("Measure a photon", "Hold Compass/Recovery Compass/Clock near a photon", null, cx, oz, 10, null,
            () -> plugin.getE91Manager().anyMeasured()));
        return l;
    }

    // =========================================================================
    // Next-room button
    // =========================================================================

    private void spawnNextButton() {
        // On the LEFT of the doorway (north), one block off the divider wall.
        // E91 is the last room — mirror its Finish button to the south (tools) side.
        int bx = doorX(currentRoom) - 2;
        int bz = (currentRoom == 3) ? oz + 3 : oz - 3;
        world.getBlockAt(bx, oy, bz).setType(Material.QUARTZ_PILLAR, false);
        org.bukkit.block.Block btn = world.getBlockAt(bx, oy + 1, bz);
        btn.setType(Material.STONE_BUTTON, false);
        try {
            Switch sw = (Switch) btn.getBlockData();
            sw.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
            btn.setBlockData(sw, false);
        } catch (Exception ignored) {}
        buttonLoc = btn.getLocation();
        spawnLabelAt(currentRoom < 3 ? "Next room →" : "Finish!", bx + 0.5, oy + 1.9, bz + 0.5);
        world.spawnParticle(Particle.END_ROD, new Location(world, bx + 0.5, oy + 1.5, bz + 0.5), 12, 0.2, 0.2, 0.2, 0.02);
    }

    /** While the walkthrough runs, the hall shell (walls, roof, floor, dividers, doors) is indestructible. */
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!active || world == null) return;
        if (!e.getBlock().getWorld().equals(world)) return;
        if (isStructure(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ())) e.setCancelled(true);
    }

    private boolean isStructure(int x, int y, int z) {
        int total = ZL * 4;
        if (x < ox - 1 || x > ox + total)       return false;
        if (z < oz - HW - 1 || z > oz + HW + 1)  return false;
        if (y < oy - 1 || y > oy + 5)            return false;
        if (y == oy - 1 || y == oy + 5)          return true;   // floor / roof
        if (z == oz - HW - 1 || z == oz + HW + 1) return true;   // side walls
        if (x == ox - 1 || x == ox + total)      return true;   // entrance / back wall
        for (int d = 0; d < 3; d++) if (x == doorX(d)) return true; // dividers + iron-bar doors
        return false;
    }

    @EventHandler
    public void onButton(PlayerInteractEvent e) {
        if (!active || subState != 2 || buttonLoc == null) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (!e.getClickedBlock().getLocation().equals(buttonLoc)) return;
        e.setCancelled(true); // stop vanilla button behaviour re-adding the block

        removeButton();

        if (currentRoom < 3) {
            openDoor(currentRoom);
            Location d = doorCenter(currentRoom);
            world.spawnParticle(Particle.HAPPY_VILLAGER, d, 40, 1, 1.5, 1, 0.1);
            world.playSound(d, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
            currentRoom++;
            subState = 0;
        } else {
            Player p = e.getPlayer();
            p.teleport(new Location(world, ox + 2.5, oy, oz + 0.5, -90f, 0f)); // lobby FIRST
            finish(p); // then fireworks + title + clear apparatus & chests
        }
    }

    private void launchFirework(Location loc) {
        Firework fw = world.spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
            .withColor(Color.AQUA, Color.LIME, Color.FUCHSIA)
            .withFade(Color.WHITE)
            .with(FireworkEffect.Type.BALL_LARGE)
            .flicker(true).trail(true).build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    // =========================================================================
    // Completion checks
    // =========================================================================

    private boolean inZone(Entity e, int zoneIndex) {
        int x = e.getLocation().getBlockX();
        return x >= ox + zoneIndex * ZL && x <= ox + (zoneIndex + 1) * ZL - 1;
    }

    private boolean anyInZone(String tag, int zoneIndex) {
        for (Entity e : tagged(world, tag)) if (inZone(e, zoneIndex)) return true;
        return false;
    }

    /** When a pad lands on its exact Alice/Bob spot, green-fade that spot's static label (once). */
    private void checkPadSpot(String key, int x, int z, String tag) {
        if (padFaded.contains(key) || !entityAt(tag, x, z)) return;
        padFaded.add(key);
        Location at = new Location(world, x + 0.5, oy + 0.9, z + 0.5);
        flashFx(at.clone().subtract(0, 0.3, 0));
        for (Entity e : world.getNearbyEntities(at, 1.2, 1.5, 1.2)) {
            if (e instanceof TextDisplay td && e.getScoreboardTags().contains(Q_WORLD)) { fadeLabelGreen(td); break; }
        }
    }

    /** True only if a tagged entity sits on the EXACT marked block (x,z). */
    private boolean entityAt(String tag, int x, int z) {
        for (Entity e : tagged(world, tag))
            if (e.getLocation().getBlockX() == x && e.getLocation().getBlockZ() == z) return true;
        return false;
    }

    /**
     * The double-chest (54-slot) inventory sitting next to a specific apparatus entity
     * (Sender / Gate). The tight radius (< the sender↔gate spacing) means the sender's
     * chest can't satisfy the gate step, and vice-versa. Guides get consumed on placement,
     * so we key off the apparatus itself, not the (now-removed) chest guides.
     */
    private Inventory doubleChestNearEntity(String apparatusTag, int zoneIndex, double maxDist) {
        Location ap = null;
        for (Entity e : tagged(world, apparatusTag)) if (inZone(e, zoneIndex)) { ap = e.getLocation(); break; }
        if (ap == null) return null;
        int r = (int) Math.ceil(maxDist);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (Math.hypot(dx, dz) > maxDist) continue;
                if (world.getBlockAt(ap.getBlockX() + dx, oy, ap.getBlockZ() + dz).getState() instanceof Container c
                        && c.getInventory().getSize() >= 54) return c.getInventory();
            }
        return null;
    }

    private boolean allSlots(Inventory inv, int[] slots, Predicate<Material> pred) {
        for (int s : slots) {
            ItemStack it = inv.getItem(s);
            if (it == null || !pred.test(it.getType())) return false;
        }
        return true;
    }

    /** Sender chest ready: every basis slot has a filter AND every bit slot has red/blue glass. */
    private boolean senderChestReady(int x, int z) {
        Inventory inv = doubleChestNearEntity(SENDER, 1, 3.0);
        if (inv == null) return false;
        return allSlots(inv, BASIS_SLOTS, m -> m == Material.COMPASS || m == Material.RECOVERY_COMPASS)
            && allSlots(inv, BIT_SLOTS,   m -> m == Material.RED_STAINED_GLASS_PANE || m == Material.BLUE_STAINED_GLASS_PANE
                                            || m == Material.RED_STAINED_GLASS || m == Material.BLUE_STAINED_GLASS);
    }

    /** Gate chest ready: every basis slot has a filter (Compass / Recovery Compass). */
    private boolean gateChestReady(int x, int z) {
        Inventory inv = doubleChestNearEntity(GATE, 1, 3.0);
        if (inv == null) return false;
        return allSlots(inv, BASIS_SLOTS, m -> m == Material.COMPASS || m == Material.RECOVERY_COMPASS);
    }

    // =========================================================================
    // "Missing: ..." display above the double chest during the fill steps
    // =========================================================================

    private void updateMissingDisplay() {
        String missing = null;
        Location anchor = null;
        if (currentRoom == 1) {
            if (stepIndex == 2) {          // sender fill step
                anchor = chestLocNearEntity(SENDER, 1, 3.0);
                if (anchor != null) missing = senderMissing(anchor);
            } else if (stepIndex == 5) {   // gate filter step
                anchor = chestLocNearEntity(GATE, 1, 3.0);
                if (anchor != null) missing = gateMissing(anchor);
            }
        }
        if (missing != null && anchor != null) {
            Location at = anchor.clone().add(0.5, 2.0, 0.5);
            if (missingDisplay == null || !missingDisplay.isValid()) {
                missingDisplay = (TextDisplay) world.spawnEntity(at, EntityType.TEXT_DISPLAY);
                missingDisplay.setBillboard(Display.Billboard.CENTER);
                missingDisplay.setShadowed(true);
                missingDisplay.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.8f), new Quaternionf()));
                missingDisplay.addScoreboardTag(Q_TUT);
            }
            missingDisplay.text(Component.text(missing, NamedTextColor.RED));
            missingDisplay.teleport(at);
        } else if (missingDisplay != null) {
            if (missingDisplay.isValid()) missingDisplay.remove();
            missingDisplay = null;
        }
    }

    private Location chestLocNearEntity(String apparatusTag, int zoneIndex, double maxDist) {
        Location ap = null;
        for (Entity e : tagged(world, apparatusTag)) if (inZone(e, zoneIndex)) { ap = e.getLocation(); break; }
        if (ap == null) return null;
        int r = (int) Math.ceil(maxDist);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (Math.hypot(dx, dz) > maxDist) continue;
                if (world.getBlockAt(ap.getBlockX() + dx, oy, ap.getBlockZ() + dz).getState() instanceof Container c
                        && c.getInventory().getSize() >= 54)
                    return new Location(world, ap.getBlockX() + dx, oy, ap.getBlockZ() + dz);
            }
        return null;
    }

    private String senderMissing(Location cl) {
        if (!(cl.getBlock().getState() instanceof Container c)) return null;
        Inventory inv = c.getInventory();
        boolean f = allSlots(inv, BASIS_SLOTS, m -> m == Material.COMPASS || m == Material.RECOVERY_COMPASS);
        boolean g = allSlots(inv, BIT_SLOTS,   m -> m == Material.RED_STAINED_GLASS_PANE || m == Material.BLUE_STAINED_GLASS_PANE
                                                 || m == Material.RED_STAINED_GLASS || m == Material.BLUE_STAINED_GLASS);
        if (f && g) return null;
        java.util.List<String> miss = new java.util.ArrayList<>();
        if (!f) miss.add("filters");
        if (!g) miss.add("photons");
        return "Missing: " + String.join(", ", miss);
    }

    private String gateMissing(Location cl) {
        if (!(cl.getBlock().getState() instanceof Container c)) return null;
        return allSlots(c.getInventory(), BASIS_SLOTS, m -> m == Material.COMPASS || m == Material.RECOVERY_COMPASS)
            ? null : "Missing: filters";
    }

    private boolean groverHasWool() {
        for (Entity e : tagged(world, GroverManager.GROVER_CHEST)) {
            if (!inZone(e, 2)) continue;
            if (e.getLocation().getBlock().getRelative(0, -1, 0).getState() instanceof Container c) {
                for (ItemStack it : c.getInventory().getContents())
                    if (it != null && it.getType().name().endsWith("_WOOL")) return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Labels / feedback / doors / titles
    // =========================================================================

    private double[] guideMidpoint(String tag, int nearX, int nearZ, double r) {
        double sx = 0, sz = 0; int n = 0;
        for (Entity e : tagged(world, tag)) {
            Location l = e.getLocation();
            if (Math.abs(l.getX() - (nearX + 0.5)) <= r && Math.abs(l.getZ() - (nearZ + 0.5)) <= r) {
                sx += l.getX(); sz += l.getZ(); n++;
            }
        }
        return n == 0 ? null : new double[]{sx / n, sz / n};
    }

    private TextDisplay spawnLabelAt(String text, double x, double y, double z) {
        TextDisplay td = (TextDisplay) world.spawnEntity(new Location(world, x, y, z), EntityType.TEXT_DISPLAY);
        td.text(Component.text(text, NamedTextColor.YELLOW));
        td.setBillboard(Display.Billboard.CENTER); // guidance labels turn to face the player
        td.setShadowed(true);
        td.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.8f), new Quaternionf()));
        td.addScoreboardTag(Q_TUT);
        return td;
    }

    private void flashFx(Location loc) {
        world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 24, 0.4, 0.4, 0.4, 0.06);
        world.spawnParticle(Particle.END_ROD, loc, 8, 0.3, 0.3, 0.3, 0.02);
        world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
    }

    private void fadeLabelGreen(TextDisplay td) {
        String txt = PlainTextComponentSerializer.plainText().serialize(td.text());
        td.text(Component.text(txt, NamedTextColor.GREEN));
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (!td.isValid()) { cancel(); return; }
                int op = 255 - t * 12;
                if (op <= 8) { td.remove(); cancel(); return; }
                try { td.setTextOpacity((byte) op); } catch (Exception ignored) {}
            }
        }.runTaskTimer(plugin, 20L, 1L);
    }

    private int doorX(int dividerAfterZone) { return ox + (dividerAfterZone + 1) * ZL - 1; }
    private Location doorCenter(int d) { return new Location(world, doorX(d) + 0.5, oy + 1, oz + 0.5); }
    private void closeDoor(int d) { setColumn(doorX(d), Material.IRON_BARS); }
    private void openDoor(int d)  { setColumn(doorX(d), Material.AIR); }
    private void setEntrance(Material m) { setColumn(ox - 1, m); }

    private void setColumn(int x, Material m) {
        if (world == null) return;
        for (int z = oz - 1; z <= oz + 1; z++)
            for (int y = oy; y <= oy + 2; y++)
                world.getBlockAt(x, y, z).setType(m, true); // applyPhysics → iron bars connect
    }

    private void showTitle(String big, String sub) {
        Title t = Title.title(
            Component.text(big, NamedTextColor.AQUA),
            Component.text(sub, NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(5000), Duration.ofMillis(600)));
        for (Player p : world.getPlayers()) p.showTitle(t);
    }
}
