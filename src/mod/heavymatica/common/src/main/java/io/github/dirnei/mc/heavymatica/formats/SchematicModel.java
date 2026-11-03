package io.github.dirnei.mc.heavymatica.formats;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * The canonical model every accepted file is read into: a size, a block state at every position as an index into
 * {@link #palette()} (x fastest, then z, then y), block entities by position, and the data version when the file
 * records one. Independent of the game.
 */
public final class SchematicModel {

    public record Pos(int x, int y, int z) {
    }

    public record BlockCount(String block, long count) {
    }

    private final SchematicFormat format;
    private final int width;
    private final int height;
    private final int length;
    private final List<String> palette;
    private final int[] blocks;
    private final Map<Pos, Nbt.Compound> blockEntities;
    private final @Nullable Integer dataVersion;
    private final int skippedEntities;

    public SchematicModel(SchematicFormat format, int width, int height, int length, List<String> palette, int[] blocks,
                          Map<Pos, Nbt.Compound> blockEntities, @Nullable Integer dataVersion, int skippedEntities) {
        if (blocks.length != (long) width * height * length) {
            throw new IllegalArgumentException("The block array does not match the size.");
        }
        this.format = format;
        this.width = width;
        this.height = height;
        this.length = length;
        this.palette = List.copyOf(palette);
        this.blocks = blocks;
        this.blockEntities = Map.copyOf(blockEntities);
        this.dataVersion = dataVersion;
        this.skippedEntities = skippedEntities;
    }

    public static int index(int x, int y, int z, int width, int length) {
        return x + z * width + y * width * length;
    }

    public SchematicFormat format() {
        return format;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int length() {
        return length;
    }

    public List<String> palette() {
        return palette;
    }

    /** Palette indices in canonical order. Do not modify. */
    public int[] blocks() {
        return blocks;
    }

    public String stateAt(int x, int y, int z) {
        return palette.get(blocks[index(x, y, z, width, length)]);
    }

    public Map<Pos, Nbt.Compound> blockEntities() {
        return blockEntities;
    }

    public @Nullable Integer dataVersion() {
        return dataVersion;
    }

    public int skippedEntities() {
        return skippedEntities;
    }

    /** Counts per block ID, properties ignored, air and structure void excluded; most common first. */
    public List<BlockCount> blockCounts() {
        long[] perPalette = new long[palette.size()];
        for (int index : blocks) {
            perPalette[index]++;
        }
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < perPalette.length; i++) {
            String id = BlockStates.id(palette.get(i));
            if (perPalette[i] > 0 && !BlockStates.isAirLike(id)) {
                counts.merge(id, perPalette[i], Long::sum);
            }
        }
        List<BlockCount> result = new ArrayList<>();
        counts.forEach((block, count) -> result.add(new BlockCount(block, count)));
        result.sort(Comparator.comparingLong(BlockCount::count).reversed().thenComparing(BlockCount::block));
        return result;
    }

    public long blockTotal() {
        return blockCounts().stream().mapToLong(BlockCount::count).sum();
    }

    /** SHA-256 over every position's canonical state followed by a newline, in canonical order. */
    public String paletteDigest() {
        byte[][] encoded = new byte[palette.size()][];
        for (int i = 0; i < encoded.length; i++) {
            encoded[i] = (palette.get(i) + "\n").getBytes(StandardCharsets.UTF_8);
        }
        MessageDigest digest = sha256();
        for (int index : blocks) {
            digest.update(encoded[index]);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SchematicModel model && format == model.format && width == model.width && height == model.height
                && length == model.length && paletteDigest().equals(model.paletteDigest()) && Objects.equals(dataVersion, model.dataVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, width, height, length, dataVersion);
    }
}
