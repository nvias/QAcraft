package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;

/**
 * E91 (Ekert 1991) entanglement-based QKD protocol.
 *
 * Completely independent of BB84 infrastructure:
 *  - own entity tags (q_e91_*)
 *  - own direct-flight movement system (no waypoints)
 *  - own measurement event handler
 *  - results stored in-memory (not in chests)
 *
 * Entangled state: |φ+⟩ = (|00⟩+|11⟩)/√2 — same basis → same result.
 * Three measurement bases:  COMPASS=0°, RECOVERY_COMPASS=45°, CLOCK=90°.
 */
public class E91Manager implements Listener {

    private final QAcraftPlugin plugin;
    private final Random random = new Random();

    private boolean eveMode       = false;
    private boolean autoGenerate  = false;
    private int     generateDelay = 0;

    private final Map<UUID, Long>     cooldowns    = new HashMap<>();
    /**
     * measurements[pairId] = int[4]:
     *   [0] aliceBasis   (-1 = not yet measured)
     *   [1] aliceResult  (-1 = not yet measured)
     *   [2] bobBasis     (-1 = not yet measured)
     *   [3] bobResult    (-1 = not yet measured)
     */
    private final Map<Integer, int[]> measurements = new TreeMap<>(); // TreeMap for sorted pairId

    private static final String[] BASIS_NAME = {"Rectilinear +", "Diagonal ×", "Circular ○"};

    public E91Manager(QAcraftPlugin plugin) { this.plugin = plugin; }

    // =========================================================================
    // Landmark placement
    // =========================================================================

