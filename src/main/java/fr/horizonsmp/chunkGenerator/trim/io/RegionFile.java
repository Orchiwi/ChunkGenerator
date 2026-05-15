package fr.horizonsmp.chunkGenerator.trim.io;

import fr.horizonsmp.chunkGenerator.shape.ChunkCoord;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegionFile {

    public static final int HEADER_SIZE = 4096;
    public static final int CHUNKS_PER_REGION_AXIS = 32;

    private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private RegionFile() {
    }

    public static int regionX(int chunkX) {
        return chunkX >> 5;
    }

    public static int regionZ(int chunkZ) {
        return chunkZ >> 5;
    }

    public static int headerOffset(int chunkX, int chunkZ) {
        return 4 * (((chunkZ & 31) << 5) | (chunkX & 31));
    }

    public static Path regionPath(Path worldFolder, int rx, int rz) {
        return worldFolder.resolve("region").resolve("r." + rx + "." + rz + ".mca");
    }

    public static List<RegionEntry> listRegions(Path worldFolder) throws IOException {
        Path regionDir = worldFolder.resolve("region");
        if (!Files.isDirectory(regionDir)) {
            return List.of();
        }
        List<RegionEntry> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "r.*.*.mca")) {
            for (Path p : stream) {
                Matcher m = REGION_NAME.matcher(p.getFileName().toString());
                if (m.matches()) {
                    int rx = Integer.parseInt(m.group(1));
                    int rz = Integer.parseInt(m.group(2));
                    out.add(new RegionEntry(p, rx, rz));
                }
            }
        }
        return out;
    }

    public static List<ChunkCoord> presentChunks(Path file, int rx, int rz) throws IOException {
        List<ChunkCoord> out = new ArrayList<>();
        if (Files.size(file) < HEADER_SIZE) {
            return out;
        }
        byte[] header = new byte[HEADER_SIZE];
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.readFully(header);
        }
        for (int z = 0; z < CHUNKS_PER_REGION_AXIS; z++) {
            for (int x = 0; x < CHUNKS_PER_REGION_AXIS; x++) {
                int offset = 4 * ((z << 5) | x);
                int sectorOffset = ((header[offset] & 0xFF) << 16)
                        | ((header[offset + 1] & 0xFF) << 8)
                        | (header[offset + 2] & 0xFF);
                int sectorCount = header[offset + 3] & 0xFF;
                if (sectorOffset != 0 || sectorCount != 0) {
                    int chunkX = (rx << 5) | x;
                    int chunkZ = (rz << 5) | z;
                    out.add(new ChunkCoord(chunkX, chunkZ));
                }
            }
        }
        return out;
    }

    public static int zeroHeaders(Path file, Collection<ChunkCoord> chunks) throws IOException {
        if (Files.size(file) < HEADER_SIZE) {
            throw new IOException("Region file too small to have a valid header: " + file);
        }
        int written = 0;
        byte[] zeros = new byte[4];
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rwd")) {
            for (ChunkCoord cc : chunks) {
                int offset = headerOffset(cc.x(), cc.z());
                raf.seek(offset);
                raf.write(zeros);
                written++;
            }
            raf.getFD().sync();
        }
        return written;
    }

    public static void deleteFile(Path file) throws IOException {
        Files.deleteIfExists(file);
    }

    public record RegionEntry(Path file, int rx, int rz) {
    }
}
