package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class ToolManager implements Listener {
    private final QAcraftPlugin plugin;

    public ToolManager(QAcraftPlugin plugin) { this.plugin = plugin; }

    // =========================================================================
    // Tool sets by protocol
    // =========================================================================

    /** BB84 quantum-key distribution tools. */
    public void giveToolsBB84(Player p) {
        p.getInventory().clear();
        p.getInventory().addItem(
            rectilinearFilter(),
            diagonalFilter(),
            makeItem(Material.SNOWBALL,                "Quantum Source",     NamedTextColor.WHITE),
            makeItem(Material.CARROT_ON_A_STICK,       "Quantum Cable",      NamedTextColor.GOLD),
            makeItem(Material.WARPED_FUNGUS_ON_A_STICK,"Parking Spot",       NamedTextColor.GREEN),
            makeItem(Material.NETHER_STAR,             "Quantum Sender",     NamedTextColor.YELLOW),
            makeItem(Material.ENDER_EYE,               "Quantum Gate",       NamedTextColor.GREEN),
            new ItemStack(Material.CHEST, 8)
        );
        p.getInventory().setItem(8, makeItem(Material.BRUSH, "Quantum Eraser", NamedTextColor.GRAY));
        // Bit-value glass (row 1) + spare filters (row 2) in the main inventory, not the hotbar
        p.getInventory().setItem(9,  new ItemStack(Material.BLUE_STAINED_GLASS, 64));
        p.getInventory().setItem(10, new ItemStack(Material.RED_STAINED_GLASS, 64));
        ItemStack rf = rectilinearFilter(); rf.setAmount(64);
        ItemStack df = diagonalFilter();    df.setAmount(64);
        p.getInventory().setItem(18, rf);
        p.getInventory().setItem(19, df);
        p.sendMessage(Component.text("BB84 tools received!", NamedTextColor.GREEN));
    }

    /** Grover's search algorithm tools. */
    public void giveToolsGrover(Player p) {
        p.getInventory().clear();
        p.getInventory().addItem(
            makeItem(Material.FIRE_CHARGE, "Grover Setup",      NamedTextColor.GOLD),
            makeItem(Material.CONDUIT,     "Place Chests",      BEIGE),
            makeItem(Material.WIND_CHARGE, "Grover Operator",   NamedTextColor.WHITE),
            makeItem(Material.SPYGLASS,    "Quantum Observer",  NamedTextColor.YELLOW),
            makeItemLore(Material.TNT,     "Grover Reset",      NamedTextColor.RED,
                "Place on the centre block", "between the chests to", "deactivate that grid")
        );
        p.getInventory().setItem(8, makeItem(Material.BRUSH, "Quantum Eraser", NamedTextColor.GRAY));
        p.sendMessage(Component.text("Grover tools received!", NamedTextColor.GREEN));
    }

    /** E91 entanglement-protocol tools. */
    public void giveToolsE91(Player p) {
        p.getInventory().clear();
        p.getInventory().addItem(
            makeItem(Material.AMETHYST_SHARD,   "EPR Source",          NamedTextColor.LIGHT_PURPLE),
            rectilinearFilter(),
            diagonalFilter(),
            circularFilter(),
            makeItem(Material.DIAMOND_AXE,      "Alice Landing Pad",   NamedTextColor.AQUA),
            makeItem(Material.GOLDEN_AXE,       "Bob Landing Pad",     NamedTextColor.GOLD)
        );
        p.getInventory().setItem(8, makeItem(Material.BRUSH, "Quantum Eraser", NamedTextColor.GRAY));
        p.sendMessage(Component.text("E91 entanglement tools received!", NamedTextColor.GREEN));
    }

    /** All tools at once (no duplicates). */
    public void giveToolsAll(Player p) {
        p.getInventory().clear();
        p.getInventory().addItem(
            makeItem(Material.AMETHYST_SHARD,          "EPR Source",          NamedTextColor.LIGHT_PURPLE),
            rectilinearFilter(),
            diagonalFilter(),
            circularFilter(),
            makeItem(Material.SNOWBALL,                "Quantum Source",      NamedTextColor.WHITE),
            makeItem(Material.CARROT_ON_A_STICK,       "Quantum Cable",       NamedTextColor.GOLD),
            makeItem(Material.WARPED_FUNGUS_ON_A_STICK,"Parking Spot",        NamedTextColor.GREEN),
            makeItem(Material.NETHER_STAR,             "Quantum Sender",      NamedTextColor.YELLOW),
            makeItem(Material.ENDER_EYE,               "Quantum Gate",        NamedTextColor.GREEN),
            makeItem(Material.WIND_CHARGE,             "Grover Operator",     NamedTextColor.WHITE),
            makeItem(Material.SPYGLASS,                "Quantum Observer",    NamedTextColor.YELLOW),
            makeItem(Material.DIAMOND_AXE,             "Alice Landing Pad",   NamedTextColor.AQUA),
            makeItem(Material.GOLDEN_AXE,              "Bob Landing Pad",     NamedTextColor.GOLD)
        );
        p.getInventory().setItem(8, makeItem(Material.BRUSH, "Quantum Eraser", NamedTextColor.GRAY));
        p.sendMessage(Component.text("All quantum tools received!", NamedTextColor.GREEN));
    }

    /** Lobby plasma tools: right-click to summon / remove the particle atom. */
    public void giveToolsPlasma(Player p) {
        p.getInventory().clear();
        p.getInventory().addItem(
            makeItem(Material.HEART_OF_THE_SEA, "Plasma Summoner", NamedTextColor.AQUA),
            makeItem(Material.ECHO_SHARD,       "Plasma Remover",  NamedTextColor.DARK_GRAY)
        );
        p.sendMessage(Component.text("Plasma tools received — right-click to summon / remove.", NamedTextColor.AQUA));
    }

    // =========================================================================
    // Tool helpers
    // =========================================================================

    public static final TextColor BEIGE = TextColor.color(0xE8, 0xD8, 0xB0);

    static ItemStack makeItem(Material mat, String name, TextColor color) {
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack makeItemLore(Material mat, String name, TextColor color, String... loreLines) {
        ItemStack item = makeItem(mat, name, color);
        ItemMeta meta = item.getItemMeta();
        java.util.List<Component> lore = new java.util.ArrayList<>();
        for (String s : loreLines) lore.add(Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // Canonical measurement-filter items — colours mirror the physical compasses.
    // Reused by the tool loadout AND by /qacraft sender fill so chests get the
    // properly named tool, not a raw compass.
    public static ItemStack rectilinearFilter() { return makeItem(Material.COMPASS,          "Rectilinear Filter", NamedTextColor.RED); }
    public static ItemStack diagonalFilter()    { return makeItem(Material.RECOVERY_COMPASS, "Diagonal Filter",    NamedTextColor.AQUA); }
    public static ItemStack circularFilter()    { return makeItem(Material.CLOCK,            "Circular Filter",    NamedTextColor.YELLOW); }

    public static boolean isQuantumTool(ItemStack item, Material mat) {
        return item != null && item.getType() == mat && item.hasItemMeta();
    }

    // =========================================================================
    // /q item <name> — hand out a single named tool without clearing the inventory
    // =========================================================================

    public static final String[] ITEM_NAMES = {
        "rectilinear","diagonal","circular","source","cable","parking","sender","gate",
        "setup","placechests","operator","observer","reset","epr","alice","bob","eraser",
        "plasma-summon","plasma-remove","red-glass","blue-glass"
    };

    /** Add a single named tool to the player's inventory (no clearing). Returns false for unknown names. */
    public boolean giveSingle(Player p, String name) {
        ItemStack item = itemByName(name);
        if (item == null) return false;
        p.getInventory().addItem(item);
        return true;
    }

    static ItemStack itemByName(String name) {
        return switch (name.toLowerCase()) {
            case "rectilinear", "compass"          -> rectilinearFilter();
            case "diagonal", "recovery"            -> diagonalFilter();
            case "circular", "clock"               -> circularFilter();
            case "source", "snowball"              -> makeItem(Material.SNOWBALL, "Quantum Source", NamedTextColor.WHITE);
            case "cable", "carrot"                 -> makeItem(Material.CARROT_ON_A_STICK, "Quantum Cable", NamedTextColor.GOLD);
            case "parking", "fungus"               -> makeItem(Material.WARPED_FUNGUS_ON_A_STICK, "Parking Spot", NamedTextColor.GREEN);
            case "sender", "netherstar"            -> makeItem(Material.NETHER_STAR, "Quantum Sender", NamedTextColor.YELLOW);
            case "gate", "endereye"                -> makeItem(Material.ENDER_EYE, "Quantum Gate", NamedTextColor.GREEN);
            case "setup", "firecharge"             -> makeItem(Material.FIRE_CHARGE, "Grover Setup", NamedTextColor.GOLD);
            case "placechests", "conduit"          -> makeItem(Material.CONDUIT, "Place Chests", BEIGE);
            case "operator", "windcharge"          -> makeItem(Material.WIND_CHARGE, "Grover Operator", NamedTextColor.WHITE);
            case "observer", "spyglass"            -> makeItem(Material.SPYGLASS, "Quantum Observer", NamedTextColor.YELLOW);
            case "reset", "tnt"                    -> makeItemLore(Material.TNT, "Grover Reset", NamedTextColor.RED,
                                                          "Place on the centre block", "between the chests to", "deactivate that grid");
            case "epr", "amethyst"                 -> makeItem(Material.AMETHYST_SHARD, "EPR Source", NamedTextColor.LIGHT_PURPLE);
            case "alice", "diamondaxe"             -> makeItem(Material.DIAMOND_AXE, "Alice Landing Pad", NamedTextColor.AQUA);
            case "bob", "goldaxe"                  -> makeItem(Material.GOLDEN_AXE, "Bob Landing Pad", NamedTextColor.GOLD);
            case "eraser", "brush"                 -> makeItem(Material.BRUSH, "Quantum Eraser", NamedTextColor.GRAY);
            case "plasma-summon", "heart"          -> makeItem(Material.HEART_OF_THE_SEA, "Plasma Summoner", NamedTextColor.AQUA);
            case "plasma-remove", "echo"           -> makeItem(Material.ECHO_SHARD, "Plasma Remover", NamedTextColor.DARK_GRAY);
            case "red-glass"                       -> new ItemStack(Material.RED_STAINED_GLASS, 64);
            case "blue-glass"                      -> new ItemStack(Material.BLUE_STAINED_GLASS, 64);
            default -> null;
        };
    }

    // =========================================================================
    // Event handlers
    // =========================================================================

    @EventHandler
    public void onSnowball(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isQuantumTool(e.getItem(), Material.SNOWBALL)) return;

        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            p.getWorld().getEntitiesByClass(org.bukkit.entity.Snowball.class).stream()
                .filter(s -> s.getShooter() == p && s.getLocation().distance(p.getLocation()) < 8)
                .forEach(org.bukkit.entity.Entity::remove);
            plugin.getPhotonManager().spawnSuperposition(p.getLocation().add(0, 0.5, 0), p);
        }, 1L);
    }

    @EventHandler
    public void onCarrotClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isQuantumTool(e.getItem(), Material.CARROT_ON_A_STICK)) return;
        plugin.getWaypointManager().placeWaypoint(e.getPlayer());
    }

    @EventHandler
    public void onFungusClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isQuantumTool(e.getItem(), Material.WARPED_FUNGUS_ON_A_STICK)) return;
        plugin.getWaypointManager().placeParking(e.getPlayer());
    }

    /** Amethyst Shard → place E91 entangled pair source. */
    @EventHandler
    public void onAmethystShard(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isQuantumTool(e.getItem(), Material.AMETHYST_SHARD)) return;
        e.setCancelled(true);
        plugin.getE91Manager().placeSource(e.getPlayer());
    }

    /**
     * Diamond Axe — dual function:
     *   Right-click → place Alice landing pad  (can place multiple)
     *   Left-click  → set Alice zone marker    (single, replaces old one)
     */
    @EventHandler
    public void onDiamondAxe(PlayerInteractEvent e) {
        if (!isQuantumTool(e.getItem(), Material.DIAMOND_AXE)) return;
        e.setCancelled(true);
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            plugin.getE91Manager().placeAliceParking(e.getPlayer());
        } else if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            plugin.getE91Manager().setAliceEnd(e.getPlayer());
        }
    }

    /**
     * Gold Axe — dual function:
     *   Right-click → place Bob landing pad    (can place multiple)
     *   Left-click  → set Bob zone marker      (single, replaces old one)
     */
    @EventHandler
    public void onGoldAxe(PlayerInteractEvent e) {
        if (!isQuantumTool(e.getItem(), Material.GOLDEN_AXE)) return;
        e.setCancelled(true);
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            plugin.getE91Manager().placeBobParking(e.getPlayer());
        } else if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            plugin.getE91Manager().setBobEnd(e.getPlayer());
        }
    }

    /** Nether Star → place BB84 transmitter (sender). */
    @EventHandler
    public void onNetherStar(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isQuantumTool(e.getItem(), Material.NETHER_STAR)) return;
        e.setCancelled(true);
        plugin.getSenderManager().placeSender(e.getPlayer());
    }

    /** Eye of Ender → place measurement gate. */
    @EventHandler
    public void onGateTool(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isQuantumTool(e.getItem(), Material.ENDER_EYE)) return;
        e.setCancelled(true);
        plugin.getGateManager().placeGate(e.getPlayer());
    }

    /** Grover Setup (named Fire Charge) → right-click builds a new Grover grid. */
    @EventHandler
    public void onGroverSetupTool(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isQuantumTool(e.getItem(), Material.FIRE_CHARGE)) return;
        e.setCancelled(true);
        plugin.getGroverManager().setup(e.getPlayer(), -1); // auto-assign lowest free id
    }

    /** Place Chests (named Conduit) → right-click places chests on the nearest Grover grid. */
    @EventHandler
    public void onGroverPlaceTool(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isQuantumTool(e.getItem(), Material.CONDUIT)) return;
        e.setCancelled(true);
        plugin.getGroverManager().placeChestsNearest(e.getPlayer());
    }

    /** In-room "give tools" button → hands out that room's tools (clears inventory, like the command). */
    @EventHandler
    public void onToolButton(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.STONE_BUTTON) return;
        World w = e.getClickedBlock().getWorld();
        Location c = e.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);

        Entity marker = null;
        for (Entity m : w.getNearbyEntities(c, 0.6, 0.6, 0.6)) {
            if (m.getType() == EntityType.MARKER && m.getScoreboardTags().contains(Q_TOOLBTN)) { marker = m; break; }
        }
        if (marker == null) return;

        Player p = e.getPlayer();
        switch (num(marker)) {
            case 1 -> giveToolsBB84(p);
            case 2 -> giveToolsGrover(p);
            case 3 -> giveToolsE91(p);
            default -> { return; }
        }
        // Particles at the end-rod level (one block below the button)
        Location rodLevel = c.clone().add(0, -1.0, 0);
        w.spawnParticle(Particle.HAPPY_VILLAGER, rodLevel, 24, 0.3, 0.4, 0.3, 0.06);
        w.spawnParticle(Particle.END_ROD, rodLevel, 8, 0.2, 0.3, 0.2, 0.02);
        w.playSound(c, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.6f);
        flashToolLabel(w, num(marker), c);
    }

    private void flashToolLabel(World w, int set, Location near) {
        for (Entity e : w.getNearbyEntities(near, 1.5, 2.5, 1.5)) {
            if (!(e instanceof TextDisplay td)) continue;
            if (!e.getScoreboardTags().contains(Q_TOOLBTN) || num(e) != set) continue;
            Component orig = td.text();
            String plain = PlainTextComponentSerializer.plainText().serialize(orig);
            Component green = null;
            for (String line : plain.split("\n")) {
                Component part = Component.text((green == null ? "" : "\n") + line, NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true);
                green = (green == null) ? part : green.append(part);
            }
            td.text(green);
            final TextDisplay ftd = td;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (ftd.isValid()) ftd.text(orig); }, 25L);
            break;
        }
    }

    /** Grover Reset (named TNT) → placing it in the centre of a grid deactivates that Grover instance. */
    @EventHandler
    public void onGroverReset(BlockPlaceEvent e) {
        if (!isQuantumTool(e.getItemInHand(), Material.TNT)) return;
        e.setCancelled(true);
        Location loc = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        boolean cleared = plugin.getGroverManager().clearInstanceAt(loc);
        e.getPlayer().sendMessage(cleared
            ? Component.text("Grover instance deactivated — its id is free again.", NamedTextColor.RED)
            : Component.text("No Grover instance nearby to deactivate.", NamedTextColor.GRAY));
    }

    /** Heart of the Sea → summon a plasma atom. */
    @EventHandler
    public void onPlasmaSummon(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isQuantumTool(e.getItem(), Material.HEART_OF_THE_SEA)) return;
        e.setCancelled(true);
        plugin.getPlasmaManager().summon(e.getPlayer());
    }

    /** Echo Shard → remove the nearest plasma atom. */
    @EventHandler
    public void onPlasmaRemove(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isQuantumTool(e.getItem(), Material.ECHO_SHARD)) return;
        e.setCancelled(true);
        plugin.getPlasmaManager().despawnNearest(e.getPlayer());
    }
}
