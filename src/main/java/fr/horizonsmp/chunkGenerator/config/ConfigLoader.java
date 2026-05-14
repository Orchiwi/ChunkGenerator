package fr.horizonsmp.chunkGenerator.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigLoader {

    private final JavaPlugin plugin;

    public ConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public PluginConfig load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        PluginConfig.Throttle throttle = readThrottle(cfg);
        logThrottleResolution(throttle);

        ConfigurationSection bossSection = section(cfg, "bossbar");
        ConfigurationSection consoleSection = section(cfg, "console");
        PluginConfig.Display display = new PluginConfig.Display(
                new PluginConfig.Display.BossBar(
                        bossSection.getBoolean("enabled", true),
                        bossSection.getString("color", "BLUE")
                ),
                new PluginConfig.Display.ActionBar(
                        cfg.getBoolean("actionbar", true)
                ),
                new PluginConfig.Display.Console(
                        consoleSection.getBoolean("enabled", true),
                        consoleSection.getLong("interval-seconds", 30L)
                )
        );

        PluginConfig.Monitoring monitoring = new PluginConfig.Monitoring(
                cfg.getLong("monitoring-poll-ms", 1000L)
        );

        PluginConfig.Persistence persistence = new PluginConfig.Persistence(
                cfg.getBoolean("auto-resume", true),
                cfg.getLong("save-throttle-seconds", 5L),
                cfg.getLong("save-throttle-chunks", 1000L)
        );

        return new PluginConfig(throttle, display, monitoring, persistence);
    }

    private static ConfigurationSection section(ConfigurationSection cfg, String path) {
        ConfigurationSection s = cfg.getConfigurationSection(path);
        return s != null ? s : cfg.createSection(path);
    }

    private void logThrottleResolution(PluginConfig.Throttle t) {
        int cores = Runtime.getRuntime().availableProcessors();
        long heapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        plugin.getLogger().info("Throttle resolved: target-tps=" + t.targetTps()
                + ", start-inflight=" + t.startInflight()
                + ", max-inflight=" + t.maxInflight()
                + " (host: " + cores + " cores, " + heapMb + " MB heap)");
        int workerRec = Math.max(2, cores - 1);
        int ioRec = Math.max(2, Math.min(4, cores / 2));
        plugin.getLogger().info("Sustained throughput is bounded by Paper's chunk worker count. "
                + "Default -1 (auto) typically allocates around cores/2 workers; for a host "
                + "doing pre-generation with no players online, set paper-global.yml > "
                + "chunk-system > worker-threads to " + workerRec
                + " and io-threads to " + ioRec
                + " (on older Paper versions the relevant key is gen-parallelism).");
    }

    private PluginConfig.Throttle readThrottle(ConfigurationSection cfg) {
        ConfigurationSection s = section(cfg, "throttle");
        double targetTps = cfg.getDouble("target-tps", 18.5);
        boolean autoScale = s.getBoolean("auto-scale", true);
        int rawMax = s.getInt("max-inflight", 0);
        int rawMin = s.getInt("min-inflight", 8);
        int rawStart = s.getInt("start-inflight", 0);
        double memBackoff = s.getDouble("memory-backoff-pct", 70.0);
        double memPause = s.getDouble("memory-pause-pct", 80.0);

        int cores = Runtime.getRuntime().availableProcessors();
        long heapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);

        int maxInflight = rawMax > 0
                ? rawMax
                : autoScale
                        ? Math.max(64, (int) Math.min((long) cores * 32L, heapMb / 60L))
                        : 96;
        int startInflight = rawStart > 0
                ? rawStart
                : autoScale
                        ? Math.max(16, cores * 6)
                        : 32;
        int minInflight = Math.max(1, rawMin);
        if (minInflight > maxInflight) {
            minInflight = maxInflight;
        }
        if (startInflight < minInflight) {
            startInflight = minInflight;
        }
        if (startInflight > maxInflight) {
            startInflight = maxInflight;
        }
        if (memBackoff < 10.0) memBackoff = 10.0;
        if (memBackoff > 99.0) memBackoff = 99.0;
        if (memPause <= memBackoff) memPause = Math.min(99.0, memBackoff + 1.0);
        if (memPause > 99.0) memPause = 99.0;

        return new PluginConfig.Throttle(targetTps, autoScale, maxInflight, minInflight,
                startInflight, memBackoff, memPause);
    }
}
