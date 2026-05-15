package fr.horizonsmp.chunkGenerator.trim;

import fr.horizonsmp.chunkGenerator.shape.ChunkCoord;
import fr.horizonsmp.chunkGenerator.shape.ZoneDefinition;
import fr.horizonsmp.chunkGenerator.trim.io.RegionFile;

import java.util.List;
import java.util.Map;

public record TrimPlan(
        String worldName,
        ZoneDefinition zone,
        List<RegionFile.RegionEntry> regionsToDelete,
        Map<RegionFile.RegionEntry, List<ChunkCoord>> headerZeros,
        long chunksAffected,
        long regionFilesAffected
) {
}
