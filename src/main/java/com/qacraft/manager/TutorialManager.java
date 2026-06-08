package com.qacraft.manager;

import com.qacraft.QAcraftPlugin;
import static com.qacraft.util.QAcraftEntity.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import java.io.File;
import java.time.Duration;
import java.util.*;

/**
 * Step-by-step walkthrough for workshops.
 *
 * Steps are loaded from {@code plugins/QAcraft/tutorial.yml} (copied from the
 * jar resource on first run). Each step has a teleport target, chat description,
 * subtitle line, and optional floating TextDisplay anchor.
 *
 * Commands:
 *   /qacraft tutorial start         — teleport to step 0
 *   /qacraft tutorial next | back   — navigate
 *   /qacraft tutorial goto N        — jump
 *   /qacraft tutorial stop          — exit
 *   /qacraft tutorial spawn         — (re)create the floating TextDisplays
 *   /qacraft tutorial clear         — remove the floating TextDisplays
 */
public class TutorialManager {

    private final QAcraftPlugin plugin;
    private final List<TutorialStep> steps = new ArrayList<>();
    private final Map<UUID, Integer> playerStep = new HashMap<>();

    public TutorialManager(QAcraftPlugin plugin) {
        this.plugin = plugin;
        loadSteps();
    }

    // =========================================================================
    // Load default tutorial.yml from jar resource on first run
    // =========================================================================

    public void loadSteps() {
        steps.clear();
        File f = new File(plugin.getDataFolder(), "tutorial.yml");
        if (!f.exists()) {
            try { plugin.saveResource("tutorial.yml", false); }
            catch (IllegalArgumentException ignored) { /* resource missing — leave empty */ }
        }
        if (!f.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        List<Map<?, ?>> raw = cfg.getMapList("steps");
        for (Map<?, ?> entry : raw) {
            String id       = str(entry.get("id"), "step");
            String title    = str(entry.get("title"), id);
            String chat     = str(entry.get("chat"), "");
            String subtitle = str(entry.get("subtitle"), "");
            Location tp     = parseLocation(entry.get("teleport"));
            Location disp   = parseLocation(entry.get("display"));
            steps.add(new TutorialStep(id, title, chat, subtitle, tp, disp));
        }
        plugin.getLogger().info("Tutorial loaded — " + steps.size() + " steps");
    }

    private static String str(Object o, String fallback) {
        return o == null ? fallback : o.toString();
    }

    private Location parseLocation(Object o) {
        if (!(o instanceof Map<?, ?> m)) return null;
        String worldName = str(m.get("world"), "world");
        World w = plugin.getServer().getWorld(worldName);
        if (w == null) return null;
        double x = num(m.get("x")), y = num(m.get("y")), z = num(m.get("z"));
        float yaw   = (float) num(m.get("yaw"));
        float pitch = (float) num(m.get("pitch"));
        return new Location(w, x, y, z, yaw, pitch);
    }

    private static double num(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o == null) return 0;
        try { return Double.parseDouble(o.toString()); } catch (NumberFormatException e) { return 0; }
    }

    public int stepCount() { return steps.size(); }

    // =========================================================================
    // Per-player navigation
    // =========================================================================

    public void start(Player p) {
        if (steps.isEmpty()) {
            p.sendMessage(Component.text("Tutorial has no steps. Edit tutorial.yml in the plugin folder.", NamedTextColor.RED));
            return;
        }
        playerStep.put(p.getUniqueId(), 0);
        showStep(p, 0);
    }

