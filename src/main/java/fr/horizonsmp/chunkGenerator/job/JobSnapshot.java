package fr.horizonsmp.chunkGenerator.job;

import fr.horizonsmp.chunkGenerator.shape.ZoneShape;

import java.util.UUID;

public record JobSnapshot(
        String worldName,
        JobStatus status,
        ZoneShape shape,
        int centerBlockX,
        int centerBlockZ,
        int halfWidthBlocks,
        int halfLengthBlocks,
        long chunksDone,
        long totalChunks,
        double chunksPerSecond,
        long etaSeconds,
        double tps,
        double cpuPercent,
        long usedRamMb,
        long maxRamMb,
        UUID launcherUuid,
        int inflight,
        int inflightTarget
) {

    public double progressPercent() {
        return totalChunks <= 0 ? 0.0 : (chunksDone / (double) totalChunks) * 100.0;
    }
}
