package fr.horizonsmp.chunkGenerator.trim;

import fr.horizonsmp.chunkGenerator.platform.PlatformAdapter;
import fr.horizonsmp.chunkGenerator.shape.ChunkCoord;
import fr.horizonsmp.chunkGenerator.shape.ZoneDefinition;
import fr.horizonsmp.chunkGenerator.trim.io.RegionFile;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class TrimJob {

    private final World world;
    private final String worldName;
    private final ZoneDefinition zone;
    private final TrimPlan plan;
    private final UUID launcherUuid;
    private final long createdAtEpochSeconds;
    private final AtomicLong chunksProcessed = new AtomicLong();
    private final AtomicReference<TrimStatus> status = new AtomicReference<>(TrimStatus.PENDING);

    private Plugin plugin;
    private PlatformAdapter platform;
    private ExecutorService executor;

    public TrimJob(World world, ZoneDefinition zone, TrimPlan plan, UUID launcherUuid) {
        this.world = world;
        this.worldName = world.getName();
        this.zone = zone;
        this.plan = plan;
        this.launcherUuid = launcherUuid;
        this.createdAtEpochSeconds = Instant.now().getEpochSecond();
    }

    public void wireRuntime(Plugin plugin, PlatformAdapter platform, ExecutorService executor) {
        this.plugin = plugin;
        this.platform = platform;
        this.executor = executor;
    }

    public synchronized void start(Runnable onComplete) {
        if (status.get() != TrimStatus.PENDING) {
            return;
        }
        status.set(TrimStatus.RUNNING);
        CompletableFuture.runAsync(this::execute, executor)
                .whenComplete((unused, ex) -> {
                    if (ex != null) {
                        status.set(TrimStatus.FAILED);
                        plugin.getLogger().warning("Trim for " + worldName + " failed: " + ex.getMessage());
                    } else {
                        status.compareAndSet(TrimStatus.RUNNING, TrimStatus.COMPLETED);
                    }
                    onComplete.run();
                });
    }

    public void cancel() {
        TrimStatus current = status.get();
        if (current == TrimStatus.RUNNING || current == TrimStatus.PENDING) {
            status.set(TrimStatus.CANCELLED);
        }
    }

    private void execute() {
        try {
            for (RegionFile.RegionEntry entry : plan.regionsToDelete()) {
                if (status.get() != TrimStatus.RUNNING) {
                    return;
                }
                long present = unloadAndDeleteFile(entry);
                chunksProcessed.addAndGet(present);
            }
            for (Map.Entry<RegionFile.RegionEntry, List<ChunkCoord>> entry : plan.headerZeros().entrySet()) {
                if (status.get() != TrimStatus.RUNNING) {
                    return;
                }
                unloadAndZeroHeaders(entry.getKey(), entry.getValue());
                chunksProcessed.addAndGet(entry.getValue().size());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private long unloadAndDeleteFile(RegionFile.RegionEntry entry) throws IOException {
        long present = RegionFile.presentChunks(entry.file(), entry.rx(), entry.rz()).size();
        List<ChunkCoord> all = new ArrayList<>(1024);
        for (int z = 0; z < RegionFile.CHUNKS_PER_REGION_AXIS; z++) {
            for (int x = 0; x < RegionFile.CHUNKS_PER_REGION_AXIS; x++) {
                all.add(new ChunkCoord((entry.rx() << 5) | x, (entry.rz() << 5) | z));
            }
        }
        unloadChunksOnMain(all);
        RegionFile.deleteFile(entry.file());
        return present;
    }

    private void unloadAndZeroHeaders(RegionFile.RegionEntry entry, List<ChunkCoord> chunks) throws IOException {
        unloadChunksOnMain(chunks);
        RegionFile.zeroHeaders(entry.file(), chunks);
    }

    private void unloadChunksOnMain(List<ChunkCoord> chunks) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        platform.runOnMain(() -> {
            try {
                for (ChunkCoord cc : chunks) {
                    if (world.isChunkLoaded(cc.x(), cc.z())) {
                        world.unloadChunk(cc.x(), cc.z(), false);
                    }
                }
            } finally {
                future.complete(null);
            }
        });
        future.join();
    }

    public TrimSnapshot snapshot() {
        return new TrimSnapshot(
                worldName,
                status.get(),
                chunksProcessed.get(),
                plan.chunksAffected(),
                plan.regionFilesAffected(),
                launcherUuid,
                createdAtEpochSeconds
        );
    }

    public String worldName() {
        return worldName;
    }

    public TrimStatus status() {
        return status.get();
    }

    public ZoneDefinition zone() {
        return zone;
    }

    public TrimPlan plan() {
        return plan;
    }

    public long chunksProcessed() {
        return chunksProcessed.get();
    }

    public long chunksAffected() {
        return plan.chunksAffected();
    }

    public UUID launcherUuid() {
        return launcherUuid;
    }
}
