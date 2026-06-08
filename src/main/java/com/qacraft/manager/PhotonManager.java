package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PhotonManager {
    private final QAcraftPlugin plugin;
    private final java.util.Random random = new java.util.Random();

    public PhotonManager(QAcraftPlugin plugin) { this.plugin = plugin; }

    public Entity spawnSuperposition(Location loc, Player sender) {
        World w = loc.getWorld();
        int num = freeNum(w, PHOTON);

        Location spawnLoc = loc.clone(); spawnLoc.setYaw(0); spawnLoc.setPitch(0);
        BlockDisplay photon = (BlockDisplay) w.spawnEntity(spawnLoc, EntityType.BLOCK_DISPLAY);
        photon.setBlock(Material.WHITE_STAINED_GLASS.createBlockData());
        photon.setGlowing(true);
        photon.setTransformation(new Transformation(
            new Vector3f(-0.25f, -0.25f, -0.25f), new Quaternionf(), new Vector3f(0.5f), new Quaternionf()));
        photon.addScoreboardTag(PHOTON);
        photon.addScoreboardTag(SUPERPOSITION);
        setNum(photon, num);
        setBasis(photon, random.nextInt(2));
        setBit(photon, random.nextInt(2));
        setAge(photon, 0);
        setFlash(photon, 0);
        setTarget(photon, 1);
        setLastGate(photon, 0);
        setTeamColor(photon, "q_white");

        TextDisplay label = (TextDisplay) w.spawnEntity(loc.clone().add(0, 0.55, 0), EntityType.TEXT_DISPLAY);
        label.text(Component.text("F" + num, NamedTextColor.WHITE));
        label.setBillboard(Display.Billboard.CENTER);
        label.setShadowed(true);
        label.setTransformation(new Transformation(
            new Vector3f(), new Quaternionf(), new Vector3f(0.6f), new Quaternionf()));
        label.addScoreboardTag(PH_LBL);
        setNum(label, num);

        if (!tagged(w, WAYPOINT).isEmpty()) photon.addScoreboardTag(TRAVELING);

        if (sender != null) {
            sender.sendMessage(Component.text("F" + num, NamedTextColor.WHITE)
                .append(Component.text(" sent in superposition", NamedTextColor.GRAY)));
        }
        w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        return photon;
    }

    public Entity spawnWithState(Location loc, int basis, int bit, Player sender) {
        World w = loc.getWorld();
        int num = freeNum(w, PHOTON);

        Location spawnLoc2 = loc.clone(); spawnLoc2.setYaw(0); spawnLoc2.setPitch(0);
        BlockDisplay photon = (BlockDisplay) w.spawnEntity(spawnLoc2, EntityType.BLOCK_DISPLAY);
        photon.setBlock(Material.WHITE_STAINED_GLASS.createBlockData());
        photon.setGlowing(true);
        photon.setTransformation(new Transformation(
            new Vector3f(-0.25f, -0.25f, -0.25f), new Quaternionf(), new Vector3f(0.5f), new Quaternionf()));
        photon.addScoreboardTag(PHOTON);
        photon.addScoreboardTag(SUPERPOSITION);
        setNum(photon, num);
        setBasis(photon, basis);
        setBit(photon, bit);
        setAge(photon, 0);
        setFlash(photon, 0);
        setTarget(photon, 1);
        setLastGate(photon, 0);
        setTeamColor(photon, "q_white");

        TextDisplay label = (TextDisplay) w.spawnEntity(loc.clone().add(0, 0.55, 0), EntityType.TEXT_DISPLAY);
        label.text(Component.text("F" + num, NamedTextColor.WHITE));
        label.setBillboard(Display.Billboard.CENTER);
        label.setShadowed(true);
        label.setTransformation(new Transformation(
            new Vector3f(), new Quaternionf(), new Vector3f(0.6f), new Quaternionf()));
        label.addScoreboardTag(PH_LBL);
        setNum(label, num);

        if (!tagged(w, WAYPOINT).isEmpty()) photon.addScoreboardTag(TRAVELING);

        if (sender != null) {
            String basisName = basis == 0 ? "Rectilinear +" : "Diagonal x";
            NamedTextColor c = basis == 0 ? NamedTextColor.AQUA : NamedTextColor.LIGHT_PURPLE;
            NamedTextColor bc = bit == 0 ? NamedTextColor.BLUE : NamedTextColor.RED;
            sender.sendMessage(Component.text("F" + num, c)
                .append(Component.text(" | " + basisName + " | bit = ", NamedTextColor.GRAY))
                .append(Component.text("" + bit, bc)));
        }
        w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        return photon;
    }

    public void tick() {
        for (World w : plugin.getServer().getWorlds()) {
            for (Entity e : tagged(w, SUPERPOSITION)) {
                setAge(e, age(e) + 1);
            }
            for (Entity e : tagged(w, FLASHING)) {
                int f = flash(e) + 1;
                setFlash(e, f);
                if (f >= 20) flashEnd(e);
            }
            for (Entity e : tagged(w, TRAVELING)) {
                photonStep(e);
            }
        }
    }

    private void photonStep(Entity photon) {
        World w = photon.getWorld();
        int targetNum = target(photon);

        Entity targetWp = null;
        for (Entity wp : tagged(w, WAYPOINT)) {
            if (num(wp) == targetNum) { targetWp = wp; break; }
        }

        if (targetWp != null) {
            Location from = photon.getLocation();
            Location to = targetWp.getLocation();
            org.bukkit.util.Vector dir = to.toVector().subtract(from.toVector()).normalize().multiply(0.3);
            photon.teleport(from.add(dir));
            w.spawnParticle(Particle.END_ROD, photon.getLocation(), 1, 0, 0, 0, 0.01);

            Entity label = findLabel(w, PH_LBL, num(photon));
            if (label != null) label.teleport(photon.getLocation().add(0, 0.55, 0));

            if (photon.getScoreboardTags().contains(SUPERPOSITION) && age(photon) >= 20) {
                plugin.getGateManager().checkGate(photon);
            }

            if (from.distance(to) < 0.5) {
                setTarget(photon, targetNum + 1);
            }
        } else {
            park(photon);
        }
    }

    private void park(Entity photon) {
        World w = photon.getWorld();
        int pNum = num(photon);
        Entity parkSpot = null;
        for (Entity pk : tagged(w, PARKING)) {
            if (num(pk) == pNum) { parkSpot = pk; break; }
        }
        if (parkSpot != null) {
            photon.teleport(parkSpot.getLocation().add(0, 0.5, 0));
        }
        Entity label = findLabel(w, PH_LBL, pNum);
        if (label != null) label.teleport(photon.getLocation().add(0, 0.55, 0));

        photon.getScoreboardTags().remove(TRAVELING);
        photon.addScoreboardTag(ARRIVED);
        setAge(photon, 0);
        w.playSound(photon.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.0f);
    }

    public void flashStart(Entity photon, int result) {
        BlockDisplay bd = (BlockDisplay) photon;
        bd.setBlock(result == 0 ? Material.BLUE_STAINED_GLASS.createBlockData() : Material.RED_STAINED_GLASS.createBlockData());
        setTeamColor(photon, result == 0 ? "q_blue" : "q_red");
        photon.addScoreboardTag(FLASHING);
        photon.getScoreboardTags().remove(SUPERPOSITION);
        setFlash(photon, 0);

        World w = photon.getWorld();
        Entity label = findLabel(w, PH_LBL, num(photon));
        if (label instanceof TextDisplay td) {
            NamedTextColor c = result == 0 ? NamedTextColor.BLUE : NamedTextColor.RED;
            td.text(Component.text("F" + num(photon), c));
        }
        w.spawnParticle(Particle.END_ROD, photon.getLocation(), 15, 0.2, 0.2, 0.2, 0.05);
    }

    private void flashEnd(Entity photon) {
        BlockDisplay bd = (BlockDisplay) photon;
        bd.setBlock(Material.WHITE_STAINED_GLASS.createBlockData());
        setTeamColor(photon, "q_white");
        photon.getScoreboardTags().remove(FLASHING);
        photon.addScoreboardTag(SUPERPOSITION);
        setFlash(photon, 0);
        setAge(photon, 20);

        Entity label = findLabel(photon.getWorld(), PH_LBL, num(photon));
        if (label instanceof TextDisplay td) {
            td.text(Component.text("F" + num(photon), NamedTextColor.WHITE));
        }
    }

    public void clearAll() {
        for (World w : plugin.getServer().getWorlds()) {
            killAll(w, PHOTON);
            killAll(w, PH_LBL);
        }
    }

    private void setTeamColor(Entity e, String teamName) {
        Scoreboard sb = plugin.getServer().getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(teamName);
        if (team == null) {
            team = sb.registerNewTeam(teamName);
            switch (teamName) {
                case "q_white" -> team.color(NamedTextColor.WHITE);
                case "q_blue" -> team.color(NamedTextColor.BLUE);
                case "q_red" -> team.color(NamedTextColor.RED);
                case "q_gold" -> team.color(NamedTextColor.GOLD);
                case "q_purple" -> team.color(NamedTextColor.LIGHT_PURPLE);
            }
        }
        team.addEntity(e);
    }
}
