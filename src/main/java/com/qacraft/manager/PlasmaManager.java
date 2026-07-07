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
 * Summoned / removed with the Plasma tools ({@code /qacraft plasma} hands them
 * out) — NOT by command. Each summon spawns an invisible MARKER tagged
 * {@code q_plasma} that persists with chunks across restarts.
 *
 * Rendering (per marker, only when a player is within range):
 *   1. Three coloured rings (blue/purple/green) of DIFFERENT sizes, each on a
 *      fixed distinct tilt, each precessing about the vertical axis at its own
 *      speed. Dense points + a single simple sweep keep each ring recognisable
 *      with only a short motion trail.
 *   2. A pulsing central core.
 *   3. A cloud of fast white "electrons" wandering in a shell hugging the rings,
 *      each leaving a short END_ROD trail.
 */
public class PlasmaManager {

    private final QAcraftPlugin plugin;
    private final Random random = new Random();
    private int phase = 0;

    private static final org.bukkit.Color BB84_BLUE     = org.bukkit.Color.fromRGB(0x2E, 0x74, 0xB5);
    private static final org.bukkit.Color GROVER_PURPLE = org.bukkit.Color.fromRGB(0x7B, 0x2D, 0x8B);
    private static final org.bukkit.Color E91_GREEN     = org.bukkit.Color.fromRGB(0x3C, 0xB3, 0x71);
    private static final org.bukkit.Color CORE_CYAN     = org.bukkit.Color.fromRGB(0x66, 0xFF, 0xFF);

    // Three rings: distinct radii (no overlap), distinct tilts (separable planes),
    // distinct precession speeds. Dense points fill each ring cleanly.
    private static final org.bukkit.Color[] RING_COLORS = { BB84_BLUE, GROVER_PURPLE, E91_GREEN };
    private static final double[] RING_RADII  = { 1.8, 2.5, 3.2 };
    private static final double[] RING_SPEED  = { 0.030, 0.020, 0.013 }; // precession rad/tick
    private static final double[] RING_TILT_X = { 0.40, 1.30, 0.90 };
    private static final double[] RING_TILT_Z = { 0.00, 0.60, 1.50 };
    private static final double   POINT_DENSITY = 20.0; // points per block of radius

    private static final int    N_ELECTRONS = 4;
    private static final double ELECTRON_R  = 2.8;
    private static final double RENDER_RANGE = 60.0;

    /** Per-plasma electrons: N × [x, y, z, vx, vy, vz]. */
    private final Map<UUID, double[]> electronState = new HashMap<>();

    public PlasmaManager(QAcraftPlugin plugin) { this.plugin = plugin; }

    // =========================================================================
    // Item-driven API (handlers live in ToolManager)
    // =========================================================================

