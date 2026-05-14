package fr.horizonsmp.chunkGenerator.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Messages {

    private static final String FILE_NAME = "messages.yml";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        FileConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        try (InputStream in = plugin.getResource(FILE_NAME)) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                loaded.setDefaults(defaults);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read bundled messages defaults: " + e.getMessage());
        }
        this.config = loaded;
    }

    public Component get(String key) {
        return get(key, Map.of());
    }

    public Component get(String key, Map<String, String> placeholders) {
        return LEGACY.deserialize(getRaw(key, placeholders));
    }

    public String getRaw(String key) {
        return getRaw(key, Map.of());
    }

    public String getRaw(String key, Map<String, String> placeholders) {
        String raw = config != null ? config.getString(key) : null;
        if (raw == null) {
            return missingPlaceholder(key);
        }
        String result = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private static String missingPlaceholder(String key) {
        return "&c[missing message: " + key + "]";
    }

    @SuppressWarnings("unused")
    public static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }
}
