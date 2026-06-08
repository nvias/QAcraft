package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;

public class MeasurementManager implements Listener {
    private final QAcraftPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final java.util.Random random = new java.util.Random();

    public MeasurementManager(QAcraftPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.COMPASS && item.getType() != Material.RECOVERY_COMPASS) return;

        // Cooldown check
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(p.getUniqueId()) && now - cooldowns.get(p.getUniqueId()) < 2000) return;

        int playerBasis = item.getType() == Material.COMPASS ? 0 : 1;

        // Raycast for photons
        Location eye = p.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection();

        for (double d = 1; d <= 5; d += 1) {
            Location check = eye.clone().add(dir.clone().multiply(d));
            Entity photon = nearest(check, PHOTON, 1.2);
            if (photon != null && photon.getScoreboardTags().contains(SUPERPOSITION) && age(photon) >= 20) {
                measure(p, photon, playerBasis);
                cooldowns.put(p.getUniqueId(), now);
                return;
            }
        }
    }

    private void measure(Player p, Entity photon, int playerBasis) {
        int phBasis = basis(photon);
        int phBit = bit(photon);
        int phNum = num(photon);
        int result;

        if (playerBasis == phBasis) {
            result = phBit;
        } else {
            result = random.nextInt(2);
            setBasis(photon, playerBasis);
            setBit(photon, result);
        }

        String basisName = playerBasis == 0 ? "Rectilinear +" : "Diagonal x";
        NamedTextColor c = playerBasis == 0 ? NamedTextColor.AQUA : NamedTextColor.LIGHT_PURPLE;
        NamedTextColor bc = result == 0 ? NamedTextColor.BLUE : NamedTextColor.RED;

        p.sendMessage(Component.text("Photon F" + phNum, c)
            .append(Component.text(" | " + basisName + " | bit = ", NamedTextColor.GRAY))
            .append(Component.text("" + result, bc)));

        plugin.getPhotonManager().flashStart(photon, result);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
    }
}
