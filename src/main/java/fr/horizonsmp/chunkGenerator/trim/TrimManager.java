package fr.horizonsmp.chunkGenerator.trim;

import fr.horizonsmp.chunkGenerator.platform.PlatformAdapter;
import fr.horizonsmp.chunkGenerator.shape.ChunkCoord;
import fr.horizonsmp.chunkGenerator.shape.ZoneDefinition;
import fr.horizonsmp.chunkGenerator.trim.io.RegionFile;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class TrimManager {

    private static final long PREVIEW_TTL_MS = 60_000L;

    private final JavaPlugin plugin;
    private final PlatformAdapter platform;
    private final ExecutorService trimExecutor;
    private final ConcurrentMap<String, TrimJob> trims = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CachedPreview> previewCache = new ConcurrentHashMap<>();

    public TrimManager(JavaPlugin plugin, PlatformAdapter platform) {
        this.plugin = plugin;
        this.platform = platform;
        this.trimExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "chunkgen-trim");
            t.setDaemon(true);
            return t;
        });
    }

    public PreviewResult preview(World world, ZoneDefinition zone) {
        try {
            TrimPlan plan = buildPlan(world, zone);
            return PreviewResult.ok(plan);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to scan region files for trim preview on "
                    + world.getName() + ": " + e.getMessage());
            return PreviewResult.failure(e.getMessage());
        }
    }

    public void cacheForSender(UUID senderUuid, TrimPlan plan) {
        previewCache.put(senderUuid, new CachedPreview(plan, System.currentTimeMillis()));
    }

    public Optional<TrimPlan> takeCachedPlan(UUID senderUuid, String worldName, ZoneDefinition zone) {
        CachedPreview cached = previewCache.get(senderUuid);
        if (cached == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() - cached.cachedAtMs() > PREVIEW_TTL_MS) {
            previewCache.remove(senderUuid);
            return Optional.empty();
        }
        if (!cached.plan().worldName().equals(worldName) || !cached.plan().zone().equals(zone)) {
            return Optional.empty();
        }
        previewCache.remove(senderUuid);
        return Optional.of(cached.plan());
    }

    public StartResult start(World world, TrimPlan plan, UUID launcherUuid) {
        if (trims.containsKey(world.getName())) {
            return StartResult.failure("trim-already-running");
        }
        TrimJob job = new TrimJob(world, plan.zone(), plan, launcherUuid);
        job.wireRuntime(plugin, platform, trimExecutor);
        trims.put(world.getName(), job);
        job.start(() -> trims.remove(world.getName(), job));
        return StartResult.ok(job);
    }

    public boolean cancel(String worldName) {
        TrimJob job = trims.get(worldName);
        if (job == null) {
            return false;
        }
        job.cancel();
        return true;
    }

    public Optional<TrimJob> get(String worldName) {
        return Optional.ofNullable(trims.get(worldName));
    }

    public List<TrimJob> trims() {
        return new ArrayList<>(trims.values());
    }

    public boolean isActive(String worldName) {
        return trims.containsKey(worldName);
    }

    public void shutdown() {
        for (TrimJob job : trims.values()) {
            job.cancel();
        }
        trimExecutor.shutdown();
        try {
            if (!trimExecutor.awaitTermination(5L, TimeUnit.SECONDS)) {
                trimExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            trimExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private TrimPlan buildPlan(World world, ZoneDefinition zone) throws IOException {
        Path worldFolder = world.getWorldFolder().toPath();
        List<RegionFile.RegionEntry> regions = RegionFile.listRegions(worldFolder);
        List<RegionFile.RegionEntry> toDelete = new ArrayList<>();
        Map<RegionFile.RegionEntry, List<ChunkCoord>> headerZeros = new LinkedHashMap<>();
        long chunksAffected = 0L;
        for (RegionFile.RegionEntry entry : regions) {
            int rx = entry.rx();
            int rz = entry.rz();
            int minCx = rx << 5;
            int maxCx = minCx + 31;
            int minCz = rz << 5;
            int maxCz = minCz + 31;
            boolean anyInside = false;
            for (int cz = minCz; cz <= maxCz && !anyInside; cz++) {
                for (int cx = minCx; cx <= maxCx && !anyInside; cx++) {
                    if (zone.chunkIntersects(cx, cz)) {
                        anyInside = true;
                    }
                }
            }
            if (!anyInside) {
                List<ChunkCoord> present = RegionFile.presentChunks(entry.file(), rx, rz);
                toDelete.add(entry);
                chunksAffected += present.size();
            } else {
                List<ChunkCoord> present = RegionFile.presentChunks(entry.file(), rx, rz);
                List<ChunkCoord> outside = new ArrayList<>();
                for (ChunkCoord cc : present) {
                    if (!zone.chunkIntersects(cc.x(), cc.z())) {
                        outside.add(cc);
                    }
                }
                if (!outside.isEmpty()) {
                    headerZeros.put(entry, outside);
                    chunksAffected += outside.size();
                }
            }
        }
        long regionsAffected = (long) toDelete.size() + (long) headerZeros.size();
        return new TrimPlan(world.getName(), zone, toDelete, headerZeros, chunksAffected, regionsAffected);
    }

    private record CachedPreview(TrimPlan plan, long cachedAtMs) {
    }

    public record PreviewResult(boolean ok, String error, TrimPlan plan) {
        public static PreviewResult ok(TrimPlan plan) {
            return new PreviewResult(true, null, plan);
        }

        public static PreviewResult failure(String error) {
            return new PreviewResult(false, error, null);
        }
    }

    public record StartResult(boolean ok, String error, TrimJob job) {
        public static StartResult ok(TrimJob job) {
            return new StartResult(true, null, job);
        }

        public static StartResult failure(String error) {
            return new StartResult(false, error, null);
        }
    }
}
