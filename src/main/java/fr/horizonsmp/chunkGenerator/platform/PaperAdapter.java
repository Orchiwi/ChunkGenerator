package fr.horizonsmp.chunkGenerator.platform;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;

public final class PaperAdapter implements PlatformAdapter {

    private final Plugin plugin;

    public PaperAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public PlatformTask scheduleTickTask(Runnable runnable, long periodTicks) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, 0L, periodTicks);
        return task::cancel;
    }

    @Override
    public PlatformTask scheduleAsyncTask(Runnable runnable, long initialDelayMs, long periodMs) {
        long initialDelayTicks = Math.max(1L, initialDelayMs / 50L);
        long periodTicks = Math.max(1L, periodMs / 50L);
        BukkitTask task = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, runnable, initialDelayTicks, periodTicks);
        return task::cancel;
    }

    @Override
    public CompletableFuture<Void> loadChunkAsync(World world, int chunkX, int chunkZ) {
        return world.getChunkAtAsync(chunkX, chunkZ, true).thenApply(c -> null);
    }

    @Override
    public void runOnMain(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }
}
