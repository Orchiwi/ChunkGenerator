package fr.horizonsmp.chunkGenerator.trim;

import java.util.UUID;

public record TrimSnapshot(
        String worldName,
        TrimStatus status,
        long chunksProcessed,
        long totalChunks,
        long regionFilesAffected,
        UUID launcherUuid,
        long createdAtEpochSeconds
) {

    public double progressPercent() {
        return totalChunks <= 0L ? 0.0 : (chunksProcessed / (double) totalChunks) * 100.0;
    }
}
