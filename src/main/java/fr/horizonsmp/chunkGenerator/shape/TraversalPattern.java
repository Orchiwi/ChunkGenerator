package fr.horizonsmp.chunkGenerator.shape;

import java.util.Locale;
import java.util.Optional;

public enum TraversalPattern {
    CENTER,
    EDGE,
    NORTH,
    SOUTH,
    EAST,
    WEST;

    public ChunkTraversalIterator iterator(ZoneDefinition zone) {
        return switch (this) {
            case CENTER -> new ChunkSpiralIterator(zone);
            case EDGE -> new ChunkConcentricInIterator(zone);
            case NORTH, SOUTH, EAST, WEST -> new ChunkLinearSweepIterator(zone, this);
        };
    }

    public static Optional<TraversalPattern> fromString(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
