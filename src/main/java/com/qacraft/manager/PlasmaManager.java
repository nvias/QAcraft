package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;

/**
 * Summon-on-demand particle "atom" effect.
 *
 * Each summon spawns an invisible MARKER entity tagged {@code q_plasma}.
 * Markers persist with chunks like any other quantum landmark — they survive
 * server restarts without any YAML/state file.
 *
 * Every game tick the manager iterates all {@code q_plasma} markers in loaded
 * worlds and renders three particle layers around each one:
 *   1. Three orbital rings (blue/purple/green) in mutually perpendicular planes,
 *      each rotating at a slightly different speed.
 *   2. A pulsing central core (END_ROD cluster + cyan dust haze).
 *   3. Five tilted-orbit "electrons" — single END_ROD particles per tick whose
 *      natural ~1 s lifetime leaves a brief trail.
 *
 * Rendering is skipped for plasmas with no players within 60 blocks (perf guard).
 */
public class PlasmaManager {

    private final QAcraftPlugin plugin;
    private int phase = 0;

    private static final org.bukkit.Color BB84_BLUE     = org.bukkit.Color.fromRGB(0x2E, 0x74, 0xB5);
    private static final org.bukkit.Color GROVER_PURPLE = org.bukkit.Color.fromRGB(0x7B, 0x2D, 0x8B);
    private static final org.bukkit.Color E91_GREEN     = org.bukkit.Color.fromRGB(0x3C, 0xB3, 0x71);
    private static final org.bukkit.Color CORE_CYAN     = org.bukkit.Color.fromRGB(0x66, 0xFF, 0xFF);

    private static final int    RING_POINTS    = 40;
    private static final double RING_RADIUS    = 2.5;
    private static final int    N_ELECTRONS    = 5;
    private static final double ELECTRON_R     = 4.5;
    private static final double RENDER_RANGE   = 60.0;

    /** Per-plasma electron orbital parameters, keyed by marker UUID (lazily filled). */
    private final Map<UUID, double[]> electronAngles = new HashMap<>();
    private final Map<UUID, double[]> electronTilts  = new HashMap<>();
    private final Random random = new Random();

    public PlasmaManager(QAcraftPlugin plugin) { this.plugin = plugin; }

    // =========================================================================
    // Command-driven API
    // =========================================================================

