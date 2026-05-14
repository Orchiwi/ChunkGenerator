package fr.horizonsmp.chunkGenerator.monitoring;

import com.sun.management.OperatingSystemMXBean;
import fr.horizonsmp.chunkGenerator.platform.PlatformAdapter;
import fr.horizonsmp.chunkGenerator.platform.PlatformTask;
import org.bukkit.Bukkit;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicReference;

public final class PerformanceMonitor {

    private final PlatformAdapter platform;
    private final long pollIntervalMs;
    private final OperatingSystemMXBean osBean;
    private final AtomicReference<PerformanceSnapshot> latest = new AtomicReference<>(PerformanceSnapshot.empty());

    private PlatformTask task;

    public PerformanceMonitor(PlatformAdapter platform, long pollIntervalMs) {
        this.platform = platform;
        this.pollIntervalMs = pollIntervalMs;
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = platform.scheduleAsyncTask(this::sample, pollIntervalMs, pollIntervalMs);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public PerformanceSnapshot snapshot() {
        return latest.get();
    }

    private void sample() {
        double tps = readTps();
        double cpuLoad = osBean.getProcessCpuLoad();
        double cpuPct = cpuLoad < 0 ? 0.0 : cpuLoad * 100.0;

        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
        long max = rt.maxMemory() / (1024L * 1024L);

        latest.set(new PerformanceSnapshot(tps, cpuPct, used, max));
    }

    private static double readTps() {
        double[] tps = Bukkit.getServer().getTPS();
        return tps.length > 0 ? Math.min(tps[0], 20.0) : 20.0;
    }
}
