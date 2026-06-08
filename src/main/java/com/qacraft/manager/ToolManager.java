package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class ToolManager implements Listener {
    private final QAcraftPlugin plugin;

    public ToolManager(QAcraftPlugin plugin) { this.plugin = plugin; }

    // =========================================================================
    // Tool sets by protocol
    // =========================================================================

    /** BB84 quantum-key distribution tools. */
    public void giveToolsBB84(Player p) {
        p.getInventory().addItem(
            makeItem(Material.COMPASS,                 "Rectilinear Filter", NamedTextColor.AQUA),
            makeItem(Material.RECOVERY_COMPASS,        "Diagonal Filter",    NamedTextColor.LIGHT_PURPLE),
            makeItem(Material.SNOWBALL,                "Quantum Source",     NamedTextColor.WHITE),
            makeItem(Material.CARROT_ON_A_STICK,       "Quantum Cable",      NamedTextColor.GOLD),
            makeItem(Material.WARPED_FUNGUS_ON_A_STICK,"Parking Spot",       NamedTextColor.GREEN),
            makeItem(Material.NETHER_STAR,             "Quantum Sender",     NamedTextColor.YELLOW),
            makeItem(Material.BREEZE_ROD,              "Quantum Gate",       NamedTextColor.GREEN)
        );
        p.getInventory().setItem(8, makeItem(Material.BRUSH, "Quantum Eraser", NamedTextColor.GRAY));
        p.sendMessage(Component.text("BB84 tools received!", NamedTextColor.GREEN));
    }

    /** Grover's search algorithm tools. */
    public void giveToolsGrover(Player p) {
        p.getInventory().addItem(
            makeItem(Material.WIND_CHARGE, "Grover Operator",   NamedTextColor.YELLOW),
            makeItem(Material.SPYGLASS,    "Quantum Observer",  NamedTextColor.AQUA)
        );
        p.getInventory().setItem(8, makeItem(Material.BRUSH, "Quantum Eraser", NamedTextColor.GRAY));
        p.sendMessage(Component.text("Grover tools received!", NamedTextColor.GREEN));
    }

    /** E91 entanglement-protocol tools. */
    public void giveToolsE91(Player p) {
        p.getInventory().addItem(
            makeItem(Material.AMETHYST_SHARD,   "EPR Source",          NamedTextColor.LIGHT_PURPLE),
            makeItem(Material.COMPASS,          "Rectilinear Filter",  NamedTextColor.AQUA),
            makeItem(Material.RECOVERY_COMPASS, "Diagonal Filter",     NamedTextColor.LIGHT_PURPLE),
            makeItem(Material.CLOCK,            "Circular Filter",     NamedTextColor.GREEN),
            makeItem(Material.DIAMOND_AXE,      "Alice Landing Pad",   NamedTextColor.AQUA),
            makeItem(Material.GOLDEN_AXE,       "Bob Landing Pad",     NamedTextColor.GOLD)
        );
        p.getInventory().setItem(8, makeItem(Material.BRUSH, "Quantum Eraser", NamedTextColor.GRAY));
        p.sendMessage(Component.text("E91 entanglement tools received!", NamedTextColor.GREEN));
    }

    /** All tools at once (no duplicates). */
    public void giveToolsAll(Player p) {
        p.getInventory().addItem(
            makeItem(Material.AMETHYST_SHARD,          "EPR Source",          NamedTextColor.LIGHT_PURPLE),
            makeItem(Material.COMPASS,                 "Rectilinear Filter",  NamedTextColor.AQUA),
            makeItem(Material.RECOVERY_COMPASS,        "Diagonal Filter",     NamedTextColor.LIGHT_PURPLE),
            makeItem(Material.SNOWBALL,                "Quantum Source",      NamedTextColor.WHITE),
            makeItem(Material.CARROT_ON_A_STICK,       "Quantum Cable",       NamedTextColor.GOLD),
            makeItem(Material.WARPED_FUNGUS_ON_A_STICK,"Parking Spot",        NamedTextColor.GREEN),
            makeItem(Material.NETHER_STAR,             "Quantum Sender",      NamedTextColor.YELLOW),
            makeItem(Material.BREEZE_ROD,              "Quantum Gate",        NamedTextColor.GREEN),
            makeItem(Material.WIND_CHARGE,             "Grover Operator",     NamedTextColor.YELLOW),
            makeItem(Material.SPYGLASS,                "Quantum Observer",    NamedTextColor.AQUA),
            makeItem(Material.CLOCK,                   "Circular Filter",     NamedTextColor.GREEN),
            makeItem(Material.DIAMOND_AXE,             "Alice Landing Pad",   NamedTextColor.AQUA),
            makeItem(Material.GOLDEN_AXE,              "Bob Landing Pad",     NamedTextColor.GOLD)
        );
        p.getInventory().setItem(8, makeItem(Material.BRUSH, "Quantum Eraser", NamedTextColor.GRAY));
        p.sendMessage(Component.text("All quantum tools received!", NamedTextColor.GREEN));
    }

    // =========================================================================
    // Tool helpers
    // =========================================================================

    ItemStack makeItem(Material mat, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isQuantumTool(ItemStack item, Material mat) {
        return item != null && item.getType() == mat && item.hasItemMeta();
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

    /** Breeze Rod → place measurement gate. */
    @EventHandler
    public void onBreezeRod(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isQuantumTool(e.getItem(), Material.BREEZE_ROD)) return;
        e.setCancelled(true);
        plugin.getGateManager().placeGate(e.getPlayer());
    }
}
