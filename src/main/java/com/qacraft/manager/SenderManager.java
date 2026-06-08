package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.util.Transformation;
import org.joml.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class SenderManager {
    private final QAcraftPlugin plugin;
    private final java.util.Random random = new java.util.Random();
    private boolean sending = false;
    private int sendSlot = 0;   // logical slot index 0-26 (27 photons, 3 rows of 9)
    private int sendDelay = 0;

    public SenderManager(QAcraftPlugin plugin) { this.plugin = plugin; }

    // =========================================================================
    // Place transmitter
    // =========================================================================

    public void placeSender(Player p) {
        World w = p.getWorld();
        killAll(w, SENDER); killAll(w, SENDER_VIS); killAll(w, SENDER_CHEST);

        Location raw = p.getLocation();
        // Snap XZ to centre of the block the player stands on; keep feet Y
        Location loc = new Location(w,
            raw.getBlockX() + 0.5, raw.getBlockY(), raw.getBlockZ() + 0.5,
            raw.getYaw(), raw.getPitch());

        // Invisible marker at snapped location
        Entity marker = w.spawnEntity(loc, EntityType.MARKER);
        marker.addScoreboardTag(SENDER);

        // Visual: test_block[mode=start] — yellow structural outline
        Location sndLoc = loc.clone(); sndLoc.setYaw(0); sndLoc.setPitch(0);
        BlockDisplay vis = (BlockDisplay) w.spawnEntity(sndLoc, EntityType.BLOCK_DISPLAY);
        BlockData bd;
        try {
            bd = Bukkit.createBlockData("minecraft:test_block[mode=start]");
        } catch (Exception ex) {
            bd = Material.AMETHYST_BLOCK.createBlockData(); // fallback for older server builds
        }
        vis.setBlock(bd);
        vis.setGlowing(true);
        vis.setTransformation(new Transformation(
            new Vector3f(-0.3f, 0f, -0.3f), new Quaternionf(), new Vector3f(0.6f, 0.6f, 0.6f), new Quaternionf()));
        vis.addScoreboardTag(SENDER_VIS);

        // Chest guides — snap yaw to nearest 90° and spawn invisible markers
        // for two adjacent chest slots. tick() draws the Grover-style particle
        // outline around each marker; when a chest is detected the marker flashes
        // green and removes itself.
        float rawYaw = ((raw.getYaw() % 360) + 360) % 360;
        int snap = (int)(java.lang.Math.round(rawYaw / 90.0) * 90) % 360;
        int rx, rz;
        switch (snap) {
            case 0   -> { rx =  1; rz =  0; }   // facing S → guides to East
            case 90  -> { rx =  0; rz = -1; }   // facing W → guides to North
            case 180 -> { rx = -1; rz =  0; }   // facing N → guides to West
            default  -> { rx =  0; rz =  1; }   // facing E → guides to South
        }
        int bx = raw.getBlockX();
        int by = raw.getBlockY();
        int bz = raw.getBlockZ();
        spawnGuideMarker(w, bx + rx,     by, bz + rz);
        spawnGuideMarker(w, bx + rx * 2, by, bz + rz * 2);

        p.sendMessage(Component.text("Transmitter placed", NamedTextColor.LIGHT_PURPLE)
            .append(Component.text(" — place a double chest at the highlighted spots", NamedTextColor.GRAY)));
        w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
    }

    /** Spawns an invisible Marker entity whose outline is drawn by tickGuides(). */
    private void spawnGuideMarker(World w, int bx, int by, int bz) {
        Entity guide = w.spawnEntity(new Location(w, bx, by, bz), EntityType.MARKER);
        guide.addScoreboardTag(SENDER_CHEST);
    }

    // =========================================================================
    // Fill nearby double chest with 27 random basis filters
    //   Row 1 (slots  0- 8) — basis choices
    //   Row 3 (slots 18-26) — basis choices
    //   Row 5 (slots 36-44) — basis choices
    //   Rows 2, 4, 6 are cleared (used for measurement results)
    // =========================================================================

    public void fillChest(Player p) {
        World w = p.getWorld();
        Entity sender = null;
        for (Entity s : tagged(w, SENDER)) { sender = s; break; }
        if (sender == null) {
            p.sendMessage(Component.text("Place transmitter first.", NamedTextColor.RED)); return;
        }
        Block chest = findChestNear(sender.getLocation());
        if (chest == null) {
            p.sendMessage(Component.text("No chest found next to transmitter.", NamedTextColor.RED)); return;
        }
        Inventory inv = ((Container) chest.getState()).getInventory();
        if (inv.getSize() < 54) {
            p.sendMessage(Component.text("Place a double chest (two chests side by side) next to the transmitter.", NamedTextColor.RED)); return;
        }

        // Clear result rows (2, 4, 6 → slots 9-17, 27-35, 45-53)
        for (int rs : new int[]{9, 27, 45}) {
            for (int i = 0; i < 9; i++) inv.setItem(rs + i, null);
        }
        // Fill basis rows (1, 3, 5 → slots 0-8, 18-26, 36-44)
        for (int bs : new int[]{0, 18, 36}) {
            for (int i = 0; i < 9; i++) {
                Material filter = random.nextBoolean() ? Material.COMPASS : Material.RECOVERY_COMPASS;
                inv.setItem(bs + i, new ItemStack(filter, 1));
            }
        }

        p.sendMessage(Component.text("Double chest filled with 27 random basis filters!", NamedTextColor.GREEN));
        w.playSound(sender.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1f, 1.2f);
    }

    // =========================================================================
    // Start / stop
    // =========================================================================

    public boolean start(Player p) {
        World w = p.getWorld();
        if (tagged(w, SENDER).isEmpty()) {
            p.sendMessage(Component.text("Place transmitter first: ", NamedTextColor.RED)
                .append(Component.text("/qacraft sender place", NamedTextColor.YELLOW)));
            return false;
        }
        plugin.getPhotonManager().clearAll();
        sending = true; sendSlot = 0; sendDelay = 0;
        p.sendMessage(Component.text("Transmitting...", NamedTextColor.LIGHT_PURPLE));
        w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f);
        return true;
    }

    public void stop() {
        sending = false;
        Bukkit.broadcast(Component.text("Transmission stopped.", NamedTextColor.GRAY));
    }

    // =========================================================================
    // Tick — two responsibilities:
    //   1. tickGuides() — always active, draws Grover-style particle outlines
    //                     around SENDER_CHEST guide markers; removes them with a
    //                     green flash when the player places a chest there.
    //   2. Transmission — fires one photon per second while sending == true.
    //
    // Double-chest slot mapping (sendSlot 0-26):
    //   sendSlot  0- 8  → basisSlot  0- 8 (row 1), resultSlot  9-17 (row 2)
    //   sendSlot  9-17  → basisSlot 18-26 (row 3), resultSlot 27-35 (row 4)
    //   sendSlot 18-26  → basisSlot 36-44 (row 5), resultSlot 45-53 (row 6)
    // =========================================================================

    public void tick() {
        // Guide outlines run regardless of transmission state
        tickGuides();

        if (!sending) return;
        sendDelay++;
        if (sendDelay < 20) return; // 1 second between photons
        sendDelay = 0;
        if (sendSlot >= 27) { stop(); return; }

        for (World w : plugin.getServer().getWorlds()) {
            for (Entity sender : tagged(w, SENDER)) {
                Block chest = findChestNear(sender.getLocation());
                if (chest == null) continue;
                Inventory inv = ((Container) chest.getState()).getInventory();

                int group      = sendSlot / 9;
                int offset     = sendSlot % 9;
                int basisSlot  = group * 18 + offset;   // 0-8 / 18-26 / 36-44
                int resultSlot = basisSlot + 9;          // 9-17 / 27-35 / 45-53

                if (basisSlot >= inv.getSize()) { stop(); return; } // chest too small

                ItemStack filter = inv.getItem(basisSlot);
                if (filter == null || filter.getType() == Material.AIR) { sendSlot++; return; }

                int basis;
                if      (filter.getType() == Material.COMPASS)          basis = 0;
                else if (filter.getType() == Material.RECOVERY_COMPASS) basis = 1;
                else { sendSlot++; return; }

                // Bit: read from result row if glass is present; otherwise random
                int bit = random.nextInt(2);
                if (resultSlot < inv.getSize()) {
                    ItemStack bitItem = inv.getItem(resultSlot);
                    if (bitItem != null) {
                        if (bitItem.getType() == Material.RED_STAINED_GLASS)  bit = 1;
                        if (bitItem.getType() == Material.BLUE_STAINED_GLASS) bit = 0;
                    }
                }

                Location spawnLoc = sender.getLocation().add(0, 0.5, 0);
                plugin.getPhotonManager().spawnWithState(spawnLoc, basis, bit, null);

                w.spawnParticle(Particle.END_ROD, spawnLoc, 10, 0.2, 0.3, 0.2, 0.05);
                w.playSound(spawnLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
                sendSlot++;
                return;
            }
        }
    }

    // =========================================================================
    // Guide outline system (mirrors GroverManager.drawSlotOutline)
    // =========================================================================

    private void tickGuides() {
        for (World w : plugin.getServer().getWorlds()) {
            for (Entity guide : tagged(w, SENDER_CHEST)) {
                Location loc = guide.getLocation();
                Material blockType = loc.getBlock().getType();
                if (blockType == Material.CHEST || blockType == Material.TRAPPED_CHEST) {
                    // Chest placed — flash green, remove guide
                    flashGreen(w, loc);
                    guide.remove();
                } else {
                    drawSlotOutline(w, loc);
                }
            }
        }
    }

    /** Particle cube outline identical to GroverManager, throttled to every 5 ticks. */
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

    /** Green burst when a chest is placed at a guide position (same style as Grover match). */
    private void flashGreen(World w, Location loc) {
        double cx = loc.getBlockX() + 0.5;
        double cy = loc.getBlockY() + 0.5;
        double cz = loc.getBlockZ() + 0.5;
        w.spawnParticle(Particle.HAPPY_VILLAGER, cx, cy, cz, 20, 0.4, 0.4, 0.4, 0.05);
        w.spawnParticle(Particle.END_ROD,        cx, cy, cz, 8,  0.3, 0.3, 0.3, 0.02);
        w.playSound(new Location(w, cx, cy, cz), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Block findChestNear(Location loc) {
        int[][] offsets = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
        for (int[] o : offsets) {
            Block b = loc.getBlock().getRelative(o[0], o[1], o[2]);
            if (b.getType() == Material.CHEST) return b;
        }
        return null;
    }

    public void clearSender() {
        sending = false;
        for (World w : plugin.getServer().getWorlds()) {
            killAll(w, SENDER); killAll(w, SENDER_VIS); killAll(w, SENDER_CHEST);
        }
    }

    // =========================================================================
    // State persistence — called from QAcraftPlugin save/load orchestration
    // =========================================================================

    public void saveState(ConfigurationSection s) {
        s.set("sending",   sending);
        s.set("sendSlot",  sendSlot);
        s.set("sendDelay", sendDelay);
    }

    public void loadState(ConfigurationSection s) {
        sending   = s.getBoolean("sending",   false);
        sendSlot  = s.getInt    ("sendSlot",  0);
        sendDelay = s.getInt    ("sendDelay", 0);
    }
}
