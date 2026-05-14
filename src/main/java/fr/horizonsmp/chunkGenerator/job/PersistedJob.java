package fr.horizonsmp.chunkGenerator.job;

import fr.horizonsmp.chunkGenerator.shape.TraversalPattern;
import fr.horizonsmp.chunkGenerator.shape.ZoneDefinition;

import java.util.UUID;

public record PersistedJob(
        String worldName,
        ZoneDefinition zone,
        TraversalPattern pattern,
        long spiralIndex,
        long chunksDone,
        long totalChunks,
        JobStatus status,
        UUID launcherUuid,
        long createdAtEpochSeconds
) {
}
