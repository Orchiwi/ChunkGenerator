package fr.horizonsmp.chunkGenerator.monitoring;

public record PerformanceSnapshot(
        double tps,
        double cpuPercent,
        long usedRamMb,
        long maxRamMb
) {

    public static PerformanceSnapshot empty() {
        return new PerformanceSnapshot(20.0, 0.0, 0L, 0L);
    }

    public double heapUsedFraction() {
        return maxRamMb <= 0L ? 0.0 : (double) usedRamMb / (double) maxRamMb;
    }
}
