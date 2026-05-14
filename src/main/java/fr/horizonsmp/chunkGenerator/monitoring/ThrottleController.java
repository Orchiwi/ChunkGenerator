package fr.horizonsmp.chunkGenerator.monitoring;

import fr.horizonsmp.chunkGenerator.config.PluginConfig;

import java.util.concurrent.atomic.AtomicReference;

public final class ThrottleController {

    private static final int EVAL_INTERVAL_TICKS = 20;

    private final AtomicReference<PluginConfig.Throttle> settings;
    private final PerformanceMonitor monitor;

    private volatile int inflightTarget;
    private volatile boolean memoryPaused;
    private long ticksSinceLastEval;

    public ThrottleController(PluginConfig.Throttle initial, PerformanceMonitor monitor) {
        this.settings = new AtomicReference<>(initial);
        this.monitor = monitor;
        this.inflightTarget = clamp(initial.startInflight(), initial.minInflight(), initial.maxInflight());
    }

    public void updateSettings(PluginConfig.Throttle next) {
        settings.set(next);
        inflightTarget = clamp(inflightTarget, next.minInflight(), next.maxInflight());
    }

    public int inflightTarget() {
        return inflightTarget;
    }

    public boolean memoryPaused() {
        return memoryPaused;
    }

    public int slack(int currentInflight) {
        ticksSinceLastEval++;
        if (ticksSinceLastEval >= EVAL_INTERVAL_TICKS) {
            ticksSinceLastEval = 0;
            adjust();
        }
        if (memoryPaused) {
            return 0;
        }
        return Math.max(0, inflightTarget - currentInflight);
    }

    private void adjust() {
        PluginConfig.Throttle cfg = settings.get();
        PerformanceSnapshot perf = monitor.snapshot();
        double heap = perf.heapUsedFraction();

        double pauseFrac = cfg.memoryPausePercent() / 100.0;
        double backoffFrac = cfg.memoryBackoffPercent() / 100.0;

        if (heap >= pauseFrac) {
            memoryPaused = true;
            inflightTarget = cfg.minInflight();
            return;
        }
        memoryPaused = false;

        if (heap >= backoffFrac) {
            inflightTarget = clamp((int) Math.floor(inflightTarget * 0.7),
                    cfg.minInflight(), cfg.maxInflight());
            return;
        }

        double margin = perf.tps() - cfg.targetTps();
        int next;
        if (margin >= 1.0) {
            next = (int) Math.floor(inflightTarget * 1.5) + 8;
        } else if (margin >= 0.5) {
            next = (int) Math.floor(inflightTarget * 1.2) + 4;
        } else if (margin >= 0.0) {
            next = inflightTarget;
        } else if (margin >= -0.5) {
            next = (int) Math.floor(inflightTarget * 0.85);
        } else {
            next = (int) Math.floor(inflightTarget * 0.6);
        }
        inflightTarget = clamp(next, cfg.minInflight(), cfg.maxInflight());
    }

    private static int clamp(int value, int min, int max) {
        if (min > max) {
            min = max;
        }
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