    /** Place the entangled-pair source at the player's feet (snapped to block centre). */
    public void placeSource(Player p) {
        World w = p.getWorld();
        killAll(w, E91_SRC);

        Location loc = blockCentre(p.getLocation());

        // Invisible marker — used for position queries
        Entity marker = w.spawnEntity(loc, EntityType.MARKER);
        marker.addScoreboardTag(E91_SRC);

        // BlockDisplay visual: glowing amethyst block
        Location vLoc = loc.clone(); vLoc.setYaw(0); vLoc.setPitch(0);
        BlockDisplay vis = (BlockDisplay) w.spawnEntity(vLoc, EntityType.BLOCK_DISPLAY);
        vis.setBlock(Material.AMETHYST_BLOCK.createBlockData());
        vis.setGlowing(true);
        vis.setTransformation(new Transformation(
            new Vector3f(-0.25f, 0f, -0.25f), new Quaternionf(), new Vector3f(0.5f), new Quaternionf()));
        vis.addScoreboardTag(E91_SRC);
        setTeam(vis, "q_e91_purple");

        // Label
        TextDisplay lbl = (TextDisplay) w.spawnEntity(loc.clone().add(0, 1.1, 0), EntityType.TEXT_DISPLAY);
        lbl.text(Component.text("EPR", NamedTextColor.LIGHT_PURPLE));
        lbl.setBillboard(Display.Billboard.CENTER);
        lbl.setShadowed(true);
        lbl.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.7f), new Quaternionf()));
        lbl.addScoreboardTag(E91_SRC);

        p.sendMessage(Component.text("E91 Source placed", NamedTextColor.LIGHT_PURPLE)
            .append(Component.text(" — mark Alice and Bob endpoints next", NamedTextColor.GRAY)));
        w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
    }

    /** Mark Alice's measurement zone at the player's current position (tall sea-lantern beacon). */
    public void setAliceEnd(Player p) {
        World w = p.getWorld();
        killAll(w, E91_ALICE);
        placeEndpoint(w, blockCentre(p.getLocation()), E91_ALICE,
            Material.SEA_LANTERN, "Alice", NamedTextColor.AQUA, "q_e91_cyan");
        p.sendMessage(Component.text("Alice zone marked", NamedTextColor.AQUA)
            .append(Component.text(" — use Diamond Axe to place her landing pad", NamedTextColor.GRAY)));
        w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.8f, 1.2f);
    }

    /** Mark Bob's measurement zone at the player's current position (tall shroomlight beacon). */
    public void setBobEnd(Player p) {
        World w = p.getWorld();
        killAll(w, E91_BOB);
        placeEndpoint(w, blockCentre(p.getLocation()), E91_BOB,
            Material.SHROOMLIGHT, "Bob", NamedTextColor.GOLD, "q_e91_gold");
        p.sendMessage(Component.text("Bob zone marked", NamedTextColor.GOLD)
            .append(Component.text(" — use Gold Axe to place his landing pad", NamedTextColor.GRAY)));
        w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.8f, 0.8f);
    }

    /**
     * Place Alice's flat landing pad (sea-lantern texture squished to pressure-plate shape).
     * Photons fly here first; falls back to the zone marker if no pad is placed.
     */
    public void placeAliceParking(Player p) {
        World w = p.getWorld();
        int padNum = freeNum(w, E91_PARK_A);
        placeParkingPad(w, blockCentre(p.getLocation()), E91_PARK_A, padNum,
            Material.SEA_LANTERN, "Alice", NamedTextColor.AQUA, "q_e91_cyan");
        p.sendMessage(Component.text("Alice landing pad #" + padNum + " placed", NamedTextColor.AQUA));
        w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.8f, 1.3f);
    }

    /**
     * Place Bob's flat landing pad (shroomlight texture squished to pressure-plate shape).
     */
    public void placeBobParking(Player p) {
        World w = p.getWorld();
        int padNum = freeNum(w, E91_PARK_B);
        placeParkingPad(w, blockCentre(p.getLocation()), E91_PARK_B, padNum,
            Material.SHROOMLIGHT, "Bob", NamedTextColor.GOLD, "q_e91_gold");
        p.sendMessage(Component.text("Bob landing pad #" + padNum + " placed", NamedTextColor.GOLD));
        w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.8f, 0.9f);
    }

    /** Flat block-display pad — same proportions as the golden pressure plate parking spot. */
    private void placeParkingPad(World w, Location loc, String tag, int padNum,
                                 Material block, String name, NamedTextColor color, String teamName) {
        Entity marker = w.spawnEntity(loc, EntityType.MARKER);
        marker.addScoreboardTag(tag);
        setNum(marker, padNum);

        Location vLoc = loc.clone(); vLoc.setYaw(0); vLoc.setPitch(0);
        BlockDisplay vis = (BlockDisplay) w.spawnEntity(vLoc, EntityType.BLOCK_DISPLAY);
        vis.setBlock(block.createBlockData());
        vis.setGlowing(true);
        vis.setTransformation(new Transformation(
            new Vector3f(-0.25f, 0f, -0.25f), new Quaternionf(),
            new Vector3f(0.5f, 0.1f, 0.5f), new Quaternionf()));
        vis.addScoreboardTag(tag);
        setNum(vis, padNum);
        setTeam(vis, teamName);

        TextDisplay lbl = (TextDisplay) w.spawnEntity(loc.clone().add(0, 0.3, 0), EntityType.TEXT_DISPLAY);
        lbl.text(Component.text(name, color));
        lbl.setBillboard(Display.Billboard.CENTER);
        lbl.setShadowed(true);
        lbl.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.5f), new Quaternionf()));
        lbl.addScoreboardTag(tag);
        setNum(lbl, padNum);
    }

    private void placeEndpoint(World w, Location loc, String tag,
                               Material block, String name, NamedTextColor color, String teamName) {
        Entity marker = w.spawnEntity(loc, EntityType.MARKER);
        marker.addScoreboardTag(tag);

        Location vLoc = loc.clone(); vLoc.setYaw(0); vLoc.setPitch(0);
        BlockDisplay vis = (BlockDisplay) w.spawnEntity(vLoc, EntityType.BLOCK_DISPLAY);
        vis.setBlock(block.createBlockData());
        vis.setGlowing(true);
        vis.setTransformation(new Transformation(
            new Vector3f(-0.3f, 0f, -0.3f), new Quaternionf(), new Vector3f(0.6f), new Quaternionf()));
        vis.addScoreboardTag(tag);
        setTeam(vis, teamName);

        TextDisplay lbl = (TextDisplay) w.spawnEntity(loc.clone().add(0, 1.1, 0), EntityType.TEXT_DISPLAY);
        lbl.text(Component.text(name, color));
        lbl.setBillboard(Display.Billboard.CENTER);
        lbl.setShadowed(true);
        lbl.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.7f), new Quaternionf()));
        lbl.addScoreboardTag(tag);
    }

    // =========================================================================
    // Pair generation
    // =========================================================================

    /** Spawn one entangled photon pair — one flies toward Alice, one toward Bob. */
    public void generate(Player p) {
        World w = (p != null) ? p.getWorld() : plugin.getServer().getWorlds().get(0);

        Entity srcMarker   = markerIn(w, E91_SRC);
        Entity aliceMarker = markerIn(w, E91_ALICE);
        Entity bobMarker   = markerIn(w, E91_BOB);

        if (srcMarker == null) {
            if (p != null) p.sendMessage(Component.text("Place source first: /qacraft e91 source", NamedTextColor.RED));
            return;
        }
        if (aliceMarker == null) {
            if (p != null) p.sendMessage(Component.text("Mark Alice endpoint: /qacraft e91 alice", NamedTextColor.RED));
            return;
        }
        if (bobMarker == null) {
            if (p != null) p.sendMessage(Component.text("Mark Bob endpoint: /qacraft e91 bob", NamedTextColor.RED));
            return;
        }

        int pairId = freePairId(w);
        measurements.put(pairId, new int[]{-1, -1, -1, -1});

        Location srcLoc = srcMarker.getLocation().add(0, 0.5, 0);
        spawnE91Photon(w, srcLoc, pairId, true);   // Alice — CYAN
        spawnE91Photon(w, srcLoc, pairId, false);  // Bob   — ORANGE

        w.spawnParticle(Particle.REVERSE_PORTAL, srcLoc, 20, 0.2, 0.2, 0.2, 0.05);
        w.spawnParticle(Particle.END_ROD, srcLoc, 8, 0.1, 0.1, 0.1, 0.02);
        w.playSound(srcLoc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f);

        if (p != null) {
            p.sendMessage(Component.text("[E91] Pair #" + pairId + " generated", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(" — photons in flight", NamedTextColor.GRAY)));
        }
    }

    private Entity spawnE91Photon(World w, Location loc, int pairId, boolean isAlice) {
        Location spawnLoc = loc.clone(); spawnLoc.setYaw(0); spawnLoc.setPitch(0);
        BlockDisplay photon = (BlockDisplay) w.spawnEntity(spawnLoc, EntityType.BLOCK_DISPLAY);
        photon.setBlock((isAlice ? Material.CYAN_STAINED_GLASS : Material.ORANGE_STAINED_GLASS).createBlockData());
        photon.setGlowing(true);
        photon.setTransformation(new Transformation(
            new Vector3f(-0.2f, -0.2f, -0.2f), new Quaternionf(), new Vector3f(0.4f), new Quaternionf()));
        photon.addScoreboardTag(E91_PHOTON);
        photon.addScoreboardTag(isAlice ? E91_PA : E91_PB);
        photon.addScoreboardTag(E91_MOVING);
        setPair(photon, pairId);
        // Assign target: lowest-numbered unoccupied pad; 0 = fall back to zone marker
        String padTag = isAlice ? E91_PARK_A : E91_PARK_B;
        Set<Integer> occupiedNums = new HashSet<>();
        for (Entity ph : tagged(w, E91_PHOTON)) {
            if (ph.getScoreboardTags().contains(isAlice ? E91_PA : E91_PB)) {
                int t = target(ph);
                if (t > 0) occupiedNums.add(t);
            }
        }
        Entity targetPad = null;
        int bestNum = Integer.MAX_VALUE;
        for (Entity pad : tagged(w, padTag)) {
            if (pad.getType() == EntityType.MARKER) {
                int n = num(pad);
                if (!occupiedNums.contains(n) && n < bestNum) { bestNum = n; targetPad = pad; }
            }
        }
        if (targetPad == null) { // all occupied — fall back to any pad
            for (Entity pad : tagged(w, padTag)) {
                if (pad.getType() == EntityType.MARKER) { targetPad = pad; break; }
            }
        }
        setTarget(photon, targetPad != null ? num(targetPad) : 0);
        setTeam(photon, isAlice ? "q_e91_cyan" : "q_e91_gold");

        // Label: A#1 / B#1 — uses num to distinguish Alice(0) vs Bob(1) within pair
        TextDisplay lbl = (TextDisplay) w.spawnEntity(loc.clone().add(0, 0.55, 0), EntityType.TEXT_DISPLAY);
        lbl.text(Component.text((isAlice ? "A" : "B") + "#" + pairId,
            isAlice ? NamedTextColor.AQUA : NamedTextColor.GOLD));
        lbl.setBillboard(Display.Billboard.CENTER);
        lbl.setShadowed(true);
        lbl.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.55f), new Quaternionf()));
        lbl.addScoreboardTag(E91_LBL);
        setPair(lbl, pairId);
        setNum(lbl, isAlice ? 0 : 1);   // 0=Alice side, 1=Bob side

        return photon;
    }

    // =========================================================================
    // Tick — called from QAcraftPlugin main scheduler every tick
    // =========================================================================

    public void tick() {
        if (autoGenerate) {
            generateDelay++;
            if (generateDelay >= 40) { generateDelay = 0; generate(null); }
        }

        for (World w : plugin.getServer().getWorlds()) {
            movePhotons(w);
        }
    }

    private void movePhotons(World w) {
        for (Entity photon : tagged(w, E91_MOVING)) {
            if (!(photon instanceof BlockDisplay)) continue;

            boolean isAlice = photon.getScoreboardTags().contains(E91_PA);
            String padTag   = isAlice ? E91_PARK_A : E91_PARK_B;
            String zoneTag  = isAlice ? E91_ALICE   : E91_BOB;

            // 1. Try assigned pad by stored target num
            int targetNum = target(photon);
            Entity endpoint = null;
            if (targetNum > 0) {
                for (Entity pad : tagged(w, padTag)) {
                    if (pad.getType() == EntityType.MARKER && num(pad) == targetNum) {
                        endpoint = pad; break;
                    }
                }
            }
            // 2. Any available pad (assigned pad was erased or none at spawn time)
            if (endpoint == null) {
                for (Entity pad : tagged(w, padTag)) {
                    if (pad.getType() == EntityType.MARKER) { endpoint = pad; break; }
                }
            }
            // 3. Zone marker fallback
            if (endpoint == null) endpoint = markerIn(w, zoneTag);
            if (endpoint == null) continue;

            Location to   = endpoint.getLocation().add(0, 0.5, 0);
            Location from = photon.getLocation();
            double dist   = from.distance(to);

            if (dist < 0.4) {
                photon.teleport(to);
                photon.getScoreboardTags().remove(E91_MOVING);
                photon.addScoreboardTag(E91_READY);
                w.playSound(to, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 1.2f);
                w.spawnParticle(Particle.END_ROD, to, 5, 0.1, 0.1, 0.1, 0.02);
            } else {
                Vector dir = to.toVector().subtract(from.toVector()).normalize().multiply(0.3);
                photon.teleport(from.add(dir));
                w.spawnParticle(Particle.END_ROD, photon.getLocation(), 1, 0, 0, 0, 0.01);
            }

            // Sync paired label
            syncLabel(w, pair(photon), isAlice ? 0 : 1, photon.getLocation().add(0, 0.55, 0));
        }
    }

    private void syncLabel(World w, int pairId, int side, Location pos) {
        for (Entity lbl : tagged(w, E91_LBL)) {
            if (pair(lbl) == pairId && num(lbl) == side) {
                lbl.teleport(pos);
                break;
            }
        }
    }

    // =========================================================================
    // Measurement — PlayerMoveEvent (separate from BB84 MeasurementManager)
    // =========================================================================

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();

        int playerBasis;
        if      (item.getType() == Material.COMPASS)          playerBasis = 0;
        else if (item.getType() == Material.RECOVERY_COMPASS) playerBasis = 1;
        else if (item.getType() == Material.CLOCK)            playerBasis = 2;
        else return;

        // 2-second per-player cooldown
        long now = System.currentTimeMillis();
        UUID uid = p.getUniqueId();
        if (cooldowns.containsKey(uid) && now - cooldowns.get(uid) < 2000) return;

        // Find a nearby E91 photon that has arrived and is awaiting measurement
        for (Entity photon : tagged(p.getWorld(), E91_READY)) {
            if (photon.getLocation().distance(p.getLocation()) < 2.5) {
                measure(p, photon, playerBasis);
                cooldowns.put(uid, now);
                return;
            }
        }
    }

    private void measure(Player p, Entity photon, int playerBasis) {
        int pairId  = pair(photon);
        boolean isAlice = photon.getScoreboardTags().contains(E91_PA);
        int[] m = measurements.computeIfAbsent(pairId, k -> new int[]{-1, -1, -1, -1});

        // m layout: [aliceBasis, aliceResult, bobBasis, bobResult]
        int myBasisIdx  = isAlice ? 0 : 2;
        int myResultIdx = isAlice ? 1 : 3;
        int paBasisIdx  = isAlice ? 2 : 0;
        int paResultIdx = isAlice ? 3 : 1;

        // REMEASUREMENT: already measured on this side
        if (m[myResultIdx] != -1) {
            if (m[myBasisIdx] != playerBasis) {
                // Different basis → locked, reject
                p.sendMessage(Component.text("[E91] Pair #" + pairId + " locked to "
                    + BASIS_NAME[m[myBasisIdx]] + " — cannot use " + BASIS_NAME[playerBasis], NamedTextColor.RED));
                return;
            }
            // Same basis → flash stored result, then dim
            flashAndDim(photon, m[myResultIdx]);
            sendMeasureFeedback(p, isAlice, pairId, playerBasis, m[myResultIdx]);
            return;
        }

        // FIRST MEASUREMENT
        boolean partnerUnmeasured = m[paResultIdx] == -1;
        int result;
        if (partnerUnmeasured) {
            result = random.nextInt(2);
        } else {
            result = computeCorrelated(playerBasis, m[paBasisIdx], m[paResultIdx]);
        }
        if (eveMode && random.nextDouble() < 0.25) result = 1 - result;

        m[myBasisIdx]  = playerBasis;
        m[myResultIdx] = result;
        measurements.put(pairId, m);

        // Flash own photon then dim
        flashAndDim(photon, result);

        // If partner has arrived: also flash partner (entanglement demo — both change simultaneously)
        if (partnerUnmeasured) {
            World w = photon.getWorld();
            int finalResult = result;
            for (Entity ph : tagged(w, E91_PHOTON)) {
                if (pair(ph) == pairId && ph != photon
                        && ph.getScoreboardTags().contains(E91_READY)
                        && ph instanceof BlockDisplay bd) {
                    Material flashBlock  = finalResult == 0
                        ? Material.BLUE_STAINED_GLASS : Material.RED_STAINED_GLASS;
                    Material partnerOrig = ph.getScoreboardTags().contains(E91_PA)
                        ? Material.CYAN_STAINED_GLASS : Material.ORANGE_STAINED_GLASS;
                    bd.setBlock(flashBlock.createBlockData());
                    ph.setGlowing(true);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (ph.isValid()) {
                            ((BlockDisplay) ph).setBlock(partnerOrig.createBlockData());
                            ph.setGlowing(false);
                        }
                    }, 30L);
                    break;
                }
            }
        }

        sendMeasureFeedback(p, isAlice, pairId, playerBasis, result);
    }

    /**
     * Flash photon to measured colour (blue/red), then dim back to original (cyan/orange, no glow)
     * after 1.5 s. Photon entity is NOT removed.
     */
    private void flashAndDim(Entity photon, int result) {
        boolean isAlicePhoton = photon.getScoreboardTags().contains(E91_PA);
        Material original = isAlicePhoton ? Material.CYAN_STAINED_GLASS : Material.ORANGE_STAINED_GLASS;
        Material flash    = result == 0 ? Material.BLUE_STAINED_GLASS : Material.RED_STAINED_GLASS;
        ((BlockDisplay) photon).setBlock(flash.createBlockData());
        photon.setGlowing(true);
        photon.getWorld().spawnParticle(Particle.END_ROD, photon.getLocation(), 10, 0.15, 0.15, 0.15, 0.03);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (photon.isValid()) {
                ((BlockDisplay) photon).setBlock(original.createBlockData());
                photon.setGlowing(false);
            }
        }, 30L);
    }

    private void sendMeasureFeedback(Player p, boolean isAlice, int pairId, int playerBasis, int result) {
        String sideLabel = isAlice ? "Alice" : "Bob";
        NamedTextColor c  = playerBasis == 0 ? NamedTextColor.AQUA
                          : playerBasis == 1 ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GREEN;
        NamedTextColor bc = result == 0 ? NamedTextColor.BLUE : NamedTextColor.RED;
        p.sendMessage(Component.text("[E91] " + sideLabel + " | Pair #" + pairId, c)
            .append(Component.text(" | " + BASIS_NAME[playerBasis] + " | bit = ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(result), bc)));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
    }

    /**
     * Quantum correlation for |φ+⟩ = (|00⟩+|11⟩)/√2.
     * Same basis  → always same result (P = 1.0).
     * Δbasis = 1  → P(same) ≈ 0.854  (45° offset, E = cos45° ≈ 0.707)
     * Δbasis = 2  → P(same) = 0.5    (90° offset, E = cos90° = 0, no correlation)
     */
    private int computeCorrelated(int myBasis, int partnerBasis, int partnerResult) {
        int diff = java.lang.Math.abs(myBasis - partnerBasis);
        double pSame = (diff == 0) ? 1.0
                     : (diff == 1) ? 0.854
                     :               0.5;
        return (random.nextDouble() < pSame) ? partnerResult : (1 - partnerResult);
    }

    // =========================================================================
    // Key extraction
    // =========================================================================

    /** Display Alice's and Bob's key bits (same-basis pairs only) and report mismatches. */
    public void showKey(Player p) {
        List<int[]> sameBasis = new ArrayList<>();

        for (int[] m : measurements.values()) {
            // Skip if either side is unmeasured, or bases differ
            if (m[0] == -1 || m[2] == -1) continue;
            if (m[0] != m[2]) continue;
            sameBasis.add(m);
        }

        if (sameBasis.isEmpty()) {
            p.sendMessage(Component.text("[E91] No same-basis pairs yet. Measure more photons!", NamedTextColor.GRAY));
            return;
        }

        p.sendMessage(Component.text("══ E91 KEY EXTRACTION ══", NamedTextColor.AQUA));

        Component aLine = Component.text("Alice: ", NamedTextColor.AQUA);
        Component bLine = Component.text("  Bob: ", NamedTextColor.GOLD);
        int mismatches = 0;

        for (int[] m : sameBasis) {
            boolean mismatch = m[1] != m[3];
            if (mismatch) mismatches++;
            NamedTextColor ac = mismatch ? NamedTextColor.RED : NamedTextColor.WHITE;
            NamedTextColor bc = mismatch ? NamedTextColor.RED : NamedTextColor.WHITE;
            aLine = aLine.append(Component.text(m[1] + " ", ac));
            bLine = bLine.append(Component.text(m[3] + " ", bc));
        }

        p.sendMessage(aLine);
        p.sendMessage(bLine);

        int total = sameBasis.size();
        double mismatchRate = (double) mismatches / total;
        p.sendMessage(Component.text(
            "Same-basis pairs: " + total + "  |  Mismatches: " + mismatches
            + " (" + String.format("%.0f%%", mismatchRate * 100) + ")", NamedTextColor.GRAY));

        if (mismatchRate <= 0.10) {
            p.sendMessage(Component.text("✓ Keys match — channel appears SECURE", NamedTextColor.GREEN));
        } else {
            p.sendMessage(Component.text("✗ Too many mismatches — possible eavesdropping detected!", NamedTextColor.RED));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
    }

    // =========================================================================
    // Bell / correlation test
    // =========================================================================

    /**
     * Compute and display cross-basis correlations.
     * For |φ+⟩ and bases 0°/45°/90° the expected correlations are:
     *   Δθ=45° (pairs 0↔1, 1↔2) : E = cos45° ≈ 0.71
     *   Δθ=90° (pair  0↔2)       : E = cos90° = 0.00
     */
    public void showBell(Player p) {
        // stats[i] = [matches, total]   i=0 → (0,1)  i=1 → (0,2)  i=2 → (1,2)
        int[][] stats = new int[3][2];
        int crossTotal = 0;

        for (int[] m : measurements.values()) {
            if (m[0] == -1 || m[2] == -1) continue;
            if (m[0] == m[2]) continue; // same basis → key pair, skip

            int bMin = java.lang.Math.min(m[0], m[2]);
            int bMax = java.lang.Math.max(m[0], m[2]);
            int idx  = (bMin == 0 && bMax == 1) ? 0 : (bMin == 0 && bMax == 2) ? 1 : 2;

            stats[idx][1]++;
            if (m[1] == m[3]) stats[idx][0]++; // same result = match
            crossTotal++;
        }

        if (crossTotal == 0) {
            p.sendMessage(Component.text("[E91] No cross-basis pairs yet. Measure more photons!", NamedTextColor.GRAY));
            return;
        }

        p.sendMessage(Component.text("══ E91 BELL CORRELATION TEST ══", NamedTextColor.GOLD));
        p.sendMessage(Component.text("Correlation E = (matches − mismatches) / total:", NamedTextColor.GRAY));

        double[] expected = {0.707, 0.000, 0.707};
        String[] labels   = {"0↔1  (Δ45°)", "0↔2  (Δ90°)", "1↔2  (Δ45°)"};
        boolean allGood = true;

        for (int i = 0; i < 3; i++) {
            int n = stats[i][1];
            if (n == 0) {
                p.sendMessage(Component.text("  Bases " + labels[i] + ": no pairs yet", NamedTextColor.DARK_GRAY));
                continue;
            }
            if (n < 5) {
                p.sendMessage(Component.text("  Bases " + labels[i] + ": only " + n + " pair(s) — need more data",
                    NamedTextColor.GRAY));
                continue;
            }

            double corr      = (double)(stats[i][0] * 2 - n) / n;
            double deviation = java.lang.Math.abs(corr - expected[i]);
            boolean ok       = deviation < 0.35;
            if (!ok) allGood = false;

            NamedTextColor c = ok ? NamedTextColor.GREEN : NamedTextColor.RED;
            p.sendMessage(Component.text("  Bases " + labels[i] + ": ", NamedTextColor.GRAY)
                .append(Component.text("expected ≈ " + String.format("%.2f", expected[i]), NamedTextColor.WHITE))
                .append(Component.text("  |  measured: " + String.format("%.2f", corr), NamedTextColor.WHITE))
                .append(Component.text(ok ? "  ✓" : "  ✗", c)));
        }

        p.sendMessage(Component.text("Total cross-basis pairs: " + crossTotal, NamedTextColor.GRAY));
        if (allGood) {
            p.sendMessage(Component.text("✓ Entanglement confirmed — quantum correlations intact!", NamedTextColor.GREEN));
        } else {
            p.sendMessage(Component.text("✗ Correlations deviate — possible eavesdropping!", NamedTextColor.RED));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
    }

    // =========================================================================
    // Eve mode
    // =========================================================================

    public void toggleEve(Player p) {
        eveMode = !eveMode;
        if (eveMode) {
            Bukkit.broadcast(Component.text("⚠ Eve is NOW ACTIVE — eavesdropping the E91 channel!", NamedTextColor.RED));
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, 0.5f);
        } else {
            Bukkit.broadcast(Component.text("✓ Eve disabled — E91 channel is clean", NamedTextColor.GREEN));
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
        }
    }

    // =========================================================================
    // Auto-generation
    // =========================================================================

    public void startAuto(Player p) {
        autoGenerate  = true;
        generateDelay = 0;
        p.sendMessage(Component.text("E91 auto-generation started (1 pair / 2 s)", NamedTextColor.GREEN));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.0f);
    }

    public void stopAuto(Player p) {
        autoGenerate = false;
        p.sendMessage(Component.text("E91 auto-generation stopped", NamedTextColor.GRAY));
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    public void clear() {
        autoGenerate  = false;
        eveMode       = false;
        measurements.clear();
        cooldowns.clear();
        generateDelay = 0;
        for (World w : plugin.getServer().getWorlds()) {
            killAll(w, E91_SRC);
            killAll(w, E91_ALICE);
            killAll(w, E91_BOB);
            killAll(w, E91_PARK_A);
            killAll(w, E91_PARK_B);
            killAll(w, E91_PHOTON);
            killAll(w, E91_LBL);
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Find the lowest pair ID not currently in use by any live photon or label entity.
     * When a pair is erased, its ID becomes available again (same behaviour as BB84 freeNum).
     */
    private int freePairId(World w) {
        Set<Integer> used = new HashSet<>();
        for (Entity ph : tagged(w, E91_PHOTON)) used.add(pair(ph));
        for (Entity lbl : tagged(w, E91_LBL))   used.add(pair(lbl));
        for (int i = 1; i <= 9999; i++) if (!used.contains(i)) return i;
        return 1;
    }

    private static Location blockCentre(Location loc) {
        return new Location(loc.getWorld(),
            loc.getBlockX() + 0.5, loc.getBlockY(), loc.getBlockZ() + 0.5);
    }

    private void setTeam(Entity e, String teamName) {
        Scoreboard sb = plugin.getServer().getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(teamName);
        if (team == null) {
            team = sb.registerNewTeam(teamName);
            switch (teamName) {
                case "q_e91_purple" -> team.color(NamedTextColor.LIGHT_PURPLE);
                case "q_e91_cyan"   -> team.color(NamedTextColor.AQUA);
                case "q_e91_gold"   -> team.color(NamedTextColor.GOLD);
            }
        }
        team.addEntity(e);
    }

    /** True once any photon (Alice or Bob) has been measured. */
    public boolean anyMeasured() {
        for (int[] m : measurements.values()) if (m[1] != -1 || m[3] != -1) return true;
        return false;
    }

    // =========================================================================
    // State persistence — measurements is the only non-trivial structure
    // =========================================================================

    public void saveState(ConfigurationSection s) {
        s.set("eveMode",       eveMode);
        s.set("autoGenerate",  autoGenerate);
        s.set("generateDelay", generateDelay);
        // measurements: pairId → int[4] (aliceBasis, aliceResult, bobBasis, bobResult)
        // Store as Map<String, String> of comma-joined ints
        Map<String, String> m = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : measurements.entrySet()) {
            int[] arr = entry.getValue();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length; i++) { if (i > 0) sb.append(','); sb.append(arr[i]); }
            m.put(String.valueOf(entry.getKey()), sb.toString());
        }
        s.set("measurements", m);
    }

    public void loadState(ConfigurationSection s) {
        eveMode       = s.getBoolean("eveMode",       false);
        autoGenerate  = s.getBoolean("autoGenerate",  false);
        generateDelay = s.getInt    ("generateDelay", 0);
        ConfigurationSection m = s.getConfigurationSection("measurements");
        if (m != null) {
            for (String k : m.getKeys(false)) {
                try {
                    String[] parts = m.getString(k).split(",");
                    int[] arr = new int[parts.length];
                    for (int i = 0; i < parts.length; i++) arr[i] = Integer.parseInt(parts[i]);
                    measurements.put(Integer.parseInt(k), arr);
                } catch (Exception ignored) { /* skip malformed entries */ }
            }
        }
    }
}
