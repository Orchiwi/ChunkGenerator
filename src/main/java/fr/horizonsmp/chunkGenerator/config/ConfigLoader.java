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
        PluginConfig.Throttle throttle = new PluginConfig.Throttle(
                throttleSection.getDouble("target-tps", 18.5),
                throttleSection.getDouble("max-chunks-per-tick", 50.0),
                throttleSection.getDouble("min-chunks-per-tick", 0.5)
        );

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
}
