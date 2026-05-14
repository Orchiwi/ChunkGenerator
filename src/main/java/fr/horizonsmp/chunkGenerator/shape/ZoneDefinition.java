package fr.horizonsmp.chunkGenerator.shape;

public record ZoneDefinition(
        ZoneShape shape,
        int centerBlockX,
        int centerBlockZ,
        int halfWidthBlocks,
        int halfLengthBlocks
) {

    public static ZoneDefinition square(int centerX, int centerZ, int radius) {
        return new ZoneDefinition(ZoneShape.SQUARE, centerX, centerZ, radius, radius);
    }

    public static ZoneDefinition circle(int centerX, int centerZ, int radius) {
        return new ZoneDefinition(ZoneShape.CIRCLE, centerX, centerZ, radius, radius);
    }

    public static ZoneDefinition rectangle(int centerX, int centerZ, int halfWidth, int halfLength) {
        return new ZoneDefinition(ZoneShape.RECTANGLE, centerX, centerZ, halfWidth, halfLength);
    }

    public int centerChunkX() {
        return centerBlockX >> 4;
    }

    public int centerChunkZ() {
        return centerBlockZ >> 4;
    }

    public int maxRingChunks() {
        int maxHalf = Math.max(halfWidthBlocks, halfLengthBlocks);
        return (maxHalf >> 4) + 2;
    }

    public boolean chunkIntersects(int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;
        int closestX = clamp(centerBlockX, minX, maxX);
        int closestZ = clamp(centerBlockZ, minZ, maxZ);
        int dx = centerBlockX - closestX;
        int dz = centerBlockZ - closestZ;
        return switch (shape) {
            case SQUARE, RECTANGLE -> Math.abs(dx) <= halfWidthBlocks && Math.abs(dz) <= halfLengthBlocks;
            case CIRCLE -> {
                long distSq = (long) dx * dx + (long) dz * dz;
                long radius = halfWidthBlocks;
                yield distSq <= radius * radius;
            }
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
