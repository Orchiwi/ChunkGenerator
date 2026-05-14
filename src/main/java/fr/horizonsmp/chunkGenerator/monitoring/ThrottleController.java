package fr.horizonsmp.chunkGenerator.monitoring;

import fr.horizonsmp.chunkGenerator.config.PluginConfig;

import java.util.concurrent.atomic.AtomicReference;

public final class ThrottleController {

    private final AtomicReference<PluginConfig.Throttle> settings;
    private final PerformanceMonitor monitor;

    private volatile double chunksPerTick;
    private double accumulator;
    private long ticksSinceLastEval;

    public ThrottleController(PluginConfig.Throttle initial, PerformanceMonitor monitor) {
        this.settings = new AtomicReference<>(initial);
        this.monitor = monitor;
        this.chunksPerTick = Math.max(1.0, initial.minChunksPerTick());
    }

    public void updateSettings(PluginConfig.Throttle next) {
        settings.set(next);
        clampChunksPerTick();
    }

    public double chunksPerTick() {
        return chunksPerTick;
    }

    public int consumeChunksThisTick() {
        accumulator += chunksPerTick;
        int whole = (int) Math.floor(accumulator);
        accumulator -= whole;

        ticksSinceLastEval++;
        if (ticksSinceLastEval >= 20) {
            ticksSinceLastEval = 0;
            adjust();
        }
        return whole;
    }

    private void adjust() {
        PluginConfig.Throttle cfg = settings.get();
        double tps = monitor.snapshot().tps();
        if (tps >= cfg.targetTps() + 0.5) {
            chunksPerTick = Math.min(cfg.maxChunksPerTick(), chunksPerTick * 1.2);
        } else if (tps < cfg.targetTps()) {
            chunksPerTick = Math.max(cfg.minChunksPerTick(), chunksPerTick * 0.8);
        }
        clampChunksPerTick();
    }

    private void clampChunksPerTick() {
        PluginConfig.Throttle cfg = settings.get();
        if (chunksPerTick < cfg.minChunksPerTick()) {
            chunksPerTick = cfg.minChunksPerTick();
        } else if (chunksPerTick > cfg.maxChunksPerTick()) {
            chunksPerTick = cfg.maxChunksPerTick();
        }
    }
}
