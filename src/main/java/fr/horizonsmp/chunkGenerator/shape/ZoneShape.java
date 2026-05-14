package fr.horizonsmp.chunkGenerator.shape;

import java.util.Locale;
import java.util.Optional;

public enum ZoneShape {
    SQUARE,
    CIRCLE,
    RECTANGLE;

    public static Optional<ZoneShape> fromString(String raw) {
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
