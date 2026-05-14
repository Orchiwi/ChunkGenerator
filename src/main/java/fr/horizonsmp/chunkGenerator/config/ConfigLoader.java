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

        ConfigurationSection throttleSection = section(cfg, "throttle");
        PluginConfig.Throttle throttle = readThrottle(throttleSection);

        ConfigurationSection displaySection = section(cfg, "display");
        ConfigurationSection bossSection = section(displaySection, "bossbar");
        ConfigurationSection actionSection = section(displaySection, "actionbar");
        ConfigurationSection consoleSection = section(displaySection, "console");
        PluginConfig.Display display = new PluginConfig.Display(
                new PluginConfig.Display.BossBar(
                        bossSection.getBoolean("enabled", true),
                        bossSection.getString("color", "BLUE")
                ),
                new PluginConfig.Display.ActionBar(
                        actionSection.getBoolean("enabled", true)
                ),
                new PluginConfig.Display.Console(
                        consoleSection.getBoolean("enabled", true),
                        consoleSection.getLong("interval-seconds", 30L)
                )
        );

        ConfigurationSection monitoringSection = section(cfg, "monitoring");
        PluginConfig.Monitoring monitoring = new PluginConfig.Monitoring(
                monitoringSection.getLong("poll-interval-ms", 1000L)
        );

        ConfigurationSection persistenceSection = section(cfg, "persistence");
        PluginConfig.Persistence persistence = new PluginConfig.Persistence(
                persistenceSection.getBoolean("auto-resume-on-startup", true),
                persistenceSection.getLong("save-throttle-seconds", 5L),
                persistenceSection.getLong("save-throttle-chunks", 1000L)
        );

        return new PluginConfig(throttle, display, monitoring, persistence);
    }

    private static ConfigurationSection section(ConfigurationSection cfg, String path) {
        ConfigurationSection s = cfg.getConfigurationSection(path);
        return s != null ? s : cfg.createSection(path);
    }

    private static PluginConfig.Throttle readThrottle(ConfigurationSection s) {
        double targetTps = s.getDouble("target-tps", 18.5);
        boolean autoScale = s.getBoolean("auto-scale", true);
        int rawMax = s.getInt("max-inflight", 0);
        int rawMin = s.getInt("min-inflight", 8);
        int rawStart = s.getInt("start-inflight", 0);
        double memBackoff = s.getDouble("memory-backoff-pct", 85.0);
        double memPause = s.getDouble("memory-pause-pct", 92.0);

        int cores = Runtime.getRuntime().availableProcessors();
        long heapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);

        int maxInflight = rawMax > 0
                ? rawMax
                : autoScale
                        ? (int) Math.min(2000L, Math.max(256L, heapMb / 5L))
                        : 256;
        int startInflight = rawStart > 0
                ? rawStart
                : autoScale
                        ? Math.max(64, cores * 32)
                        : 64;
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
