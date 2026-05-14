package fr.horizonsmp.chunkGenerator.shape;

import java.util.Optional;

public final class ChunkSpiralIterator implements ChunkTraversalIterator {

    private static final int[] DX = {1, 0, -1, 0};
    private static final int[] DZ = {0, 1, 0, -1};

    private final ZoneDefinition zone;
    private final int maxRing;

    private int x;
    private int z;
    private int dirIdx;
    private int stepLen;
    private int stepsTaken;
    private int sideCount;
    private long index;
    private boolean done;

    public ChunkSpiralIterator(ZoneDefinition zone) {
        this.zone = zone;
        this.maxRing = zone.maxRingChunks();
        reset();
    }

    public void reset() {
        this.x = 0;
        this.z = 0;
        this.dirIdx = 0;
        this.stepLen = 1;
        this.stepsTaken = 0;
        this.sideCount = 0;
        this.index = 0;
        this.done = false;
    }

    public void seek(long target) {
        while (index < target && !done) {
            rawAdvance();
        }
    }

    public long index() {
        return index;
    }

    public boolean isDone() {
        return done;
    }

    public Optional<ChunkCoord> next() {
        while (!done) {
            int curX = x;
            int curZ = z;
            int chunkX = zone.centerChunkX() + curX;
            int chunkZ = zone.centerChunkZ() + curZ;
            rawAdvance();
            if (zone.chunkIntersects(chunkX, chunkZ)) {
                return Optional.of(new ChunkCoord(chunkX, chunkZ));
            }
        }
        return Optional.empty();
    }

    private void rawAdvance() {
        if (done) {
            return;
        }
        index++;
        x += DX[dirIdx];
        z += DZ[dirIdx];
        stepsTaken++;
        if (stepsTaken == stepLen) {
            stepsTaken = 0;
            dirIdx = (dirIdx + 1) & 3;
            sideCount++;
            if (sideCount == 2) {
                sideCount = 0;
                stepLen++;
            }
        }
        if (Math.max(Math.abs(x), Math.abs(z)) > maxRing) {
            done = true;
        }
    }

    public long countMatching() {
        long count = 0;
        int savedX = x, savedZ = z, savedDir = dirIdx, savedStepLen = stepLen;
        int savedTaken = stepsTaken, savedSide = sideCount;
        long savedIndex = index;
        boolean savedDone = done;
        reset();
        while (!done) {
            int chunkX = zone.centerChunkX() + x;
            int chunkZ = zone.centerChunkZ() + z;
            rawAdvance();
            if (zone.chunkIntersects(chunkX, chunkZ)) {
                count++;
            }
        }
        this.x = savedX;
        this.z = savedZ;
        this.dirIdx = savedDir;
        this.stepLen = savedStepLen;
        this.stepsTaken = savedTaken;
        this.sideCount = savedSide;
        this.index = savedIndex;
        this.done = savedDone;
        return count;
    }
}