    public void summon(Player p) {
        World w = p.getWorld();
        // Place at eye level so the atom is at head height instead of at the feet
        Location loc = p.getEyeLocation();
        Entity anchor = w.spawnEntity(loc, EntityType.MARKER);
        anchor.addScoreboardTag(Q_PLASMA);
        p.sendMessage(Component.text("Plasma summoned", NamedTextColor.AQUA)
            .append(Component.text(" at " + (int) loc.getX() + ", " + (int) loc.getY() + ", " + (int) loc.getZ(),
                NamedTextColor.GRAY)));
        w.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);
        w.spawnParticle(Particle.REVERSE_PORTAL, loc, 40, 0.5, 0.5, 0.5, 0.1);
    }

    public void despawnNearest(Player p) {
        World w = p.getWorld();
        Entity nearest = null;
        double bestDist = 10.0 * 10.0; // 10-block radius squared
        for (Entity e : tagged(w, Q_PLASMA)) {
            double d2 = e.getLocation().distanceSquared(p.getLocation());
            if (d2 < bestDist) { bestDist = d2; nearest = e; }
        }
        if (nearest == null) {
            p.sendMessage(Component.text("No plasma within 10 blocks.", NamedTextColor.RED));
            return;
        }
        Location loc = nearest.getLocation();
        electronAngles.remove(nearest.getUniqueId());
        electronTilts.remove(nearest.getUniqueId());
        nearest.remove();
        w.spawnParticle(Particle.REVERSE_PORTAL, loc, 30, 0.5, 0.5, 0.5, 0.1);
        w.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.5f);
        p.sendMessage(Component.text("Plasma despawned.", NamedTextColor.GRAY));
    }

    public void clearAll() {
        electronAngles.clear();
        electronTilts.clear();
        for (World w : plugin.getServer().getWorlds()) killAll(w, Q_PLASMA);
    }

    // =========================================================================
    // Tick — render every active plasma
    // =========================================================================

    public void tick() {
        phase++;
        for (World w : plugin.getServer().getWorlds()) {
            for (Entity anchor : tagged(w, Q_PLASMA)) {
                Location centre = anchor.getLocation();
                if (w.getNearbyPlayers(centre, RENDER_RANGE).isEmpty()) continue;
                renderPlasma(w, centre, anchor.getUniqueId());
            }
        }
    }

    private void renderPlasma(World w, Location centre, UUID id) {
        renderRings(w, centre);
        renderCore(w, centre);
        renderElectrons(w, centre, id);
    }

    // =========================================================================
    // Layer 1 — three orbital rings (XY, XZ, YZ planes)
    // =========================================================================

    private void renderRings(World w, Location c) {
        double base = phase * 0.03;
        drawRing(w, c, BB84_BLUE,     0, base);
        drawRing(w, c, GROVER_PURPLE, 1, base * 1.2);
        drawRing(w, c, E91_GREEN,     2, base * 1.5);
    }

    private void drawRing(World w, Location c, org.bukkit.Color color, int planeAxis, double phaseOffset) {
        Particle.DustOptions opts = new Particle.DustOptions(color, 1.2f);
        double cx0 = c.getX(), cy0 = c.getY(), cz0 = c.getZ();
        for (int i = 0; i < RING_POINTS; i++) {
            double theta = (2.0 * java.lang.Math.PI * i / RING_POINTS) + phaseOffset;
            double a = java.lang.Math.cos(theta) * RING_RADIUS;
            double b = java.lang.Math.sin(theta) * RING_RADIUS;
            double x, y, z;
            switch (planeAxis) {
                case 0 -> { x = a; y = b; z = 0; } // XY plane (rotates around Z)
                case 1 -> { x = a; y = 0; z = b; } // XZ plane (rotates around Y)
                default -> { x = 0; y = a; z = b; } // YZ plane (rotates around X)
            }
            w.spawnParticle(Particle.DUST, cx0 + x, cy0 + y, cz0 + z, 1, 0, 0, 0, 0, opts);
        }
    }

    // =========================================================================
    // Layer 2 — pulsing central core
    // =========================================================================

    private void renderCore(World w, Location c) {
        double pulse = 0.3 + java.lang.Math.sin(phase * 0.1) * 0.15;
        w.spawnParticle(Particle.END_ROD, c.getX(), c.getY(), c.getZ(), 3, pulse, pulse, pulse, 0);
        Particle.DustOptions cyan = new Particle.DustOptions(CORE_CYAN, 1.5f);
        w.spawnParticle(Particle.DUST, c.getX(), c.getY(), c.getZ(), 5, 0.4, 0.4, 0.4, 0, cyan);
    }

    // =========================================================================
    // Layer 3 — 5 tilted-orbit electrons with natural particle trails
    // =========================================================================

    private void renderElectrons(World w, Location c, UUID id) {
        double[] angles = electronAngles.computeIfAbsent(id, k -> {
            double[] a = new double[N_ELECTRONS];
            for (int i = 0; i < N_ELECTRONS; i++) a[i] = random.nextDouble() * 2.0 * java.lang.Math.PI;
            return a;
        });
        double[] tilts = electronTilts.computeIfAbsent(id, k -> {
            double[] t = new double[N_ELECTRONS];
            for (int i = 0; i < N_ELECTRONS; i++) t[i] = (java.lang.Math.PI / N_ELECTRONS) * i + random.nextDouble();
            return t;
        });

        for (int i = 0; i < N_ELECTRONS; i++) {
            angles[i] += 0.07;
            double theta = angles[i];
            double tilt  = tilts[i];
            double x = java.lang.Math.cos(theta) * ELECTRON_R * java.lang.Math.cos(tilt);
            double y = java.lang.Math.sin(theta) * ELECTRON_R;
            double z = java.lang.Math.cos(theta) * ELECTRON_R * java.lang.Math.sin(tilt);
            w.spawnParticle(Particle.END_ROD, c.getX() + x, c.getY() + y, c.getZ() + z, 1, 0, 0, 0, 0);
        }
    }
}
