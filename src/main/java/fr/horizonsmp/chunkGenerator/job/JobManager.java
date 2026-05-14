package fr.horizonsmp.chunkGenerator.job;

import fr.horizonsmp.chunkGenerator.config.PluginConfig;
import fr.horizonsmp.chunkGenerator.monitoring.PerformanceMonitor;
import fr.horizonsmp.chunkGenerator.monitoring.PerformanceSnapshot;
import fr.horizonsmp.chunkGenerator.monitoring.ThrottleController;
import fr.horizonsmp.chunkGenerator.platform.PlatformAdapter;
import fr.horizonsmp.chunkGenerator.platform.PlatformTask;
import fr.horizonsmp.chunkGenerator.shape.TraversalPattern;
import fr.horizonsmp.chunkGenerator.shape.ZoneDefinition;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public final class JobManager {

    private final JavaPlugin plugin;
    private final PlatformAdapter platform;
    private final ThrottleController throttle;
    private final PerformanceMonitor monitor;
    private final JobStorage storage;
    private final Supplier<PluginConfig> configSupplier;

    private final ConcurrentMap<String, GenerationJob> jobs = new ConcurrentHashMap<>();
    private PlatformTask sampler;

    public JobManager(JavaPlugin plugin,
                      PlatformAdapter platform,
                      ThrottleController throttle,
                      PerformanceMonitor monitor,
                      JobStorage storage,
                      Supplier<PluginConfig> configSupplier) {
        this.plugin = plugin;
        this.platform = platform;
        this.throttle = throttle;
        this.monitor = monitor;
        this.storage = storage;
        this.configSupplier = configSupplier;
    }

    public void start() {
        sampler = platform.scheduleAsyncTask(this::sampleAll, 1000L, 1000L);
    }

    public void shutdown() {
        if (sampler != null) {
            sampler.cancel();
            sampler = null;
        }
        for (GenerationJob job : jobs.values()) {
            if (job.status() == JobStatus.RUNNING) {
                job.pause();
            } else {
                job.persistNow();
            }
        }
        jobs.clear();
    }

    public Optional<GenerationJob> get(String worldName) {
        return Optional.ofNullable(jobs.get(worldName));
    }

    public List<GenerationJob> jobs() {
        return new ArrayList<>(jobs.values());
    }

    public StartResult startJob(World world, ZoneDefinition zone, TraversalPattern pattern, UUID launcherUuid) {
        if (jobs.containsKey(world.getName())) {
            return StartResult.alreadyRunning(jobs.get(world.getName()));
        }
        long total;
        try {
            total = pattern.iterator(zone).countMatching();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Failed to compute total chunks for "
                    + world.getName() + ": " + e.getMessage());
            return StartResult.failure(e.getMessage());
        }
        if (total <= 0L) {
            return StartResult.failure("zone-empty");
        }
        GenerationJob job = new GenerationJob(world, zone, pattern, 0L, total, 0L,
                launcherUuid, Instant.now().getEpochSecond());
        job.wireRuntime(plugin, platform, throttle, storage, configSupplier.get().persistence());
        jobs.put(world.getName(), job);
        job.start();
        return StartResult.started(job);
    }

    public boolean stop(String worldName) {
        GenerationJob job = jobs.get(worldName);
        if (job == null || job.status() != JobStatus.RUNNING) {
            return false;
        }
        job.pause();
        return true;
    }

    public ResumeResult resume(String worldName) {
        GenerationJob job = jobs.get(worldName);
        if (job == null) {
            return ResumeResult.failure("no-job");
        }
        JobStatus current = job.status();
        if (current == JobStatus.RUNNING) {
            return ResumeResult.failure("already-running");
        }
        if (current == JobStatus.COMPLETED || current == JobStatus.CANCELLED) {
            return ResumeResult.failure("terminal");
        }
        job.start();
        return ResumeResult.ok(job);
    }

    public boolean cancel(String worldName) {
        GenerationJob job = jobs.remove(worldName);
        if (job == null) {
            storage.delete(worldName);
            return false;
        }
        job.cancel();
        return true;
    }

    public void resumePersisted(List<PersistedJob> persisted) {
        for (PersistedJob p : persisted) {
            World world = Bukkit.getWorld(p.worldName());
            if (world == null) {
                plugin.getLogger().warning("Skipping persisted job for unknown world: " + p.worldName());
                continue;
            }
            GenerationJob job = new GenerationJob(world, p.zone(), p.pattern(), p.chunksDone(),
                    p.totalChunks(), p.spiralIndex(), p.launcherUuid(), p.createdAtEpochSeconds());
            job.wireRuntime(plugin, platform, throttle, storage, configSupplier.get().persistence());
            jobs.put(p.worldName(), job);
            if (p.status() == JobStatus.RUNNING && configSupplier.get().persistence().autoResumeOnStartup()) {
                job.start();
                plugin.getLogger().info("Resumed job for world " + p.worldName()
                        + " at " + p.chunksDone() + "/" + p.totalChunks() + " chunks.");
            }
        }
    }

    public PerformanceSnapshot performanceSnapshot() {
        return monitor.snapshot();
    }

    public JobSnapshot snapshotOf(GenerationJob job) {
        return job.snapshot(monitor.snapshot());
    }

    private void sampleAll() {
        for (GenerationJob job : jobs.values()) {
            job.refreshSpeedSample();
            if (job.status() == JobStatus.COMPLETED) {
                jobs.remove(job.worldName());
            }
        }
    }

    public record StartResult(boolean ok, String error, GenerationJob job) {
        public static StartResult started(GenerationJob job) {
            return new StartResult(true, null, job);
        }

        public static StartResult alreadyRunning(GenerationJob job) {
            return new StartResult(false, "already-running", job);
        }

        public static StartResult failure(String error) {
            return new StartResult(false, error, null);
        }
    }

    public record ResumeResult(boolean ok, String error, GenerationJob job) {
        public static ResumeResult ok(GenerationJob job) {
            return new ResumeResult(true, null, job);
        }

        public static ResumeResult failure(String error) {
            return new ResumeResult(false, error, null);
        }
    }
}
