package fr.horizonsmp.chunkGenerator.shape;

import java.util.Optional;

public final class ChunkConcentricInIterator implements ChunkTraversalIterator {

    private final ZoneDefinition zone;
    private final int maxRing;

    private int ring;
    private int side;
    private int step;
    private long index;
    private boolean done;

    public ChunkConcentricInIterator(ZoneDefinition zone) {
        this.zone = zone;
        this.maxRing = zone.maxRingChunks();
        reset();
    }

    private void reset() {
        ring = maxRing;
        side = 0;
        step = 0;
        index = 0;
        done = maxRing < 0;
    }

    @Override
    public Optional<ChunkCoord> next() {
        while (!done) {
            int curX = currentX();
            int curZ = currentZ();
            advance();
            int chunkX = zone.centerChunkX() + curX;
            int chunkZ = zone.centerChunkZ() + curZ;
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
        for (int dz = -maxRing; dz <= maxRing; dz++) {
            for (int dx = -maxRing; dx <= maxRing; dx++) {
                int chunkX = zone.centerChunkX() + dx;
                int chunkZ = zone.centerChunkZ() + dz;
                if (zone.chunkIntersects(chunkX, chunkZ)) {
                    count++;
                }
            }
        }
        return count;
    }

    private int currentX() {
        if (ring == 0) {
            return 0;
        }
        return switch (side) {
            case 0 -> -ring + step;
            case 1 -> ring;
            case 2 -> ring - step;
            case 3 -> -ring;
            default -> throw new IllegalStateException("Invalid side: " + side);
        };
    }

    private int currentZ() {
        if (ring == 0) {
            return 0;
        }
        return switch (side) {
            case 0 -> -ring;
            case 1 -> -ring + step;
            case 2 -> ring;
            case 3 -> ring - step;
            default -> throw new IllegalStateException("Invalid side: " + side);
        };
    }

    private void advance() {
        index++;
        if (ring == 0) {
            done = true;
            return;
        }
        step++;
        if (step == ring * 2) {
            step = 0;
            side++;
            if (side == 4) {
                side = 0;
                ring--;
            }
        }
    }
}
