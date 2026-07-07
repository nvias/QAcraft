package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;

public class EraserManager implements Listener {
    private final QAcraftPlugin plugin;

    public EraserManager(QAcraftPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onBrush(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (!ToolManager.isQuantumTool(e.getItem(), Material.BRUSH)) return;

        Player p = e.getPlayer();
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection();
        Set<Entity> toErase = new HashSet<>();

        for (double d = 1; d <= 3; d += 1) {
            Location check = eye.clone().add(dir.clone().multiply(d));
            double radius = 0.8;
            for (Entity ent : w.getNearbyEntities(check, radius, radius, radius)) {
                Set<String> tags = ent.getScoreboardTags();
                if (tags.contains(PHOTON)) { addPair(w, toErase, ent, PH_LBL); }
                else if (tags.contains(PH_LBL)) { addPair(w, toErase, ent, PHOTON); }
                else if (tags.contains(WAYPOINT)) { addGroup(w, toErase, ent, WP_VIS, WP_LBL); }
                else if (tags.contains(WP_VIS)) { addGroup(w, toErase, ent, WAYPOINT, WP_LBL); }
                else if (tags.contains(WP_LBL)) { addGroup(w, toErase, ent, WAYPOINT, WP_VIS); }
                else if (tags.contains(PARKING)) { addGroup(w, toErase, ent, PK_VIS, PK_LBL); }
                else if (tags.contains(PK_VIS)) { addGroup(w, toErase, ent, PARKING, PK_LBL); }
                else if (tags.contains(PK_LBL)) { addGroup(w, toErase, ent, PARKING, PK_VIS); }
                else if (tags.contains(GATE)) { addGroup(w, toErase, ent, GATE_VIS, GATE_LBL); addSameTagGroup(w, toErase, ent, GATE_CHEST_GUIDE); }
                else if (tags.contains(GATE_VIS)) { addGroup(w, toErase, ent, GATE, GATE_LBL); addSameTagGroup(w, toErase, ent, GATE_CHEST_GUIDE); }
                else if (tags.contains(GATE_LBL)) { addGroup(w, toErase, ent, GATE, GATE_VIS); addSameTagGroup(w, toErase, ent, GATE_CHEST_GUIDE); }
                else if (tags.contains(GATE_CHEST_GUIDE)) { addGroup(w, toErase, ent, GATE, GATE_VIS); Entity gl = findLabel(w, GATE_LBL, num(ent)); if (gl != null) toErase.add(gl); addSameTagGroup(w, toErase, ent, GATE_CHEST_GUIDE); }
                // Sender: erasing any part removes the marker, visual AND the chest-guide spots
                else if (tags.contains(SENDER) || tags.contains(SENDER_VIS) || tags.contains(SENDER_CHEST)) {
                    for (Entity s : tagged(w, SENDER))       toErase.add(s);
                    for (Entity s : tagged(w, SENDER_VIS))   toErase.add(s);
                    for (Entity s : tagged(w, SENDER_CHEST)) toErase.add(s);
                }
                // E91 landmarks — source/alice/bob are single: kill all entities sharing tag
                else if (tags.contains(E91_SRC))    { tagged(w, E91_SRC).forEach(toErase::add); }
                else if (tags.contains(E91_ALICE))  { tagged(w, E91_ALICE).forEach(toErase::add); }
                else if (tags.contains(E91_BOB))    { tagged(w, E91_BOB).forEach(toErase::add); }
                // Parking pads are multiple — erase only the specific pad that was touched (matched by num)
                else if (tags.contains(E91_PARK_A)) { addSameTagGroup(w, toErase, ent, E91_PARK_A); }
                else if (tags.contains(E91_PARK_B)) { addSameTagGroup(w, toErase, ent, E91_PARK_B); }
                // E91 photons and their labels (erase one side at a time)
                else if (tags.contains(E91_PHOTON)) { addE91PhotonGroup(w, toErase, ent); }
                else if (tags.contains(E91_LBL))    { addE91PhotonGroup(w, toErase, ent); }
            }
        }

        if (!toErase.isEmpty()) {
            Location fx = toErase.iterator().next().getLocation();
            w.spawnParticle(Particle.SMOKE, fx, 5, 0.1, 0.1, 0.1, 0.02);
            w.playSound(fx, Sound.BLOCK_GRAVEL_BREAK, 0.8f, 0.8f);
            toErase.forEach(Entity::remove);
            p.sendMessage(Component.text("Erased", NamedTextColor.GRAY));
        }
    }

    private void addPair(World w, Set<Entity> set, Entity e, String pairTag) {
        set.add(e);
        int n = num(e);
        Entity pair = findLabel(w, pairTag, n);
        if (pair != null) set.add(pair);
    }

    private void addGroup(World w, Set<Entity> set, Entity e, String tag2, String tag3) {
        set.add(e);
        int n = num(e);
        Entity e2 = findLabel(w, tag2, n);
        Entity e3 = findLabel(w, tag3, n);
        if (e2 != null) set.add(e2);
        if (e3 != null) set.add(e3);
    }

    /** Erase all entities of one specific landing pad (marker + vis + label) matched by num. */
    private void addSameTagGroup(World w, Set<Entity> set, Entity touched, String tag) {
        int n = num(touched);
        for (Entity e : tagged(w, tag)) {
            if (num(e) == n) set.add(e);
        }
    }

    /**
     * Erase one side of an E91 entangled pair (photon + its label).
     * The partner photon on the other side is left untouched.
     */
    private void addE91PhotonGroup(World w, Set<Entity> set, Entity e) {
        int pId = pair(e);
        // Determine which side: 0=Alice (E91_PA), 1=Bob (E91_PB)
        int side;
        if (e.getScoreboardTags().contains(E91_PHOTON)) {
            side = e.getScoreboardTags().contains(E91_PA) ? 0 : 1;
        } else { // E91_LBL
            side = num(e);
        }
        // Add the photon for this side
        for (Entity ph : tagged(w, E91_PHOTON)) {
            if (pair(ph) == pId) {
                boolean phIsAlice = ph.getScoreboardTags().contains(E91_PA);
                if ((side == 0) == phIsAlice) { set.add(ph); break; }
            }
        }
        // Add the label for this side
        for (Entity lbl : tagged(w, E91_LBL)) {
            if (pair(lbl) == pId && num(lbl) == side) { set.add(lbl); break; }
        }
    }
}