    public void next(Player p) {
        Integer cur = playerStep.get(p.getUniqueId());
        if (cur == null) {
            p.sendMessage(Component.text("Use /qacraft tutorial start first.", NamedTextColor.YELLOW));
            return;
        }
        int n = cur + 1;
        if (n >= steps.size()) {
            p.sendMessage(Component.text("Tutorial complete!", NamedTextColor.GREEN));
            p.showTitle(Title.title(
                Component.text("Tutorial Complete", NamedTextColor.GREEN),
                Component.text("Welcome to QAcraft", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500))));
            playerStep.remove(p.getUniqueId());
            return;
        }
        playerStep.put(p.getUniqueId(), n);
        showStep(p, n);
    }

    public void back(Player p) {
        Integer cur = playerStep.get(p.getUniqueId());
        if (cur == null || cur == 0) {
            p.sendMessage(Component.text("Already at the first step.", NamedTextColor.YELLOW));
            return;
        }
        int n = cur - 1;
        playerStep.put(p.getUniqueId(), n);
        showStep(p, n);
    }

    public void gotoStep(Player p, int n) {
        if (n < 0 || n >= steps.size()) {
            p.sendMessage(Component.text("Step out of range (0–" + (steps.size() - 1) + ").", NamedTextColor.RED));
            return;
        }
        playerStep.put(p.getUniqueId(), n);
        showStep(p, n);
    }

    public void stop(Player p) {
        if (playerStep.remove(p.getUniqueId()) != null) {
            p.sendMessage(Component.text("Tutorial mode exited.", NamedTextColor.GRAY));
        } else {
            p.sendMessage(Component.text("You weren't in tutorial mode.", NamedTextColor.YELLOW));
        }
    }

    private void showStep(Player p, int idx) {
        TutorialStep s = steps.get(idx);
        if (s.teleport != null) p.teleport(s.teleport);

        p.sendMessage(Component.text("══ Step " + (idx + 1) + "/" + steps.size() + ": "
            + s.title + " ══", NamedTextColor.AQUA));
        if (!s.chat.isBlank()) p.sendMessage(Component.text(s.chat, NamedTextColor.WHITE));
        p.sendMessage(Component.text("Use /qacraft tutorial next to continue.", NamedTextColor.DARK_GRAY));

        Component titleComp    = Component.text(s.title,    NamedTextColor.AQUA);
        Component subtitleComp = Component.text(s.subtitle.isBlank() ? "Step " + (idx + 1) : s.subtitle,
            NamedTextColor.GRAY);
        p.showTitle(Title.title(titleComp, subtitleComp,
            Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
    }

    // =========================================================================
    // Floating TextDisplay station signs
    // =========================================================================

    /** Spawn / re-spawn the floating step descriptions defined in tutorial.yml. Idempotent. */
    public void spawnDisplays(Player p) {
        // Clear existing first so this is idempotent
        clearDisplays();

        int count = 0;
        for (int i = 0; i < steps.size(); i++) {
            TutorialStep s = steps.get(i);
            if (s.display == null) continue;
            spawnDisplay(s.display, i + 1, s.title, s.chat);
            count++;
        }
        p.sendMessage(Component.text("Spawned " + count + " tutorial displays.", NamedTextColor.GREEN));
    }

    private void spawnDisplay(Location loc, int stepNum, String title, String chat) {
        World w = loc.getWorld();
        TextDisplay td = (TextDisplay) w.spawnEntity(loc, EntityType.TEXT_DISPLAY);
        Component header = Component.text("Step " + stepNum + " — " + title, NamedTextColor.AQUA);
        Component body   = Component.text("\n" + wrap(chat, 40), NamedTextColor.WHITE);
        td.text(header.append(body));
        td.setBillboard(Display.Billboard.CENTER);
        td.setShadowed(true);
        td.setSeeThrough(false);
        td.setTransformation(new Transformation(
            new Vector3f(), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf()));
        td.addScoreboardTag(Q_TUTORIAL_DISPLAY);
    }

    /** Crude word-wrap to keep TextDisplay readable. */
    private static String wrap(String text, int width) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        int lineLen = 0;
        for (String word : text.split(" ")) {
            if (lineLen + word.length() + 1 > width) { out.append('\n'); lineLen = 0; }
            else if (lineLen > 0)                    { out.append(' ');  lineLen++; }
            out.append(word);
            lineLen += word.length();
        }
        return out.toString();
    }

    public void clearDisplays() {
        for (World w : plugin.getServer().getWorlds()) killAll(w, Q_TUTORIAL_DISPLAY);
    }

    // =========================================================================
    // Step data
    // =========================================================================

    private static final class TutorialStep {
        final String id, title, chat, subtitle;
        final Location teleport, display;
        TutorialStep(String id, String title, String chat, String subtitle,
                     Location teleport, Location display) {
            this.id = id; this.title = title; this.chat = chat; this.subtitle = subtitle;
            this.teleport = teleport; this.display = display;
        }
    }
}
