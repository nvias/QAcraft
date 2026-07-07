package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Builds a decorated, self-guided tutorial hall next to the player.
 *
 * A single museum-style corridor split into four themed zones (Lobby, BB84,
 * Grover, E91). Each zone has coloured walls/floor, lighting, a big FIXED title,
 * FIXED wall info panels, floating tool showcases, and marked apparatus spots.
 *
 * All text displays are FIXED (they face a set direction and never track the
 * player). Built along +X from the player's position so it never touches terrain
 * behind them. Every spawned entity is tagged {@code Q_WORLD}; a Q_WORLD origin
 * marker lets {@code clear} air-fill the exact footprint.
 */
public class TutorialWorldBuilder {

    private final QAcraftPlugin plugin;

    public  static final int HALF_W  = 5;   // interior half-width in Z (11 wide)
    private static final int WALL_H  = 5;   // interior height
    public  static final int ZONE_L  = 16;  // length of each themed zone in X
    private static final int ZONES   = 4;
    private static final int TOTAL_L = ZONE_L * ZONES;

    // Display facings (front points along yaw): 0=south(+Z) 90=west(-X) 180=north(-Z)
    private static final float FACE_SOUTH = 0f;   // north-wall panels → face the walkway
    private static final float FACE_NORTH = 180f; // south-wall panels/showcases → face the walkway
    private static final float FACE_WEST  = 90f;  // titles → face the approaching player

    public TutorialWorldBuilder(QAcraftPlugin plugin) { this.plugin = plugin; }

    private record Theme(Material wall, Material accent, Material glass, NamedTextColor text) {}

    private static final Theme[] THEMES = {
        new Theme(Material.WHITE_CONCRETE,      Material.LIGHT_GRAY_CONCRETE, Material.WHITE_STAINED_GLASS,      NamedTextColor.WHITE),
        new Theme(Material.LIGHT_BLUE_CONCRETE, Material.BLUE_CONCRETE,       Material.LIGHT_BLUE_STAINED_GLASS, NamedTextColor.AQUA),
        new Theme(Material.PURPLE_CONCRETE,     Material.MAGENTA_CONCRETE,    Material.PURPLE_STAINED_GLASS,     NamedTextColor.LIGHT_PURPLE),
        new Theme(Material.LIME_CONCRETE,       Material.GREEN_CONCRETE,      Material.LIME_STAINED_GLASS,       NamedTextColor.GREEN),
    };

    // =========================================================================
    // Build
    // =========================================================================

