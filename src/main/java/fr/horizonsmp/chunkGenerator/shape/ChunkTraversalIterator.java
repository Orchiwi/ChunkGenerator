package fr.horizonsmp.chunkGenerator.shape;

import java.util.Optional;

public interface ChunkTraversalIterator {
    Optional<ChunkCoord> next();

    void seek(long target);

    long index();

    boolean isDone();

    long countMatching();
}
