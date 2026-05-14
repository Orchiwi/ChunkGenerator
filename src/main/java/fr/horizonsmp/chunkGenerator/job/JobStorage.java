package fr.horizonsmp.chunkGenerator.job;

import fr.horizonsmp.chunkGenerator.shape.TraversalPattern;
import fr.horizonsmp.chunkGenerator.shape.ZoneDefinition;
import fr.horizonsmp.chunkGenerator.shape.ZoneShape;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JobStorage {

    private static final String DIRECTORY = "jobs";

    private final JavaPlugin plugin;
    private final Path jobsDir;
    private final Logger logger;

    public JobStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.jobsDir = plugin.getDataFolder().toPath().resolve(DIRECTORY);
        this.logger = plugin.getLogger();
    }

    public synchronized void ensureDirectory() {
        try {
            Files.createDirectories(jobsDir);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create jobs directory at " + jobsDir, e);
        }
    }

    public synchronized List<PersistedJob> loadAll() {
        ensureDirectory();
        List<PersistedJob> out = new ArrayList<>();
        File[] files = jobsDir.toFile().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return out;
        }
        for (File file : files) {
            try {
                PersistedJob loaded = readFile(file);
                if (loaded != null) {
                    out.add(loaded);
                }
            } catch (RuntimeException e) {
                logger.warning("Failed to load job file " + file.getName() + ": " + e.getMessage());
            }
        }
        return out;
    }

    public synchronized void save(PersistedJob job) {
        ensureDirectory();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", job.worldName());
        yaml.set("shape", job.zone().shape().name());
        yaml.set("center-x", job.zone().centerBlockX());
        yaml.set("center-z", job.zone().centerBlockZ());
        yaml.set("half-width", job.zone().halfWidthBlocks());
        yaml.set("half-length", job.zone().halfLengthBlocks());
        yaml.set("pattern", job.pattern().name());
        yaml.set("spiral-index", job.spiralIndex());
        yaml.set("chunks-done", job.chunksDone());
        yaml.set("total-chunks", job.totalChunks());
        yaml.set("status", job.status().name());
        yaml.set("launcher-uuid", job.launcherUuid() != null ? job.launcherUuid().toString() : null);
        yaml.set("created-at", job.createdAtEpochSeconds());

        Path target = jobsDir.resolve(sanitize(job.worldName()) + ".yml");
        Path temp;
        try {
            temp = Files.createTempFile(jobsDir, ".tmp-" + sanitize(job.worldName()) + "-", ".yml");
            yaml.save(temp.toFile());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to stage job file for " + job.worldName(), e);
            return;
        }
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ioEx) {
                logger.log(Level.WARNING, "Non-atomic move also failed for " + job.worldName(), ioEx);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to persist job for " + job.worldName(), e);
        }
    }

    public synchronized void delete(String worldName) {
        Path target = jobsDir.resolve(sanitize(worldName) + ".yml");
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to delete job file for " + worldName, e);
        }
    }

    private PersistedJob readFile(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String worldName = yaml.getString("world");
        if (worldName == null) {
            return null;
        }
        ZoneShape shape = ZoneShape.fromString(yaml.getString("shape")).orElse(ZoneShape.SQUARE);
        int cx = yaml.getInt("center-x");
        int cz = yaml.getInt("center-z");
        int halfW = yaml.getInt("half-width");
        int halfL = yaml.getInt("half-length");
        ZoneDefinition zone = new ZoneDefinition(shape, cx, cz, halfW, halfL);
        TraversalPattern pattern = TraversalPattern.fromString(yaml.getString("pattern"))
                .orElse(TraversalPattern.CENTER);
        long spiralIndex = yaml.getLong("spiral-index");
        long chunksDone = yaml.getLong("chunks-done");
        long total = yaml.getLong("total-chunks");
        JobStatus status;
        try {
            status = JobStatus.valueOf(yaml.getString("status", "PAUSED").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            status = JobStatus.PAUSED;
        }
        UUID launcher = null;
        String launcherRaw = yaml.getString("launcher-uuid");
        if (launcherRaw != null && !launcherRaw.isBlank()) {
            try {
                launcher = UUID.fromString(launcherRaw);
            } catch (IllegalArgumentException ignored) {
            }
        }
        long createdAt = yaml.getLong("created-at");
        return new PersistedJob(worldName, zone, pattern, spiralIndex, chunksDone, total, status,
                launcher, createdAt);
    }

    private static String sanitize(String worldName) {
        return worldName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
