package com.qacraft;

import com.qacraft.command.QAcraftCommand;
import com.qacraft.manager.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;

public class QAcraftPlugin extends JavaPlugin {
    private static QAcraftPlugin instance;
    private ToolManager toolManager;
    private PhotonManager photonManager;
    private WaypointManager waypointManager;
    private GateManager gateManager;
    private SenderManager senderManager;
    private EraserManager eraserManager;
    private MeasurementManager measurementManager;
    private GroverManager groverManager;
    private E91Manager e91Manager;
    private PlasmaManager plasmaManager;
    private TutorialManager tutorialManager;
    private TutorialWorldBuilder worldBuilder;
    private InteractiveTutorial interactiveTutorial;

    @Override
    public void onEnable() {
        instance = this;
        // Ensure data folder exists so saveResource() and YAML writes work
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        toolManager        = new ToolManager(this);
        photonManager      = new PhotonManager(this);
        waypointManager    = new WaypointManager(this);
        gateManager        = new GateManager(this);
        senderManager      = new SenderManager(this);
        eraserManager      = new EraserManager(this);
        measurementManager = new MeasurementManager(this);
        groverManager      = new GroverManager(this);
        e91Manager         = new E91Manager(this);
        plasmaManager       = new PlasmaManager(this);
        tutorialManager     = new TutorialManager(this);
        worldBuilder        = new TutorialWorldBuilder(this);
        interactiveTutorial = new InteractiveTutorial(this);

        getServer().getPluginManager().registerEvents(toolManager, this);
        getServer().getPluginManager().registerEvents(eraserManager, this);
        getServer().getPluginManager().registerEvents(measurementManager, this);
        getServer().getPluginManager().registerEvents(groverManager, this);
        getServer().getPluginManager().registerEvents(e91Manager, this);
        getServer().getPluginManager().registerEvents(interactiveTutorial, this);

        QAcraftCommand cmd = new QAcraftCommand(this);
        getCommand("qacraft").setExecutor(cmd);
        getCommand("qacraft").setTabCompleter(cmd);

        // Main tick loop
        getServer().getScheduler().runTaskTimer(this, () -> {
            photonManager.tick();
            gateManager.tick();
            senderManager.tick();
            groverManager.tick();
            e91Manager.tick();
            plasmaManager.tick();
            interactiveTutorial.tick();
        }, 1L, 1L);

        // Auto-save runtime state every 60 s as a crash safety net
        getServer().getScheduler().runTaskTimer(this, this::saveAllState, 20L * 60, 20L * 60);

        // Restore runtime state from previous run
        loadAllState();

        getLogger().info("QAcraft loaded!");
    }

    @Override
    public void onDisable() {
        // NOTE: entity clearing is intentionally NOT performed here so that
        // BlockDisplays, TextDisplays and Markers persist across server restarts.
        // The user-facing /qacraft clear and per-protocol clear subcommands still
        // call clearAll() explicitly.
        saveAllState();
        getLogger().info("QAcraft disabled — entities preserved.");
    }

    // =========================================================================
    // Manager runtime state — save/load orchestration
    // =========================================================================

    private void saveAllState() {
        try {
            File f = new File(getDataFolder(), "state.yml");
            YamlConfiguration cfg = new YamlConfiguration();
            if (senderManager != null) senderManager.saveState(cfg.createSection("sender"));
            if (groverManager != null) groverManager.saveState(cfg.createSection("grover"));
            if (e91Manager    != null) e91Manager.saveState(cfg.createSection("e91"));
            cfg.save(f);
        } catch (IOException ex) {
            getLogger().warning("State save failed: " + ex.getMessage());
        }
    }

    private void loadAllState() {
        File f = new File(getDataFolder(), "state.yml");
        if (!f.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        if (cfg.isConfigurationSection("sender")) senderManager.loadState(cfg.getConfigurationSection("sender"));
        if (cfg.isConfigurationSection("grover")) groverManager.loadState(cfg.getConfigurationSection("grover"));
        if (cfg.isConfigurationSection("e91"))    e91Manager.loadState(cfg.getConfigurationSection("e91"));
        getLogger().info("QAcraft state restored from state.yml");
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public static QAcraftPlugin getInstance() { return instance; }
    public ToolManager        getToolManager()        { return toolManager; }
    public PhotonManager      getPhotonManager()      { return photonManager; }
    public WaypointManager    getWaypointManager()    { return waypointManager; }
    public GateManager        getGateManager()        { return gateManager; }
    public SenderManager      getSenderManager()      { return senderManager; }
    public EraserManager      getEraserManager()      { return eraserManager; }
    public MeasurementManager getMeasurementManager() { return measurementManager; }
    public GroverManager      getGroverManager()      { return groverManager; }
    public E91Manager         getE91Manager()         { return e91Manager; }
    public PlasmaManager      getPlasmaManager()      { return plasmaManager; }
    public TutorialManager    getTutorialManager()    { return tutorialManager; }
    public TutorialWorldBuilder getWorldBuilder()     { return worldBuilder; }
    public InteractiveTutorial getInteractiveTutorial() { return interactiveTutorial; }
}
