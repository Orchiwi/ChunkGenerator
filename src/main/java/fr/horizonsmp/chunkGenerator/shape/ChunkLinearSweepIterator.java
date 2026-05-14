package fr.horizonsmp.chunkGenerator.shape;

import java.util.Optional;

public final class ChunkLinearSweepIterator implements ChunkTraversalIterator {

    private final ZoneDefinition zone;
    private final TraversalPattern direction;
    private final int minDx;
    private final int maxDx;
    private final int minDz;
    private final int maxDz;

    private int dx;
    private int dz;
    private long index;
    private boolean done;

    public ChunkLinearSweepIterator(ZoneDefinition zone, TraversalPattern direction) {
        if (direction != TraversalPattern.NORTH && direction != TraversalPattern.SOUTH
                && direction != TraversalPattern.EAST && direction != TraversalPattern.WEST) {
            throw new IllegalArgumentException("Not a sweep direction: " + direction);
        }
        this.zone = zone;
        this.direction = direction;
        int r = zone.maxRingChunks();
        this.minDx = -r;
        this.maxDx = r;
        this.minDz = -r;
        this.maxDz = r;
        reset();
    }

    private void reset() {
        switch (direction) {
            case NORTH -> {
                dx = minDx;
                dz = minDz;
            }
            case SOUTH -> {
                dx = minDx;
                dz = maxDz;
            }
            case EAST -> {
                dx = maxDx;
                dz = minDz;
            }
            case WEST -> {
                dx = minDx;
                dz = minDz;
            }
            default -> throw new IllegalStateException();
        }
        index = 0;
        done = maxDx < minDx || maxDz < minDz;
    }

    @Override
    public Optional<ChunkCoord> next() {
        while (!done) {
            int curDx = dx;
            int curDz = dz;
            advance();
            int chunkX = zone.centerChunkX() + curDx;
            int chunkZ = zone.centerChunkZ() + curDz;
            if (zone.chunkIntersects(chunkX, chunkZ)) {
                return Optional.of(new ChunkCoord(chunkX, chunkZ));
            }
        }
        return Optional.empty();
    }

    @Override
    public void seek(long target) {
        reset();
        while (index < target && !done) {
            advance();
        }
    }

    @Override
    public long index() {
        return index;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public long countMatching() {
        long count = 0;
        for (int z = minDz; z <= maxDz; z++) {
            for (int x = minDx; x <= maxDx; x++) {
                int chunkX = zone.centerChunkX() + x;
                int chunkZ = zone.centerChunkZ() + z;
                if (zone.chunkIntersects(chunkX, chunkZ)) {
                    count++;
                }
            }
        }
        return count;
    }

    private void advance() {
        index++;
        switch (direction) {
            case NORTH -> {
                dx++;
                if (dx > maxDx) {
                    dx = minDx;
                    dz++;
                    if (dz > maxDz) done = true;
                }
            }
            case SOUTH -> {
                dx++;
                if (dx > maxDx) {
                    dx = minDx;
                    dz--;
                    if (dz < minDz) done = true;
                }
            }
            case WEST -> {
                dz++;
                if (dz > maxDz) {
                    dz = minDz;
                    dx++;
                    if (dx > maxDx) done = true;
                }
            }
            case EAST -> {
                dz++;
                if (dz > maxDz) {
                    dz = minDz;
                    dx--;
                    if (dx < minDx) done = true;
                }
            }
            default -> throw new IllegalStateException();
        }
    }
}