    public void build(Player p) {
        World w = p.getWorld();
        int ox = p.getLocation().getBlockX();
        int oy = p.getLocation().getBlockY();
        int oz = p.getLocation().getBlockZ();
        int fy = oy - 1; // floor level

        Entity origin = w.spawnEntity(new Location(w, ox + 0.5, oy, oz + 0.5), EntityType.MARKER);
        origin.addScoreboardTag(Q_WORLD);

        for (int zi = 0; zi < ZONES; zi++) {
            Theme t = THEMES[zi];
            int zx0 = ox + zi * ZONE_L;
            int zx1 = zx0 + ZONE_L - 1;

            // Floor: accent border + quartz interior, lit central runway
            fill(w, zx0, fy, oz - HALF_W, zx1, fy, oz + HALF_W, Material.SMOOTH_QUARTZ);
            for (int x = zx0; x <= zx1; x++) {
                setBlock(w, x, fy, oz - HALF_W, t.accent());
                setBlock(w, x, fy, oz + HALF_W, t.accent());
                setBlock(w, x, fy - 1, oz, Material.GLOWSTONE);
                setBlock(w, x, fy, oz, t.glass());
            }

            // Solid side walls (no windows)
            for (int x = zx0; x <= zx1; x++) {
                for (int y = oy; y < oy + WALL_H; y++) {
                    setBlock(w, x, y, oz - HALF_W - 1, t.wall());
                    setBlock(w, x, y, oz + HALF_W + 1, t.wall());
                }
            }
            // Roof over the interior, lit down the centre line
            for (int x = zx0; x <= zx1; x++)
                for (int z = oz - HALF_W - 1; z <= oz + HALF_W + 1; z++)
                    setBlock(w, x, oy + WALL_H, z, (z == oz && x % 3 == 0) ? Material.SEA_LANTERN : t.accent());

            if (zi < ZONES - 1) buildDivider(w, zx1, oy, oz, t);
        }

        buildEndWall(w, ox - 1, oy, fy, oz, THEMES[0], true);                    // entrance (doorway)
        buildEndWall(w, ox + TOTAL_L, oy, fy, oz, THEMES[ZONES - 1], false);     // back wall

        decorateLobby(w, ox + 0 * ZONE_L, oy, oz);
        decorateBB84 (w, ox + 1 * ZONE_L, oy, oz);
        decorateGrover(w, ox + 2 * ZONE_L, oy, oz);
        decorateE91  (w, ox + 3 * ZONE_L, oy, oz);

        Location spawn = new Location(w, ox + 1.5, oy, oz + 0.5, -90f, 0f);
        p.teleport(spawn);
        p.sendMessage(Component.text("Tutorial hall built! ", NamedTextColor.GREEN)
            .append(Component.text("Walk east through the four rooms. Remove it with /qacraft world clear.",
                NamedTextColor.GRAY)));
        w.playSound(spawn, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
    }

    private void buildDivider(World w, int x, int oy, int oz, Theme t) {
        for (int z = oz - HALF_W - 1; z <= oz + HALF_W + 1; z++) {
            for (int y = oy; y <= oy + WALL_H; y++) {
                boolean doorway = (z >= oz - 1 && z <= oz + 1 && y <= oy + 2);
                if (doorway) continue;
                boolean frame = (z <= oz - HALF_W || z >= oz + HALF_W || y >= oy + WALL_H - 1);
                setBlock(w, x, y, z, frame ? t.accent() : t.wall());
            }
        }
    }

    private void buildEndWall(World w, int x, int oy, int fy, int oz, Theme t, boolean doorway) {
        for (int z = oz - HALF_W - 1; z <= oz + HALF_W + 1; z++) {
            for (int y = oy; y <= oy + WALL_H; y++) {
                if (doorway && z >= oz - 1 && z <= oz + 1 && y <= oy + 2) continue;
                setBlock(w, x, y, z, t.wall());
            }
            setBlock(w, x, fy, z, t.accent());
        }
    }

    // =========================================================================
    // Zone decoration
    // =========================================================================

    private void decorateLobby(World w, int zx, int oy, int oz) {
        int cx = zx + ZONE_L / 2;
        title(w, cx, oy + 3.7, oz + HALF_W, FACE_NORTH, "QAcraft — Quantum Academy", NamedTextColor.WHITE);
        panel(w, cx, oy + 2.1, oz - HALF_W - 0.4, FACE_SOUTH,
            "Welcome to the Quantum Academy",
            "Three protocols, three rooms:",
            "BB84 · Grover · E91");
        panel(w, cx + 4, oy + 2.1, oz + HALF_W + 0.4, FACE_NORTH,
            "Get your tools",
            "/qacraft tools bb84",
            "/qacraft tools grover",
            "/qacraft tools e91",
            "/qacraft tools all");
        Entity plasma = w.spawnEntity(new Location(w, cx + 0.5, oy + 2, oz + 0.5), EntityType.MARKER);
        plasma.addScoreboardTag(Q_WORLD);
        plasma.addScoreboardTag(Q_PLASMA);
        pedestal(w, cx, oy, oz, Material.SEA_LANTERN);
    }

    private void decorateBB84(World w, int zx, int oy, int oz) {
        int cx = zx + ZONE_L / 2;
        Theme t = THEMES[1];
        title(w, cx, oy + 3.7, oz + HALF_W, FACE_NORTH, "BB84 — Quantum Key Distribution", t.text());
        panel(w, zx + 4, oy + 2.1, oz - HALF_W - 0.4, FACE_SOUTH,
            "BB84 — how it works",
            "Alice sends photons in a random basis.",
            "Bob measures in a random basis.",
            "Matching bases = shared secret bit.");
        panel(w, zx + 11, oy + 2.1, oz - HALF_W - 0.4, FACE_SOUTH,
            "Try it",
            "1) Nether Star = Sender (yellow spot)",
            "2) Double chest on markers, /q sender fill",
            "3) Fill bit slots with red/blue glass",
            "4) Eye of Ender = Gate (purple spot) + chest",
            "5) Fill Gate chest with Compasses",
            "6) Quantum Cable behind the Gate",
            "7) /q sender start");
        panel(w, cx, oy + 2.1, oz + HALF_W + 0.4, FACE_NORTH,
            "Filters",
            "Compass = Rectilinear (+)",
            "Recovery Compass = Diagonal (x)");
        showcase(w, zx + 4, oy, oz + HALF_W - 1, FACE_NORTH, ToolManager.rectilinearFilter());
        showcase(w, zx + 6, oy, oz + HALF_W - 1, FACE_NORTH, ToolManager.diagonalFilter());
        showcase(w, zx + 8, oy, oz + HALF_W - 1, FACE_NORTH, ToolManager.makeItem(Material.NETHER_STAR, "Quantum Sender", NamedTextColor.YELLOW));
        showcase(w, zx + 10, oy, oz + HALF_W - 1, FACE_NORTH, ToolManager.makeItem(Material.ENDER_EYE, "Quantum Gate", NamedTextColor.GREEN));
        toolButton(w, zx + 1, oy, oz + HALF_W - 1, 1, "BB84");
        // Sender=yellow, gate=purple; 2-block gap between gate and cable
        spot(w, zx + 4, oy, oz, FACE_WEST, Material.YELLOW_CONCRETE, "Sender here");
        spot(w, zx + 8, oy, oz, FACE_WEST, Material.PURPLE_CONCRETE, "Gate here");
        // Photons need somewhere to travel to and pass through the gate → a cable behind it
        spot(w, zx + 11, oy, oz, FACE_WEST, t.accent(), "Quantum Cable here");
    }

    private void decorateGrover(World w, int zx, int oy, int oz) {
        int cx = zx + ZONE_L / 2;
        Theme t = THEMES[2];
        title(w, cx, oy + 3.7, oz + HALF_W, FACE_NORTH, "Grover's Search", t.text());
        panel(w, zx + 4, oy + 2.1, oz - HALF_W - 0.4, FACE_SOUTH,
            "Grover — how it works",
            "Finds a marked item in ~sqrt(N) steps.",
            "With 8 chests, ~2 iterations",
            "reach about 97.7%.");
        panel(w, zx + 12, oy + 2.1, oz - HALF_W - 0.4, FACE_SOUTH,
            "Try it",
            "1) Fire Charge = new grid (or /q grover setup)",
            "2) Conduit = place chests, then /q grover fillwool",
            "3) Spyglass + target wool in offhand",
            "4) Throw Wind Charge to iterate & observe");
        panel(w, cx, oy + 2.1, oz + HALF_W + 0.4, FACE_NORTH,
            "Multiple searches",
            "Each grid has its own id (shown in the",
            "centre). Wind Charge iterates the nearest.",
            "TNT on the centre block deactivates a grid.");
        showcase(w, zx + 3,  oy, oz + HALF_W - 1, FACE_NORTH, ToolManager.makeItem(Material.FIRE_CHARGE, "Grover Setup",    NamedTextColor.GOLD));
        showcase(w, zx + 5,  oy, oz + HALF_W - 1, FACE_NORTH, ToolManager.makeItem(Material.CONDUIT,     "Place Chests",    ToolManager.BEIGE));
        showcase(w, zx + 7,  oy, oz + HALF_W - 1, FACE_NORTH, ToolManager.makeItem(Material.WIND_CHARGE, "Grover Operator", NamedTextColor.WHITE));
        showcase(w, zx + 9,  oy, oz + HALF_W - 1, FACE_NORTH, ToolManager.makeItem(Material.SPYGLASS,    "Quantum Observer",NamedTextColor.YELLOW));
        showcase(w, zx + 11, oy, oz + HALF_W - 1, FACE_NORTH, ToolManager.makeItem(Material.TNT,         "Grover Reset",    NamedTextColor.RED));
        toolButton(w, zx + 1, oy, oz + HALF_W - 1, 2, "Grover");
        // Plain smooth-quartz floor around the centre so the chest spots stand out
        // (overwrite the glass runway strip that runs through this zone)
        for (int x = zx; x < zx + ZONE_L; x++) setBlock(w, x, oy - 1, oz, Material.SMOOTH_QUARTZ);
        // 8 chest spots: stained glass with glowstone beneath (matches the lit floor elsewhere)
        int[][] ring = {{-2,-2},{0,-2},{2,-2},{2,0},{2,2},{0,2},{-2,2},{-2,0}};
        for (int[] r : ring) {
            setBlock(w, cx + r[0], oy - 2, oz + r[1], Material.GLOWSTONE);
            setBlock(w, cx + r[0], oy - 1, oz + r[1], t.glass());
        }
        spot(w, cx, oy, oz, FACE_WEST, t.accent(), "Stand here: /qacraft grover setup 1");
    }

    private void decorateE91(World w, int zx, int oy, int oz) {
        int cx = zx + ZONE_L / 2;
        Theme t = THEMES[3];
        title(w, cx, oy + 3.7, oz + HALF_W, FACE_NORTH, "E91 — Entanglement", t.text());
        panel(w, zx + 4, oy + 2.1, oz - HALF_W - 0.4, FACE_SOUTH,
            "E91 — how it works",
            "An EPR source emits entangled pairs.",
            "One photon to Alice, one to Bob.",
            "Measuring one correlates the other.");
        panel(w, zx + 12, oy + 2.1, oz - HALF_W - 0.4, FACE_SOUTH,
            "Try it",
            "1) Amethyst Shard = EPR Source",
            "2) Diamond Axe = Alice pad, Gold Axe = Bob pad",
            "3) /q e91 generate, then measure a photon",
            "4) /q e91 key  &  /q e91 bell");
        panel(w, cx, oy + 2.1, oz + HALF_W + 0.4, FACE_NORTH,
            "Measurement bases",
            "Compass + / Recovery Compass x / Clock o",
            "Bell test detects an eavesdropper.");
        showcase(w, zx + 4, oy, oz + HALF_W - 1, FACE_NORTH, ToolManager.makeItem(Material.AMETHYST_SHARD, "EPR Source", NamedTextColor.LIGHT_PURPLE));
        showcase(w, zx + 6, oy, oz + HALF_W - 1, FACE_NORTH, ToolManager.circularFilter());
        showcase(w, zx + 8, oy, oz + HALF_W - 1, FACE_NORTH, ToolManager.makeItem(Material.DIAMOND_AXE, "Alice Landing Pad", NamedTextColor.AQUA));
        showcase(w, zx + 10, oy, oz + HALF_W - 1, FACE_NORTH, ToolManager.makeItem(Material.GOLDEN_AXE, "Bob Landing Pad", NamedTextColor.GOLD));
        toolButton(w, zx + 1, oy, oz + HALF_W - 1, 3, "E91");
        // Purple source spot; Alice & Bob pads on the SAME side, with billboard labels
        setBlock(w, cx, oy - 1, oz, Material.PURPLE_CONCRETE);
        setBlock(w, cx - 4, oy - 1, oz - 3, Material.CYAN_CONCRETE);
        setBlock(w, cx + 4, oy - 1, oz - 3, Material.ORANGE_CONCRETE);
        billboardLabel(w, cx - 4 + 0.5, oy + 0.9, oz - 3 + 0.5, "Alice", NamedTextColor.AQUA);
        billboardLabel(w, cx + 4 + 0.5, oy + 0.9, oz - 3 + 0.5, "Bob", NamedTextColor.GOLD);
    }

    // =========================================================================
    // Display + block helpers
    // =========================================================================

    private void title(World w, double x, double y, double z, float yaw, String text, NamedTextColor color) {
        TextDisplay td = (TextDisplay) w.spawnEntity(new Location(w, x + 0.5, y, z + 0.5, yaw, 0f), EntityType.TEXT_DISPLAY);
        td.text(Component.text(text, color).decoration(TextDecoration.BOLD, true));
        td.setBillboard(Display.Billboard.FIXED);
        td.setRotation(yaw, 0f);
        td.setShadowed(true);
        td.setBrightness(new Display.Brightness(15, 15));
        td.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2.2f), new Quaternionf()));
        td.addScoreboardTag(Q_WORLD);
    }

    /**
     * A "give tools" station: end rod (facing down) + stone button on top + a white
     * "Tools / <Room>" label. Pressing the button hands out that room's tools.
     * set: 1 = BB84, 2 = Grover, 3 = E91.
     */
    private void toolButton(World w, int x, int oy, int z, int set, String roomName) {
        Block rod = w.getBlockAt(x, oy, z);
        rod.setType(Material.END_ROD, false);
        try { Directional d = (Directional) rod.getBlockData(); d.setFacing(BlockFace.DOWN); rod.setBlockData(d, false); } catch (Exception ignored) {}

        Block btn = w.getBlockAt(x, oy + 1, z);
        btn.setType(Material.STONE_BUTTON, false);
        try { Switch sw = (Switch) btn.getBlockData(); sw.setAttachedFace(FaceAttachable.AttachedFace.FLOOR); btn.setBlockData(sw, false); } catch (Exception ignored) {}

        Entity marker = w.spawnEntity(new Location(w, x + 0.5, oy + 1, z + 0.5), EntityType.MARKER);
        marker.addScoreboardTag(Q_TOOLBTN);
        marker.addScoreboardTag(Q_WORLD);
        setNum(marker, set);

        Component txt = Component.text("Tools", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true)
            .append(Component.text("\n" + roomName, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true));
        TextDisplay td = (TextDisplay) w.spawnEntity(new Location(w, x + 0.5, oy + 1.9, z + 0.5, FACE_NORTH, 0f), EntityType.TEXT_DISPLAY);
        td.text(txt);
        td.setBillboard(Display.Billboard.FIXED);
        td.setRotation(FACE_NORTH, 0f);
        td.setShadowed(true);
        td.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.55f), new Quaternionf()));
        td.addScoreboardTag(Q_TOOLBTN);
        td.addScoreboardTag(Q_WORLD);
        setNum(td, set);
    }

    /** A player-facing (billboard) floating label, e.g. Alice / Bob pad markers. */
    private void billboardLabel(World w, double x, double y, double z, String text, NamedTextColor color) {
        TextDisplay td = (TextDisplay) w.spawnEntity(new Location(w, x, y, z), EntityType.TEXT_DISPLAY);
        td.text(Component.text(text, color));
        td.setBillboard(Display.Billboard.CENTER);
        td.setShadowed(true);
        td.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.9f), new Quaternionf()));
        td.addScoreboardTag(Q_WORLD);
    }

    private void panel(World w, double x, double y, double z, float yaw, String header, String... lines) {
        Component c = Component.text(header, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true);
        for (String line : lines) c = c.append(Component.text("\n" + line, NamedTextColor.GRAY));
        TextDisplay td = (TextDisplay) w.spawnEntity(new Location(w, x + 0.5, y, z + 0.5, yaw, 0f), EntityType.TEXT_DISPLAY);
        td.text(c);
        td.setBillboard(Display.Billboard.FIXED);
        td.setRotation(yaw, 0f);
        td.setShadowed(true);
        td.setBrightness(new Display.Brightness(15, 15));
        td.setBackgroundColor(Color.fromARGB(140, 10, 10, 20));
        td.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.8f), new Quaternionf()));
        td.addScoreboardTag(Q_WORLD);
    }

    /** A floating item showcase: quartz-pillar plinth + a name plaque on its face + the lit item above. */
    private void showcase(World w, int bx, int oy, int bz, float yaw, ItemStack item) {
        setBlock(w, bx, oy, bz, Material.QUARTZ_PILLAR); // default axis = Y

        // Light block on top of the pillar so the item display is lit
        Block lb = w.getBlockAt(bx, oy + 1, bz);
        lb.setType(Material.LIGHT, false);
        try {
            Levelled lv = (Levelled) lb.getBlockData();
            lv.setLevel(15);
            lb.setBlockData(lv, false);
        } catch (Exception ignored) {}

        ItemDisplay id = (ItemDisplay) w.spawnEntity(new Location(w, bx + 0.5, oy + 1.35, bz + 0.5, yaw, 0f), EntityType.ITEM_DISPLAY);
        id.setItemStack(item);
        id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        id.setBillboard(Display.Billboard.FIXED);
        id.setRotation(yaw, 0f);
        id.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6f), new Quaternionf()));
        id.addScoreboardTag(Q_WORLD);

        // Name plaque stuck to the front face of the pillar (offset toward the viewer)
        Component nameComp = (item.hasItemMeta() && item.getItemMeta().hasItemName())
            ? item.getItemMeta().itemName() : Component.text(item.getType().name());
        String name = PlainTextComponentSerializer.plainText().serialize(nameComp);
        TextColor col = nameComp.color() != null ? nameComp.color() : NamedTextColor.WHITE;
        Component plaque = Component.empty();
        String[] words = name.split(" ");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) plaque = plaque.append(Component.text("\n"));
            plaque = plaque.append(Component.text(words[i], col).decoration(TextDecoration.BOLD, true));
        }
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad) * 0.75; // clearly in front of the pillar face (toward the viewer)
        double fz =  Math.cos(rad) * 0.75;
        TextDisplay plate = (TextDisplay) w.spawnEntity(
            new Location(w, bx + 0.5 + fx, oy + 0.6, bz + 0.5 + fz, yaw, 0f), EntityType.TEXT_DISPLAY);
        plate.text(plaque);
        plate.setBillboard(Display.Billboard.FIXED);
        plate.setRotation(yaw, 0f);
        plate.setShadowed(true);
        plate.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.55f), new Quaternionf()));
        plate.addScoreboardTag(Q_WORLD);
    }

    private void pedestal(World w, int x, int y, int z, Material top) {
        setBlock(w, x, y, z, Material.SMOOTH_QUARTZ);
        setBlock(w, x, y + 1, z, top);
    }

    /**
     * A marked floor block. The floating guidance label ("Sender here", …) is
     * NOT created here — the interactive walkthrough spawns and manages those so
     * they reappear each time the tutorial is (re)started.
     */
    private void spot(World w, int x, int y, int z, float yaw, Material floor, String label) {
        if (floor != Material.AIR) setBlock(w, x, y - 1, z, floor);
    }

    private void setBlock(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private void fill(World w, int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        int xa = Math.min(x1, x2), xb = Math.max(x1, x2);
        int ya = Math.min(y1, y2), yb = Math.max(y1, y2);
        int za = Math.min(z1, z2), zb = Math.max(z1, z2);
        for (int x = xa; x <= xb; x++)
            for (int y = ya; y <= yb; y++)
                for (int z = za; z <= zb; z++)
                    w.getBlockAt(x, y, z).setType(m, false);
    }

    // =========================================================================
    // Clear — air-fill the footprint and remove Q_WORLD entities
    // =========================================================================

    public void clear(Player p) {
        World w = p.getWorld();
        Entity origin = null;
        for (Entity e : tagged(w, Q_WORLD)) {
            if (e.getType() == EntityType.MARKER) { origin = e; break; }
        }
        if (origin == null) {
            for (World world : plugin.getServer().getWorlds()) killAll(world, Q_WORLD);
            p.sendMessage(Component.text("Removed tutorial-hall entities (no origin marker to air-fill blocks).",
                NamedTextColor.GRAY));
            return;
        }
        int ox = origin.getLocation().getBlockX();
        int oy = origin.getLocation().getBlockY();
        int oz = origin.getLocation().getBlockZ();
        // Remove the whole structure (floor up), then restore the ground so no pit is left
        fill(w, ox - 1, oy, oz - HALF_W - 2, ox + TOTAL_L, oy + WALL_H + 1, oz + HALF_W + 2, Material.AIR);
        fill(w, ox - 1, oy - 1, oz - HALF_W - 2, ox + TOTAL_L, oy - 1, oz + HALF_W + 2, Material.GRASS_BLOCK);
        fill(w, ox - 1, oy - 2, oz - HALF_W - 2, ox + TOTAL_L, oy - 2, oz + HALF_W + 2, Material.DIRT);
        for (World world : plugin.getServer().getWorlds()) killAll(world, Q_WORLD);
        p.sendMessage(Component.text("Tutorial hall removed.", NamedTextColor.GRAY));
    }
}
