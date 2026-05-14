package fr.horizonsmp.chunkGenerator.job;

import fr.horizonsmp.chunkGenerator.config.PluginConfig;
import fr.horizonsmp.chunkGenerator.monitoring.PerformanceSnapshot;
import fr.horizonsmp.chunkGenerator.monitoring.ThrottleController;
import fr.horizonsmp.chunkGenerator.platform.PlatformAdapter;
import fr.horizonsmp.chunkGenerator.platform.PlatformTask;
import fr.horizonsmp.chunkGenerator.shape.ChunkCoord;
import fr.horizonsmp.chunkGenerator.shape.ChunkSpiralIterator;
import fr.horizonsmp.chunkGenerator.shape.ZoneDefinition;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class GenerationJob {

    private final World world;
    private final String worldName;
    private final ZoneDefinition zone;
    private final UUID launcherUuid;
    private final long createdAtEpochSeconds;
    private final ChunkSpiralIterator iterator;
    private final AtomicLong chunksDone;
    private final long totalChunks;
    private final AtomicReference<JobStatus> status = new AtomicReference<>(JobStatus.PAUSED);
    private final AtomicInteger inflight = new AtomicInteger();

    private Plugin plugin;
    private PlatformAdapter platform;
    private ThrottleController throttle;
    private JobStorage storage;
    private PluginConfig.Persistence persistenceCfg;
    private PlatformTask tickTask;

    private volatile long lastSavedAtMs;
    private volatile long lastSavedChunkCount;

    private volatile long speedSampleTimeMs;
    private volatile long speedSampleChunks;
    private volatile double cachedChunksPerSecond;

    public GenerationJob(World world,
                         ZoneDefinition zone,
                         long initialChunksDone,
                         long totalChunks,
                         long initialSpiralIndex,
                         UUID launcherUuid,
                         long createdAtEpochSeconds) {
        this.world = world;
        this.worldName = world.getName();
        this.zone = zone;
        this.launcherUuid = launcherUuid;
        this.createdAtEpochSeconds = createdAtEpochSeconds;
        this.iterator = new ChunkSpiralIterator(zone);
        if (initialSpiralIndex > 0) {
            this.iterator.seek(initialSpiralIndex);
        }
        this.chunksDone = new AtomicLong(initialChunksDone);
        this.totalChunks = totalChunks;
    }

    public void wireRuntime(Plugin plugin,
                            PlatformAdapter platform,
                            ThrottleController throttle,
                            JobStorage storage,
                            PluginConfig.Persistence persistenceCfg) {
        this.plugin = plugin;
        this.platform = platform;
        this.throttle = throttle;
        this.storage = storage;
        this.persistenceCfg = persistenceCfg;
    }

    public synchronized void start() {
        JobStatus current = status.get();
        if (current == JobStatus.COMPLETED || current == JobStatus.CANCELLED) {
            return;
        }
        if (iterator.isDone()) {
            onComplete();
            return;
        }
        status.set(JobStatus.RUNNING);
        if (tickTask == null) {
            tickTask = platform.scheduleTickTask(this::tick, 1L);
        }
        persistNow();
    }

    public synchronized void pause() {
        if (status.get() == JobStatus.COMPLETED || status.get() == JobStatus.CANCELLED) {
            return;
        }
        status.set(JobStatus.PAUSED);
        cancelTickTask();
        persistNow();
    }

    public synchronized void cancel() {
        status.set(JobStatus.CANCELLED);
        cancelTickTask();
        storage.delete(worldName);
    }

    private void cancelTickTask() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void tick() {
        if (status.get() != JobStatus.RUNNING) {
            return;
        }
        int slack = throttle.slack(inflight.get());
        for (int i = 0; i < slack; i++) {
            Optional<ChunkCoord> next = iterator.next();
            if (next.isEmpty()) {
                onComplete();
                return;
            }
            ChunkCoord cc = next.get();
            inflight.incrementAndGet();
            platform.loadChunkAsync(world, cc.x(), cc.z()).whenComplete((unused, ex) -> {
                inflight.decrementAndGet();
                if (ex != null) {
                    plugin.getLogger().warning("Chunk load failed for " + worldName
                            + " (" + cc.x() + "," + cc.z() + "): " + ex.getMessage());
                    return;
                }
                chunksDone.incrementAndGet();
            });
        }
        maybeSave();
    }

    private synchronized void onComplete() {
        status.set(JobStatus.COMPLETED);
        cancelTickTask();
        storage.delete(worldName);
        plugin.getLogger().info("[" + worldName + "] Chunk generation completed ("
                + chunksDone.get() + "/" + totalChunks + " chunks).");
    }

    public void refreshSpeedSample() {
        long now = System.currentTimeMillis();
        long done = chunksDone.get();
        if (speedSampleTimeMs > 0L) {
            double dt = (now - speedSampleTimeMs) / 1000.0;
            if (dt > 0.0) {
                double instant = Math.max(0.0, (done - speedSampleChunks) / dt);
                cachedChunksPerSecond = instant * 0.6 + cachedChunksPerSecond * 0.4;
            }
        }
        speedSampleTimeMs = now;
        speedSampleChunks = done;
    }

    public int inflight() {
        return inflight.get();
    }

    public double chunksPerSecond() {
        return cachedChunksPerSecond;
    }

    public long etaSeconds() {
        double speed = chunksPerSecond();
        long remaining = totalChunks - chunksDone.get();
        if (speed < 0.01 || remaining <= 0L) {
            return -1L;
        }
        double seconds = remaining / speed;
        if (seconds < 0.0 || seconds > 365.0 * 24.0 * 3600.0) {
            return -1L;
        }
        return (long) seconds;
    }

    private void maybeSave() {
        if (persistenceCfg == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long doneNow = chunksDone.get();
        boolean byTime = now - lastSavedAtMs >= persistenceCfg.saveThrottleSeconds() * 1000L;
        boolean byCount = doneNow - lastSavedChunkCount >= persistenceCfg.saveThrottleChunks();
        if (byTime || byCount) {
            persistNow();
        }
    }

    public void persistNow() {
        if (storage == null) {
            return;
        }
        JobStatus persistedStatus = status.get() == JobStatus.RUNNING ? JobStatus.RUNNING : JobStatus.PAUSED;
        lastSavedAtMs = System.currentTimeMillis();
        lastSavedChunkCount = chunksDone.get();
        storage.save(new PersistedJob(
                worldName, zone, iterator.index(), chunksDone.get(), totalChunks,
                persistedStatus, launcherUuid, createdAtEpochSeconds
        ));
    }

    public JobSnapshot snapshot(PerformanceSnapshot perf) {
        int target = throttle != null ? throttle.inflightTarget() : 0;
        return new JobSnapshot(
                worldName,
                status.get(),
                zone.shape(),
                zone.centerBlockX(),
                zone.centerBlockZ(),
                zone.halfWidthBlocks(),
                zone.halfLengthBlocks(),
                chunksDone.get(),
                totalChunks,
                chunksPerSecond(),
                etaSeconds(),
                perf.tps(),
                perf.cpuPercent(),
                perf.usedRamMb(),
                perf.maxRamMb(),
                launcherUuid,
                inflight.get(),
                target
        );
    }

    public String worldName() {
        return worldName;
    }

    public World world() {
        return world;
    }

    public ZoneDefinition zone() {
        return zone;
    }

    public UUID launcherUuid() {
        return launcherUuid;
    }

    public JobStatus status() {
        return status.get();
    }

    public long chunksDone() {
        return chunksDone.get();
    }

    public long totalChunks() {
        return totalChunks;
    }

    public long spiralIndex() {
        return iterator.index();
    }
}
