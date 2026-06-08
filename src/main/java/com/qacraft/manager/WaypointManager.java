package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.entity.*;
import org.bukkit.util.Transformation;
import org.joml.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class WaypointManager {
    private final QAcraftPlugin plugin;
    public WaypointManager(QAcraftPlugin plugin) { this.plugin = plugin; }

    public void placeWaypoint(Player p) {
        World w = p.getWorld();
        Location loc = p.getLocation();
        int num = freeNum(w, WAYPOINT);

        // Marker
        Entity marker = w.spawnEntity(loc, EntityType.MARKER);
        marker.addScoreboardTag(WAYPOINT);
        setNum(marker, num);

        // Visual (end rod) — zero rotation so display is level
        Location spawnLoc = loc.clone(); spawnLoc.setYaw(0); spawnLoc.setPitch(0);
        BlockDisplay vis = (BlockDisplay) w.spawnEntity(spawnLoc, EntityType.BLOCK_DISPLAY);
        vis.setBlock(Material.END_ROD.createBlockData());
        vis.setTransformation(new Transformation(
            new Vector3f(-0.1f, 0f, -0.1f), new Quaternionf(), new Vector3f(0.2f, 1f, 0.2f), new Quaternionf()));
        vis.addScoreboardTag(WP_VIS);
        setNum(vis, num);

        // Label
        TextDisplay lbl = (TextDisplay) w.spawnEntity(loc.clone().add(0, 1.2, 0), EntityType.TEXT_DISPLAY);
        lbl.text(Component.text("" + num, NamedTextColor.WHITE));
        lbl.setBillboard(Display.Billboard.CENTER);
        lbl.setShadowed(true);
        lbl.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6f), new Quaternionf()));
        lbl.addScoreboardTag(WP_LBL);
        setNum(lbl, num);

        p.sendMessage(Component.text("Station #" + num, NamedTextColor.GOLD));
        w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.8f, 1.0f);
    }

    public void placeParking(Player p) {
        World w = p.getWorld();
        Location loc = p.getLocation();
        int num = freeNum(w, PARKING);

        Entity marker = w.spawnEntity(loc, EntityType.MARKER);
        marker.addScoreboardTag(PARKING);
        setNum(marker, num);

        Location pkSpawnLoc = loc.clone(); pkSpawnLoc.setYaw(0); pkSpawnLoc.setPitch(0);
        BlockDisplay vis = (BlockDisplay) w.spawnEntity(pkSpawnLoc, EntityType.BLOCK_DISPLAY);
        vis.setBlock(Material.LIGHT_WEIGHTED_PRESSURE_PLATE.createBlockData());
        vis.setGlowing(true);
        vis.setTransformation(new Transformation(
            new Vector3f(-0.25f, 0f, -0.25f), new Quaternionf(), new Vector3f(0.5f, 0.1f, 0.5f), new Quaternionf()));
        vis.addScoreboardTag(PK_VIS);
        setNum(vis, num);
        // Gold team
        Scoreboard sb = plugin.getServer().getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team team = sb.getTeam("q_gold");
        if (team == null) { team = sb.registerNewTeam("q_gold"); team.color(NamedTextColor.GOLD); }
        team.addEntity(vis);

        TextDisplay lbl = (TextDisplay) w.spawnEntity(loc.clone().add(0, 0.25, 0), EntityType.TEXT_DISPLAY);
        lbl.text(Component.text("P" + num, NamedTextColor.GOLD));
        lbl.setBillboard(Display.Billboard.CENTER);
        lbl.setShadowed(true);
        lbl.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.5f), new Quaternionf()));
        lbl.addScoreboardTag(PK_LBL);
        setNum(lbl, num);

        p.sendMessage(Component.text("Parking P" + num, NamedTextColor.GREEN));
        w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.8f, 0.8f);
    }

    public void clearAll() {
        for (World w : plugin.getServer().getWorlds()) {
            killAll(w, WAYPOINT); killAll(w, WP_VIS); killAll(w, WP_LBL);
            killAll(w, PARKING); killAll(w, PK_VIS); killAll(w, PK_LBL);
        }
    }
}