    public void summon(Player p) {
        World w = p.getWorld();
        // A few blocks ahead at eye height so the atom appears in view, not on the player
        Location loc = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(4));
        Entity anchor = w.spawnEntity(loc, EntityType.MARKER);
        anchor.addScoreboardTag(Q_PLASMA);
        p.sendMessage(Component.text("Plasma summoned", NamedTextColor.AQUA)
            .append(Component.text(" at " + (int) loc.getX() + ", " + (int) loc.getY() + ", " + (int) loc.getZ(),
                NamedTextColor.GRAY)));
        w.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);
        w.spawnParticle(Particle.REVERSE_PORTAL, loc, 30, 0.5, 0.5, 0.5, 0.1);
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
        electronState.remove(nearest.getUniqueId());
        nearest.remove();
        w.spawnParticle(Particle.REVERSE_PORTAL, loc, 25, 0.5, 0.5, 0.5, 0.1);
        w.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.5f);
        p.sendMessage(Component.text("Plasma removed.", NamedTextColor.GRAY));
    }

    public void clearAll() {
        electronState.clear();
        for (World w : plugin.getServer().getWorlds()) killAll(w, Q_PLASMA);
    }

    // =========================================================================
    // Tick
    // =========================================================================

    public void tick() {
        phase++;
        for (World w : plugin.getServer().getWorlds()) {
            for (Entity anchor : tagged(w, Q_PLASMA)) {
                Location centre = anchor.getLocation();
                if (w.getNearbyPlayers(centre, RENDER_RANGE).isEmpty()) continue;
                renderRings(w, centre);
                renderCore(w, centre);
                renderElectrons(w, centre, anchor.getUniqueId());
            }
        }
    }

    // =========================================================================
    // Layer 1 — three differently-sized rings, each precessing at its own speed
    // =========================================================================

    private void renderRings(World w, Location c) {
        for (int i = 0; i < 3; i++) {
            double precess = phase * RING_SPEED[i];
            drawRing(w, c, RING_COLORS[i], RING_RADII[i], RING_TILT_X[i], RING_TILT_Z[i], precess);
        }
    }

    private void drawRing(World w, Location c, org.bukkit.Color color, double radius,
                          double tiltX, double tiltZ, double precess) {
        int points = (int) Math.round(radius * POINT_DENSITY);
        double cX = Math.cos(tiltX), sX = Math.sin(tiltX);
        double cZ = Math.cos(tiltZ), sZ = Math.sin(tiltZ);
        double cP = Math.cos(precess), sP = Math.sin(precess);
        Particle.DustOptions opts = new Particle.DustOptions(color, 1.0f);
        double cx = c.getX(), cy = c.getY(), cz = c.getZ();

        for (int j = 0; j < points; j++) {
            double theta = (2.0 * Math.PI * j) / points;
            double lx = Math.cos(theta) * radius, ly = Math.sin(theta) * radius, lz = 0;
            // fixed tilt about X, then about Z → gives this ring its own plane
            double y1 = ly * cX - lz * sX, z1 = ly * sX + lz * cX, x1 = lx;
            double x2 = x1 * cZ - y1 * sZ, y2 = x1 * sZ + y1 * cZ, z2 = z1;
            // precession about the vertical (Y) axis → visible spin, short sweep
            double x3 = x2 * cP + z2 * sP, z3 = -x2 * sP + z2 * cP, y3 = y2;
            w.spawnParticle(Particle.DUST, cx + x3, cy + y3, cz + z3, 1, 0, 0, 0, 0, opts);
        }
    }

    // =========================================================================
    // Layer 2 — pulsing central core
    // =========================================================================

    private void renderCore(World w, Location c) {
        double pulse = 0.3 + Math.sin(phase * 0.1) * 0.15;
        w.spawnParticle(Particle.END_ROD, c.getX(), c.getY(), c.getZ(), 2, pulse, pulse, pulse, 0);
        Particle.DustOptions cyan = new Particle.DustOptions(CORE_CYAN, 1.5f);
        w.spawnParticle(Particle.DUST, c.getX(), c.getY(), c.getZ(), 3, 0.35, 0.35, 0.35, 0, cyan);
    }

    // =========================================================================
    // Layer 3 — fast, randomly wandering electrons in a shell near the rings
    // =========================================================================

    private void renderElectrons(World w, Location c, UUID id) {
        double[] es = electronState.computeIfAbsent(id, k -> {
            double[] a = new double[N_ELECTRONS * 6];
            for (int n = 0; n < N_ELECTRONS; n++) {
                int b = n * 6;
                double u = random.nextDouble() * 2 - 1;          // cos(phi)
                double t = random.nextDouble() * Math.PI * 2;    // azimuth
                double s = Math.sqrt(1 - u * u);
                a[b]     = Math.cos(t) * s * ELECTRON_R;
                a[b + 1] = u * ELECTRON_R;
                a[b + 2] = Math.sin(t) * s * ELECTRON_R;
                a[b + 3] = (random.nextDouble() - 0.5) * 0.25;
                a[b + 4] = (random.nextDouble() - 0.5) * 0.25;
                a[b + 5] = (random.nextDouble() - 0.5) * 0.25;
            }
            return a;
        });

        for (int n = 0; n < N_ELECTRONS; n++) {
            int b = n * 6;
            // stronger random acceleration + lighter damping → noticeably faster drift
            for (int k = 3; k <= 5; k++) {
                es[b + k] = es[b + k] * 0.82 + (random.nextDouble() - 0.5) * 0.09;
                if (es[b + k] >  0.28) es[b + k] =  0.28;
                if (es[b + k] < -0.28) es[b + k] = -0.28;
            }
            es[b]     += es[b + 3];
            es[b + 1] += es[b + 4];
            es[b + 2] += es[b + 5];

            // soft spring back to the shell radius so they keep hugging the rings
            double r = Math.sqrt(es[b] * es[b] + es[b + 1] * es[b + 1] + es[b + 2] * es[b + 2]);
            if (r < 1e-3) r = 1e-3;
            double scale = 1 + 0.06 * (ELECTRON_R - r) / r;
            es[b]     *= scale;
            es[b + 1] *= scale;
            es[b + 2] *= scale;

            w.spawnParticle(Particle.END_ROD, c.getX() + es[b], c.getY() + es[b + 1], c.getZ() + es[b + 2],
                1, 0, 0, 0, 0);
        }
    }
}
