package fr.horizonsmp.chunkGenerator.platform;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Folia scheduler bridge using reflection so the plugin keeps a
 * pure Paper-API compile classpath while still scheduling on the
 * GlobalRegionScheduler / AsyncScheduler at runtime when Folia is
 * detected.
 */
public final class FoliaAdapter implements PlatformAdapter {

    private final Plugin plugin;
    private final Object globalRegionScheduler;
    private final Object asyncScheduler;
    private final Method globalRunAtFixedRate;
    private final Method globalExecute;
    private final Method asyncRunAtFixedRate;
    private final Method taskCancel;

    public FoliaAdapter(Plugin plugin) throws ReflectiveOperationException {
        this.plugin = plugin;
        Method getGlobal = Bukkit.class.getMethod("getGlobalRegionScheduler");
        Method getAsync = Bukkit.class.getMethod("getAsyncScheduler");
        this.globalRegionScheduler = getGlobal.invoke(null);
        this.asyncScheduler = getAsync.invoke(null);

        this.globalRunAtFixedRate = globalRegionScheduler.getClass()
                .getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
        this.globalExecute = globalRegionScheduler.getClass()
                .getMethod("execute", Plugin.class, Runnable.class);
        this.asyncRunAtFixedRate = asyncScheduler.getClass()
                .getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

        Class<?> taskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
        this.taskCancel = taskClass.getMethod("cancel");
    }

    @Override
    public boolean isFolia() {
        return true;
    }

    @Override
    public PlatformTask scheduleTickTask(Runnable runnable, long periodTicks) {
        try {
            Consumer<Object> wrapped = ignored -> runnable.run();
            Object scheduled = globalRunAtFixedRate.invoke(globalRegionScheduler, plugin, wrapped, 1L, periodTicks);
            return wrapTask(scheduled);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to schedule tick task on Folia", e);
        }
    }

    @Override
    public PlatformTask scheduleAsyncTask(Runnable runnable, long initialDelayMs, long periodMs) {
        try {
            Consumer<Object> wrapped = ignored -> runnable.run();
            long initial = Math.max(1L, initialDelayMs);
            long period = Math.max(1L, periodMs);
            Object scheduled = asyncRunAtFixedRate.invoke(asyncScheduler, plugin, wrapped, initial, period, TimeUnit.MILLISECONDS);
            return wrapTask(scheduled);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to schedule async task on Folia", e);
        }
    }

    @Override
    public CompletableFuture<Void> loadChunkAsync(World world, int chunkX, int chunkZ) {
        return world.getChunkAtAsync(chunkX, chunkZ, true).thenApply(c -> null);
    }

    @Override
    public void runOnMain(Runnable runnable) {
        try {
            globalExecute.invoke(globalRegionScheduler, plugin, runnable);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to dispatch to Folia global region scheduler", e);
        }
    }

    private PlatformTask wrapTask(Object scheduled) {
        return () -> {
            try {
                taskCancel.invoke(scheduled);
            } catch (ReflectiveOperationException ignored) {
            }
        };
    }

    public static boolean detected() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
