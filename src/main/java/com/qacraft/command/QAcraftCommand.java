package com.qacraft.command;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;

public class QAcraftCommand implements CommandExecutor, TabCompleter {
    private final QAcraftPlugin plugin;

    public QAcraftCommand(QAcraftPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) { showHelp(p); return true; }

        switch (args[0].toLowerCase()) {
            case "help" -> showHelp(p);
            case "tools", "give" -> handleTools(p, args);
            case "sender" -> handleSender(p, args);
            case "gate" -> handleGate(p, args);
            case "waypoint", "wp" -> handleWaypoint(p, args);
            case "parking", "park" -> plugin.getWaypointManager().placeParking(p);
            case "photon" -> handlePhoton(p, args);
            case "clear" -> handleClear(p);
            case "send" -> handleSend(p, args);
            case "grover" -> handleGrover(p, args);
            case "e91" -> handleE91(p, args);
            case "plasma" -> handlePlasma(p, args);
            case "tutorial" -> handleTutorial(p, args);
            default -> p.sendMessage(Component.text("Unknown command. Use /qacraft help", NamedTextColor.RED));
        }
        return true;
    }

    private void handleTools(Player p, String[] args) {
        String cat = args.length > 1 ? args[1].toLowerCase() : "";
        switch (cat) {
            case "bb84", "communication", "comm" -> plugin.getToolManager().giveToolsBB84(p);
            case "grover"                        -> plugin.getToolManager().giveToolsGrover(p);
            case "e91"                           -> plugin.getToolManager().giveToolsE91(p);
            case "all"                           -> plugin.getToolManager().giveToolsAll(p);
            default -> {
                p.sendMessage(Component.text("Tool categories:", NamedTextColor.YELLOW));
                p.sendMessage(Component.text("/qacraft tools bb84", NamedTextColor.GOLD)
                    .append(Component.text(" — BB84 quantum-key tools", NamedTextColor.GRAY)));
                p.sendMessage(Component.text("/qacraft tools grover", NamedTextColor.GOLD)
                    .append(Component.text(" — Grover's search tools", NamedTextColor.GRAY)));
                p.sendMessage(Component.text("/qacraft tools e91", NamedTextColor.GOLD)
                    .append(Component.text(" — E91 entanglement tools", NamedTextColor.GRAY)));
                p.sendMessage(Component.text("/qacraft tools all", NamedTextColor.GOLD)
                    .append(Component.text(" — all tools at once", NamedTextColor.GRAY)));
            }
        }
    }

    private void handleSender(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(Component.text("/qacraft sender <place|fill|start|stop>", NamedTextColor.YELLOW)); return; }
        switch (args[1].toLowerCase()) {
            case "place" -> plugin.getSenderManager().placeSender(p);
            case "fill"  -> plugin.getSenderManager().fillChest(p);
            case "start" -> plugin.getSenderManager().start(p);
            case "stop"  -> plugin.getSenderManager().stop();
        }
    }

    private void handleGate(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(Component.text("/qacraft gate <place|clear>", NamedTextColor.YELLOW)); return; }
        switch (args[1].toLowerCase()) {
            case "place" -> plugin.getGateManager().placeGate(p);
            case "clear" -> { plugin.getGateManager().clearAll(); Bukkit.broadcast(Component.text("Gates cleared.", NamedTextColor.GRAY)); }
        }
    }

    private void handleWaypoint(Player p, String[] args) {
        if (args.length < 2) { plugin.getWaypointManager().placeWaypoint(p); return; }
        switch (args[1].toLowerCase()) {
            case "place" -> plugin.getWaypointManager().placeWaypoint(p);
            case "clear" -> { plugin.getWaypointManager().clearAll(); Bukkit.broadcast(Component.text("Stations cleared.", NamedTextColor.GRAY)); }
        }
    }

    private void handlePhoton(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(Component.text("/qacraft photon <clear|info>", NamedTextColor.YELLOW)); return; }
        switch (args[1].toLowerCase()) {
            case "clear" -> { plugin.getPhotonManager().clearAll(); Bukkit.broadcast(Component.text("Photons cleared.", NamedTextColor.GRAY)); }
            case "info" -> {
                for (Entity e : tagged(p.getWorld(), PHOTON)) {
                    p.sendMessage(Component.text("F" + num(e), NamedTextColor.GOLD)
                        .append(Component.text(" basis=" + basis(e) + "(0=+,1=x) bit=" + bit(e) + "(0=blue,1=red)", NamedTextColor.DARK_GRAY)));
                }
            }
        }
    }

    private void handleClear(Player p) {
        plugin.getPhotonManager().clearAll();
        plugin.getWaypointManager().clearAll();
        plugin.getGateManager().clearAll();
        plugin.getSenderManager().clearSender();
        plugin.getGroverManager().clear();
        plugin.getE91Manager().clear();
        plugin.getPlasmaManager().clearAll();
        plugin.getTutorialManager().clearDisplays();
        Bukkit.broadcast(Component.text("Everything cleared.", NamedTextColor.GRAY));
    }

    // =========================================================================
    // Plasma + Tutorial subcommand handlers
    // =========================================================================

    private void handlePlasma(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(Component.text("/qacraft plasma <summon|despawn|clear>", NamedTextColor.YELLOW));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "summon"  -> plugin.getPlasmaManager().summon(p);
            case "despawn" -> plugin.getPlasmaManager().despawnNearest(p);
            case "clear"   -> {
                plugin.getPlasmaManager().clearAll();
                p.sendMessage(Component.text("All plasmas cleared.", NamedTextColor.GRAY));
            }
            default -> p.sendMessage(Component.text("/qacraft plasma <summon|despawn|clear>", NamedTextColor.YELLOW));
        }
    }

    private void handleTutorial(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(Component.text("/qacraft tutorial <start|next|back|goto|stop|spawn|clear|reload>", NamedTextColor.YELLOW));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "start"  -> plugin.getTutorialManager().start(p);
            case "next"   -> plugin.getTutorialManager().next(p);
            case "back"   -> plugin.getTutorialManager().back(p);
            case "stop"   -> plugin.getTutorialManager().stop(p);
            case "spawn"  -> plugin.getTutorialManager().spawnDisplays(p);
            case "clear"  -> {
                plugin.getTutorialManager().clearDisplays();
                p.sendMessage(Component.text("Tutorial displays cleared.", NamedTextColor.GRAY));
            }
            case "reload" -> {
                plugin.getTutorialManager().loadSteps();
                p.sendMessage(Component.text("Tutorial reloaded — "
                    + plugin.getTutorialManager().stepCount() + " steps.", NamedTextColor.GREEN));
            }
            case "goto"   -> {
                if (args.length < 3) {
                    p.sendMessage(Component.text("/qacraft tutorial goto <step>", NamedTextColor.YELLOW));
                    return;
                }
                try { plugin.getTutorialManager().gotoStep(p, Integer.parseInt(args[2])); }
                catch (NumberFormatException ex) {
                    p.sendMessage(Component.text("Step must be a number.", NamedTextColor.RED));
                }
            }
            default -> p.sendMessage(Component.text("/qacraft tutorial <start|next|back|goto|stop|spawn|clear|reload>", NamedTextColor.YELLOW));
        }
    }

    private void handleSend(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage(Component.text("/qacraft send <rect|diag> <0|1>", NamedTextColor.YELLOW)); return; }
        int basis = args[1].equalsIgnoreCase("rect") ? 0 : 1;
        int bit = args[2].equals("1") ? 1 : 0;
        plugin.getPhotonManager().spawnWithState(p.getLocation().add(0, 0.5, 0), basis, bit, p);
    }

    private void handleGrover(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(Component.text("/qacraft grover <setup|placechests|fillwool|iterate|reset|clear>", NamedTextColor.YELLOW)); return; }
        switch (args[1].toLowerCase()) {
            case "setup" -> plugin.getGroverManager().setup(p);
            case "iterate", "iter" -> plugin.getGroverManager().iterate(p);
            case "reset" -> plugin.getGroverManager().reset(p);
            case "placechests", "place" -> plugin.getGroverManager().placeChests(p);
            case "fillwool", "fill" -> plugin.getGroverManager().fillWool(p);
            case "clear" -> { plugin.getGroverManager().clear(); Bukkit.broadcast(Component.text("Grover cleared.", NamedTextColor.GRAY)); }
            default -> p.sendMessage(Component.text("/qacraft grover <setup|placechests|fillwool|iterate|reset|clear>", NamedTextColor.YELLOW));
        }
    }

    private void handleE91(Player p, String[] args) {
        if (args.length < 2) { showE91Help(p); return; }
        switch (args[1].toLowerCase()) {
            case "source"          -> plugin.getE91Manager().placeSource(p);
            case "alice"           -> plugin.getE91Manager().setAliceEnd(p);
            case "bob"             -> plugin.getE91Manager().setBobEnd(p);
            case "generate", "gen" -> plugin.getE91Manager().generate(p);
            case "start"           -> plugin.getE91Manager().startAuto(p);
            case "stop"            -> plugin.getE91Manager().stopAuto(p);
            case "key"             -> plugin.getE91Manager().showKey(p);
            case "bell"            -> plugin.getE91Manager().showBell(p);
            case "eve"             -> plugin.getE91Manager().toggleEve(p);
            case "clear"           -> { plugin.getE91Manager().clear();
                                        Bukkit.broadcast(Component.text("E91 cleared.", NamedTextColor.GRAY)); }
            default -> showE91Help(p);
        }
    }

    private void showE91Help(Player p) {
        p.sendMessage(Component.text("=== E91 ENTANGLEMENT PROTOCOL ===", NamedTextColor.LIGHT_PURPLE));
        p.sendMessage(Component.text("/qacraft e91 source", NamedTextColor.YELLOW).append(Component.text(" — place entangled pair source", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft e91 alice|bob", NamedTextColor.YELLOW).append(Component.text(" — mark Alice's / Bob's endpoint", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft e91 generate", NamedTextColor.YELLOW).append(Component.text(" — spawn one entangled pair", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft e91 start|stop", NamedTextColor.YELLOW).append(Component.text(" — auto-generate (1 pair / 2 s)", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft e91 key", NamedTextColor.YELLOW).append(Component.text(" — show extracted key bits", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft e91 bell", NamedTextColor.YELLOW).append(Component.text(" — show cross-basis correlations", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft e91 eve", NamedTextColor.YELLOW).append(Component.text(" — toggle eavesdropping simulation", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft e91 clear", NamedTextColor.YELLOW).append(Component.text(" — remove all E91 entities + log", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("Hold COMPASS(+) / REC.COMPASS(×) / CLOCK(○) near arrived photon to measure", NamedTextColor.DARK_GRAY));
    }

    private void showHelp(Player p) {
        p.sendMessage(Component.text("=== QACRAFT — QUANTUM COMMUNICATION ===", NamedTextColor.AQUA));
        p.sendMessage(Component.text(""));
        p.sendMessage(Component.text("/qacraft tools", NamedTextColor.YELLOW).append(Component.text(" — give all tools", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft sender place|fill|start|stop", NamedTextColor.YELLOW).append(Component.text(" — transmitter", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft gate place|clear", NamedTextColor.YELLOW).append(Component.text(" — measurement gate", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft wp [place|clear]", NamedTextColor.YELLOW).append(Component.text(" — stations", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft parking", NamedTextColor.YELLOW).append(Component.text(" — parking spot", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft send rect|diag 0|1", NamedTextColor.YELLOW).append(Component.text(" — manual send", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft photon clear|info", NamedTextColor.YELLOW).append(Component.text(" — photon utils", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft clear", NamedTextColor.YELLOW).append(Component.text(" — clear everything", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("  Nether Star=sender  Breeze Rod=gate", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text(""));
        p.sendMessage(Component.text("=== GROVER'S SEARCH ===", NamedTextColor.GOLD));
        p.sendMessage(Component.text("/qacraft grover setup", NamedTextColor.YELLOW).append(Component.text(" — mark 8 positions", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft grover placechests", NamedTextColor.YELLOW).append(Component.text(" — auto-place chests", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft grover fillwool", NamedTextColor.YELLOW).append(Component.text(" — random wool in each chest", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft grover iterate", NamedTextColor.YELLOW).append(Component.text(" — amplify (Wind Charge)", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft grover reset", NamedTextColor.YELLOW).append(Component.text(" — superposition", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft grover clear", NamedTextColor.YELLOW).append(Component.text(" — remove markers", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("Hold any item (offhand) + spyglass (main) to see probabilities", NamedTextColor.GRAY));
        p.sendMessage(Component.text(""));
        p.sendMessage(Component.text("=== E91 ENTANGLEMENT PROTOCOL ===", NamedTextColor.LIGHT_PURPLE));
        p.sendMessage(Component.text("/qacraft e91 source|alice|bob", NamedTextColor.YELLOW).append(Component.text(" — place landmarks", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft e91 generate|start|stop", NamedTextColor.YELLOW).append(Component.text(" — spawn pairs", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft e91 key|bell|eve|clear", NamedTextColor.YELLOW).append(Component.text(" — analyse & control", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("  Amethyst Shard=source  Diamond Axe=Alice  Gold Axe=Bob", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("Hold COMPASS/REC.COMPASS/CLOCK near photon to measure", NamedTextColor.GRAY));
        p.sendMessage(Component.text(""));
        p.sendMessage(Component.text("=== LOBBY & WORKSHOP ===", NamedTextColor.AQUA));
        p.sendMessage(Component.text("/qacraft plasma summon|despawn|clear", NamedTextColor.YELLOW).append(Component.text(" — particle atom", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft tutorial start|next|back|stop", NamedTextColor.YELLOW).append(Component.text(" — guided walkthrough", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/qacraft tutorial spawn|clear|reload", NamedTextColor.YELLOW).append(Component.text(" — station displays (admin)", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 1) return filter(args[0], "help","tools","sender","gate","waypoint","parking","photon","send","clear","grover","e91","plasma","tutorial");
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "tools", "give" -> filter(args[1], "bb84", "grover", "e91", "all");
                case "sender" -> filter(args[1], "place", "fill", "start", "stop");
                case "gate" -> filter(args[1], "place", "clear");
                case "waypoint", "wp" -> filter(args[1], "place", "clear");
                case "photon" -> filter(args[1], "clear", "info");
                case "send" -> filter(args[1], "rect", "diag");
                case "grover" -> filter(args[1], "setup", "placechests", "fillwool", "iterate", "reset", "clear");
                case "e91"    -> filter(args[1], "source", "alice", "bob", "generate", "start", "stop", "key", "bell", "eve", "clear");
                case "plasma" -> filter(args[1], "summon", "despawn", "clear");
                case "tutorial" -> filter(args[1], "start", "next", "back", "goto", "stop", "spawn", "clear", "reload");
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("send")) return filter(args[2], "0", "1");
        return List.of();
    }

    private List<String> filter(String input, String... options) {
        String low = input.toLowerCase();
        return Arrays.stream(options).filter(o -> o.startsWith(low)).toList();
    }
}
