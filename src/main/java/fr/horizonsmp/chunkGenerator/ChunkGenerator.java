package fr.horizonsmp.chunkGenerator;

import fr.horizonsmp.chunkGenerator.command.ChunkGeneratorCommand;
import fr.horizonsmp.chunkGenerator.config.ConfigLoader;
import fr.horizonsmp.chunkGenerator.config.PluginConfig;
import fr.horizonsmp.chunkGenerator.i18n.Messages;
import fr.horizonsmp.chunkGenerator.job.JobManager;
import fr.horizonsmp.chunkGenerator.job.JobStorage;
import fr.horizonsmp.chunkGenerator.job.PersistedJob;
import fr.horizonsmp.chunkGenerator.monitoring.PerformanceMonitor;
import fr.horizonsmp.chunkGenerator.monitoring.StatsDisplay;
import fr.horizonsmp.chunkGenerator.monitoring.ThrottleController;
import fr.horizonsmp.chunkGenerator.permission.PermissionService;
import fr.horizonsmp.chunkGenerator.platform.FoliaAdapter;
import fr.horizonsmp.chunkGenerator.platform.PaperAdapter;
import fr.horizonsmp.chunkGenerator.platform.PlatformAdapter;
import fr.horizonsmp.chunkGenerator.trim.TrimManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ChunkGenerator extends JavaPlugin {

    private ConfigLoader configLoader;
    private Messages messages;
    private PluginConfig config;
    private PermissionService permissionService;
    private PlatformAdapter platform;
    private PerformanceMonitor performanceMonitor;
    private ThrottleController throttle;
    private JobStorage jobStorage;
    private JobManager jobManager;
    private TrimManager trimManager;
    private StatsDisplay statsDisplay;

    @Override
    public void onEnable() {
        this.configLoader = new ConfigLoader(this);
        this.messages = new Messages(this);
        this.config = configLoader.load();
        this.messages.load();

        this.permissionService = new PermissionService(this);
        this.permissionService.announceProvider();

        this.platform = resolvePlatform();
        getLogger().info("Detected platform: " + (platform.isFolia() ? "Folia" : "Paper/Purpur"));

        this.performanceMonitor = new PerformanceMonitor(platform, config.monitoring().pollIntervalMs());
        this.performanceMonitor.start();

        this.throttle = new ThrottleController(config.throttle(), performanceMonitor);

        this.jobStorage = new JobStorage(this);
        this.jobStorage.ensureDirectory();

        this.jobManager = new JobManager(this, platform, throttle, performanceMonitor, jobStorage, this::pluginConfig);
        this.jobManager.start();

        this.trimManager = new TrimManager(this, platform);
        this.jobManager.setTrimActiveCheck(name -> trimManager.isActive(name));

        this.statsDisplay = new StatsDisplay(this, platform, jobManager, messages, this::pluginConfig);
        this.statsDisplay.start();

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(statsDisplay), this);

        List<PersistedJob> persisted = jobStorage.loadAll();
        if (!persisted.isEmpty()) {
            getLogger().info("Found " + persisted.size() + " persisted job(s).");
            jobManager.resumePersisted(persisted);
        }

        ChunkGeneratorCommand commandExecutor = new ChunkGeneratorCommand(this);
        var rootCommand = getCommand("chunkgen");
        if (rootCommand != null) {
            rootCommand.setExecutor(commandExecutor);
            rootCommand.setTabCompleter(commandExecutor);
        } else {
            getLogger().severe("Could not register /chunkgen command (missing in plugin.yml).");
        }

        getLogger().info("ChunkGenerator ready.");
    }

    @Override
    public void onDisable() {
        if (statsDisplay != null) {
            statsDisplay.stop();
        }
        if (trimManager != null) {
            trimManager.shutdown();
        }
        if (jobManager != null) {
            jobManager.shutdown();
        }
        if (performanceMonitor != null) {
            performanceMonitor.stop();
        }
    }

    public void reloadAll() {
        this.config = configLoader.load();
        this.messages.load();
        if (throttle != null) {
            throttle.updateSettings(config.throttle());
        }
    }

    private PlatformAdapter resolvePlatform() {
        if (FoliaAdapter.detected()) {
            try {
                return new FoliaAdapter(this);
            } catch (ReflectiveOperationException e) {
                getLogger().warning("Folia detected but adapter initialization failed: " + e.getMessage()
                        + " - falling back to Paper scheduling.");
            }
        }
        return new PaperAdapter(this);
    }

    public PluginConfig pluginConfig() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public PermissionService permissionService() {
        return permissionService;
    }

    public JobManager jobManager() {
        return jobManager;
    }

    public TrimManager trimManager() {
        return trimManager;
    }

    public StatsDisplay statsDisplay() {
        return statsDisplay;
    }
}
