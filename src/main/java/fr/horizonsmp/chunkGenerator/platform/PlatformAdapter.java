package fr.horizonsmp.chunkGenerator.platform;

import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

public interface PlatformAdapter {

    boolean isFolia();

    PlatformTask scheduleTickTask(Runnable runnable, long periodTicks);

    PlatformTask scheduleAsyncTask(Runnable runnable, long initialDelayMs, long periodMs);

    CompletableFuture<Void> loadChunkAsync(World world, int chunkX, int chunkZ);
}
