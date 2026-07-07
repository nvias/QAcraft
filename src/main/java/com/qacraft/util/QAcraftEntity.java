package com.qacraft.util;
import org.bukkit.*;
import org.bukkit.entity.*;
import java.util.*;

public class QAcraftEntity {
    public static final String PHOTON="q_photon", SUPERPOSITION="q_superposition",
        TRAVELING="q_traveling", ARRIVED="q_arrived", FLASHING="q_flashing",
        WAYPOINT="q_waypoint", WP_VIS="q_wp_visual", WP_LBL="q_wp_label",
        PARKING="q_parking", PK_VIS="q_pk_visual", PK_LBL="q_pk_label",
        GATE="q_gate", GATE_VIS="q_gate_visual", GATE_LBL="q_gate_label",
        GATE_FLASH="q_gate_flashing", GATE_CHEST_GUIDE="q_gate_chest_guide",
        SENDER="q_sender", SENDER_VIS="q_sender_visual",
        SENDER_CHEST="q_sender_chest",
        PH_LBL="q_photon_label",
        // E91 protocol — completely independent of BB84 entities above
        E91_SRC    = "q_e91_src",      // all source entities (marker + vis + label share this tag)
        E91_ALICE  = "q_e91_end_a",    // all Alice endpoint entities
        E91_BOB    = "q_e91_end_b",    // all Bob endpoint entities
        E91_PHOTON = "q_e91_photon",   // all E91 photon BlockDisplays
        E91_PA     = "q_e91_pa",       // Alice's photon of a pair (also has E91_PHOTON)
        E91_PB     = "q_e91_pb",       // Bob's photon of a pair  (also has E91_PHOTON)
        E91_MOVING = "q_e91_moving",   // photon is in flight
        E91_READY  = "q_e91_ready",    // photon arrived, awaiting measurement
        E91_LBL    = "q_e91_lbl",      // photon pair TextDisplay labels
        E91_PARK_A = "q_e91_park_a",   // Alice's flat parking pad (marker + vis + label)
        E91_PARK_B = "q_e91_park_b",   // Bob's flat parking pad (marker + vis + label)
        Q_PLASMA   = "q_plasma",       // Lobby plasma anchor (Marker entity) — rendered by PlasmaManager
        Q_TUTORIAL_DISPLAY = "q_tutorial_display", // TextDisplay tutorial station signs
        Q_WORLD    = "q_world",        // Tutorial-world builder entities (titles, panels, showcases, origin)
        Q_TUT      = "q_tut",          // Interactive-walkthrough runtime entities (step labels, next-room button)
        Q_TOOLBTN  = "q_toolbtn";      // In-room "give tools" button marker + its label (num = 1 bb84 / 2 grover / 3 e91)

    public static List<Entity> tagged(World w, String tag) {
        List<Entity> r = new ArrayList<>();
        for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(tag)) r.add(e);
        return r;
    }
    public static Entity nearest(Location l, String tag, double r) {
        Entity n = null; double d = r;
        for (Entity e : l.getWorld().getNearbyEntities(l, r, r, r))
            if (e.getScoreboardTags().contains(tag) && e.getLocation().distance(l) < d) {
                d = e.getLocation().distance(l); n = e;
            }
        return n;
    }
    public static void killAll(World w, String tag) { tagged(w, tag).forEach(Entity::remove); }
    public static int freeNum(World w, String tag) {
        Set<Integer> u = new HashSet<>();
        tagged(w, tag).forEach(e -> u.add(num(e)));
        for (int i = 1; i <= 999; i++) if (!u.contains(i)) return i;
        return 1;
    }
    // Tag-based metadata
    static void setMeta(Entity e, String k, int v) {
        e.getScoreboardTags().removeIf(t -> t.startsWith(k + ":"));
        e.getScoreboardTags().add(k + ":" + v);
    }
    static int getMeta(Entity e, String k, int def) {
        for (String t : e.getScoreboardTags()) if (t.startsWith(k + ":")) return Integer.parseInt(t.substring(k.length()+1));
        return def;
    }
    public static void setNum(Entity e, int v) { setMeta(e, "num", v); }
    public static int num(Entity e) { return getMeta(e, "num", 0); }
    /** Grover instance id — lets multiple Grover searches coexist. */
    public static void setGid(Entity e, int v) { setMeta(e, "gid", v); }
    public static int gid(Entity e) { return getMeta(e, "gid", 0); }
    public static void setBasis(Entity e, int v) { setMeta(e, "basis", v); }
    public static int basis(Entity e) { return getMeta(e, "basis", 0); }
    public static void setBit(Entity e, int v) { setMeta(e, "bit", v); }
    public static int bit(Entity e) { return getMeta(e, "bit", 0); }
    public static void setTarget(Entity e, int v) { setMeta(e, "target", v); }
    public static int target(Entity e) { return getMeta(e, "target", 1); }
    public static void setAge(Entity e, int v) { setMeta(e, "age", v); }
    public static int age(Entity e) { return getMeta(e, "age", 0); }
    public static void setFlash(Entity e, int v) { setMeta(e, "flash", v); }
    public static int flash(Entity e) { return getMeta(e, "flash", 0); }
    public static void setLastGate(Entity e, int v) { setMeta(e, "lastgate", v); }
    public static int lastGate(Entity e) { return getMeta(e, "lastgate", 0); }
    public static Entity findLabel(World w, String tag, int n) {
        for (Entity e : tagged(w, tag)) if (num(e) == n) return e;
        return null;
    }
    /** Returns the first MARKER entity in the tagged group, or null. */
    public static Entity markerIn(World w, String tag) {
        for (Entity e : tagged(w, tag))
            if (e.getType() == EntityType.MARKER) return e;
        return null;
    }
    // E91 pair ID metadata
    public static void setPair(Entity e, int v) { setMeta(e, "pair", v); }
    public static int  pair   (Entity e)        { return getMeta(e, "pair", -1); }
}
