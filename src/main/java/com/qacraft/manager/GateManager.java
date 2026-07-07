package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;


public class GateManager {
    private final QAcraftPlugin plugin;
    private final java.util.Random random = new java.util.Random();

    private static final int[] BASIS_SLOTS = {0,1,2,3,4,5,6,7,8, 18,19,20,21,22,23,24,25,26, 36,37,38,39,40,41,42,43,44};
    private static final String MISSING_TAG = "q_missing_gate";

    public GateManager(QAcraftPlugin plugin) { this.plugin = plugin; }

    public void placeGate(Player p) {
        World w = p.getWorld();
        // Snap XZ to centre of the block the player stands on; keep feet Y
        Location raw = p.getLocation();
        Location loc = new Location(w,
            raw.getBlockX() + 0.5, raw.getBlockY(), raw.getBlockZ() + 0.5,
            raw.getYaw(), raw.getPitch());
        int num = freeNum(w, GATE);

        Entity marker = w.spawnEntity(loc, EntityType.MARKER);
        marker.addScoreboardTag(GATE);
        setNum(marker, num);

        // Snap player yaw to nearest 45° out of 8 directions (0,45,90,135,180,225,270,315).
        // JOML rotateY() is CCW; Minecraft yaw is CW — negate so the gate faces the player's direction.
        // Gate normal after rotateY(θ) = (sinθ, 0, cosθ); player direction = (−sinY, 0, cosY).
        // Setting θ = −Y makes them parallel → gate is perpendicular to player's view for all 8 snaps.
        float rawYaw = ((loc.getYaw() % 360) + 360) % 360;
        float snappedYaw = (float)(java.lang.Math.round(rawYaw / 45.0) * 45 % 360);
        float theta = (float) java.lang.Math.toRadians(-snappedYaw);   // <-- negated
        float cosT = (float) java.lang.Math.cos(theta);
        float sinT = (float) java.lang.Math.sin(theta);
        // Recompute translation so the 1×1×0.1 slab stays centred at entity origin after Y-rotation.
        float tx = -0.5f * cosT - 0.05f * sinT;
        float tz =  0.5f * sinT - 0.05f * cosT;
        Quaternionf leftRot = new Quaternionf().rotateY(theta);

        Location gateLoc = loc.clone(); gateLoc.setYaw(0); gateLoc.setPitch(0);
        BlockDisplay vis = (BlockDisplay) w.spawnEntity(gateLoc, EntityType.BLOCK_DISPLAY);
        vis.setBlock(Bukkit.createBlockData("minecraft:nether_portal[axis=x]"));
        vis.setGlowing(true);
        vis.setTransformation(new Transformation(
            new Vector3f(tx, 0f, tz), leftRot, new Vector3f(1f, 1f, 0.1f), new Quaternionf()));
        vis.addScoreboardTag(GATE_VIS);
        setNum(vis, num);
        Scoreboard sb = plugin.getServer().getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team team = sb.getTeam("q_purple");
        if (team == null) { team = sb.registerNewTeam("q_purple"); team.color(NamedTextColor.LIGHT_PURPLE); }
        team.addEntity(vis);

        TextDisplay lbl = (TextDisplay) w.spawnEntity(loc.clone().add(0, 1.2, 0), EntityType.TEXT_DISPLAY);
        lbl.text(Component.text("G" + num, NamedTextColor.LIGHT_PURPLE));
        lbl.setBillboard(Display.Billboard.CENTER);
        lbl.setShadowed(true);
        lbl.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6f), new Quaternionf()));
        lbl.addScoreboardTag(GATE_LBL);
        setNum(lbl, num);

        // Chest guide — snap yaw to nearest 90° (chests are axis-aligned) and
        // place an invisible marker whose particle outline shows where to put the
        // filter chest.  tick() draws the outline; when a chest is detected the
        // marker flashes green and removes itself.
        int snap90 = (int)(java.lang.Math.round(rawYaw / 90.0) * 90) % 360;
        int rx, rz;
        switch (snap90) {
            case 0   -> { rx =  1; rz =  0; }   // facing S → guide to East
            case 90  -> { rx =  0; rz = -1; }   // facing W → guide to North
            case 180 -> { rx = -1; rz =  0; }   // facing N → guide to West
            default  -> { rx =  0; rz =  1; }   // facing E → guide to South
        }
        for (int i = 1; i <= 2; i++) {
            Entity chestGuide = w.spawnEntity(
                new Location(w, raw.getBlockX() + rx * i, raw.getBlockY(), raw.getBlockZ() + rz * i),
                EntityType.MARKER);
            chestGuide.addScoreboardTag(GATE_CHEST_GUIDE);
            setNum(chestGuide, num);
        }

        p.sendMessage(Component.text("Measurement Gate G" + num, NamedTextColor.LIGHT_PURPLE)
            .append(Component.text(" — place chest with filter at the highlighted spot", NamedTextColor.GRAY)));
        w.playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 1.5f);
    }

    public void checkGate(Entity photon) {
        World w = photon.getWorld();
        Entity gate = nearest(photon.getLocation(), GATE, 1.5);
        if (gate == null) return;

        int gateNum = num(gate);
        if (lastGate(photon) == gateNum) return; // Already measured here
        setLastGate(photon, gateNum);

        // Find chest nearby gate
        Location gLoc = gate.getLocation();
        Block chest = findChestNear(gLoc);
        if (chest == null) return;

        Inventory inv = ((Container) chest.getState()).getInventory();
        // Map photon number to double-chest basis slot (same formula as SenderManager.tick)
        // photon 1-9  → basisSlot  0- 8 (row 1), resultSlot  9-17 (row 2)
        // photon 10-18 → basisSlot 18-26 (row 3), resultSlot 27-35 (row 4)
        // photon 19-27 → basisSlot 36-44 (row 5), resultSlot 45-53 (row 6)
        int sendSlot   = num(photon) - 1;
        int group      = sendSlot / 9;
        int offset     = sendSlot % 9;
        int slot       = group * 18 + offset;    // basisSlot
        if (slot < 0 || slot >= inv.getSize()) return;

        ItemStack filter = inv.getItem(slot);
        if (filter == null) return;

        int gateBasis;
        if (filter.getType() == Material.COMPASS) gateBasis = 0;
        else if (filter.getType() == Material.RECOVERY_COMPASS) gateBasis = 1;
        else return;

        // Measure
        int phBasis = basis(photon);
        int phBit = bit(photon);
        int result;
        if (gateBasis == phBasis) {
            result = phBit;
        } else {
            result = random.nextInt(2);
            setBasis(photon, gateBasis);
            setBit(photon, result);
        }

        // Write result to corresponding result row (row 2, 4, or 6)
        int resultSlot = slot + 9;
        inv.setItem(resultSlot, new ItemStack(result == 0 ? Material.BLUE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE, 1));

        // Flash gate visual
        Entity gateVis = findLabel(w, GATE_VIS, gateNum);
        if (gateVis instanceof BlockDisplay bd) {
            bd.setBlock(Material.WHITE_STAINED_GLASS.createBlockData());
            gateVis.addScoreboardTag(GATE_FLASH);
            setFlash(gateVis, 0);
        }

        w.playSound(gLoc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5f, 1.5f);
        w.spawnParticle(Particle.REVERSE_PORTAL, gLoc.add(0, 0.5, 0), 20, 0.3, 0.5, 0.3, 0.05);
    }

    public void tick() {
        for (World w : plugin.getServer().getWorlds()) {
            // Gate flash restore
            for (Entity e : tagged(w, GATE_FLASH)) {
                int f = flash(e) + 1;
                setFlash(e, f);
                if (f >= 15 && e instanceof BlockDisplay bd) {
                    bd.setBlock(Bukkit.createBlockData("minecraft:nether_portal[axis=x]"));
                    e.getScoreboardTags().remove(GATE_FLASH);
                    setFlash(e, 0);
                }
            }
            // "Missing: filters" display above each gate's double chest
            Set<Entity> keep = new HashSet<>();
            for (Entity gate : tagged(w, GATE)) {
                Block chest = findDoubleChestNear(gate.getLocation());
                if (chest == null) continue;
                TextDisplay d = ensureMissing(w, chest, gateMissing(chest));
                if (d != null) keep.add(d);
            }
            for (Entity e : new ArrayList<>(tagged(w, MISSING_TAG))) if (!keep.contains(e)) e.remove();

            // Chest guide outlines — persistent so they return if the chest is removed
            for (Entity guide : tagged(w, GATE_CHEST_GUIDE)) {
                Location gloc = guide.getLocation();
                Material bt = gloc.getBlock().getType();
                boolean filled = guide.getScoreboardTags().contains("q_filled");
                if (bt == Material.CHEST || bt == Material.TRAPPED_CHEST) {
                    if (!filled) { flashGreen(w, gloc); guide.addScoreboardTag("q_filled"); }
                } else {
                    if (filled) guide.getScoreboardTags().remove("q_filled");
                    drawSlotOutline(w, gloc);
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

    /** Green burst when a chest is placed at the guide position. */
    private void flashGreen(World w, Location loc) {
        double cx = loc.getBlockX() + 0.5;
        double cy = loc.getBlockY() + 0.5;
        double cz = loc.getBlockZ() + 0.5;
        w.spawnParticle(Particle.HAPPY_VILLAGER, cx, cy, cz, 20, 0.4, 0.4, 0.4, 0.05);
        w.spawnParticle(Particle.END_ROD,        cx, cy, cz, 8,  0.3, 0.3, 0.3, 0.02);
        w.playSound(new Location(w, cx, cy, cz), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
    }

    private Block findChestNear(Location loc) {
        int[][] offsets = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
        for (int[] o : offsets) {
            Block b = loc.getBlock().getRelative(o[0], o[1], o[2]);
            if (b.getType() == Material.CHEST) return b;
        }
        return null;
    }

    private Block findDoubleChestNear(Location loc) {
        int[][] offsets = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{2,0,0},{-2,0,0},{0,0,2},{0,0,-2},
                           {1,0,1},{1,0,-1},{-1,0,1},{-1,0,-1}};
        for (int[] o : offsets) {
            Block b = loc.getBlock().getRelative(o[0], o[1], o[2]);
            if (b.getType() == Material.CHEST && b.getState() instanceof Container c && c.getInventory().getSize() >= 54) return b;
        }
        return null;
    }

    private String gateMissing(Block chest) {
        Inventory inv = ((Container) chest.getState()).getInventory();
        for (int s : BASIS_SLOTS) {
            ItemStack it = inv.getItem(s);
            if (it == null || (it.getType() != Material.COMPASS && it.getType() != Material.RECOVERY_COMPASS)) return "Missing: filters";
        }
        return null;
    }

    private TextDisplay ensureMissing(World w, Block chest, String missing) {
        Location at = chest.getLocation().add(0.5, 2.0, 0.5);
        TextDisplay disp = null;
        for (Entity e : w.getNearbyEntities(at, 1.0, 2.0, 1.0)) {
            if (e instanceof TextDisplay td && e.getScoreboardTags().contains(MISSING_TAG)) { disp = td; break; }
        }
        if (missing == null) { if (disp != null) disp.remove(); return null; }
        if (disp == null) {
            disp = (TextDisplay) w.spawnEntity(at, EntityType.TEXT_DISPLAY);
            disp.setBillboard(Display.Billboard.CENTER);
            disp.setShadowed(true);
            disp.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.8f), new Quaternionf()));
            disp.addScoreboardTag(MISSING_TAG);
        }
        disp.text(Component.text(missing, NamedTextColor.RED));
        return disp;
    }

    public void clearAll() {
        for (World w : plugin.getServer().getWorlds()) {
            killAll(w, GATE); killAll(w, GATE_VIS); killAll(w, GATE_LBL); killAll(w, GATE_CHEST_GUIDE); killAll(w, MISSING_TAG);
        }
    }
}
