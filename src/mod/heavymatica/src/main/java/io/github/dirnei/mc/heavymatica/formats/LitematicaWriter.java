package io.github.dirnei.mc.heavymatica.formats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a model as a Litematica file of format version 7 with one region covering the whole model. The region's
 * palette starts with air, as Litematica expects, followed by the other states in the order they first appear.
 */
public final class LitematicaWriter {

    public static final int VERSION = 7;
    public static final int SUB_VERSION = 1;

    /** @param createdMillis creation time in milliseconds since the epoch */
    public record Metadata(String name, String author, long createdMillis) {
    }

    private LitematicaWriter() {
    }

    public static byte[] write(SchematicModel model, Metadata metadata) {
        int width = model.width(), height = model.height(), length = model.length();
        int[] blocks = model.blocks();
        List<String> palette = new ArrayList<>(List.of(BlockStates.AIR));
        int[] remap = new int[model.palette().size()];
        Arrays.fill(remap, -1);
        int modelAir = model.palette().indexOf(BlockStates.AIR);
        if (modelAir >= 0) {
            remap[modelAir] = 0;
        }
        int[] region = new int[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            int index = blocks[i];
            if (remap[index] < 0) {
                palette.add(model.palette().get(index));
                remap[index] = palette.size() - 1;
            }
            region[i] = remap[index];
        }

        LinkedHashMap<String, Nbt.Tag> regionTag = new LinkedHashMap<>();
        regionTag.put("Position", vector(0, 0, 0));
        regionTag.put("Size", vector(width, height, length));
        regionTag.put("BlockStatePalette", new Nbt.ListTag(Nbt.COMPOUND, palette.stream().<Nbt.Tag>map(LitematicaWriter::paletteEntry).toList()));
        regionTag.put("BlockStates", new Nbt.LongArray(pack(region, palette.size())));
        regionTag.put("TileEntities", new Nbt.ListTag(Nbt.COMPOUND, tileEntities(model)));
        regionTag.put("Entities", new Nbt.ListTag(Nbt.COMPOUND, List.of()));
        regionTag.put("PendingBlockTicks", new Nbt.ListTag(Nbt.COMPOUND, List.of()));
        regionTag.put("PendingFluidTicks", new Nbt.ListTag(Nbt.COMPOUND, List.of()));
        LinkedHashMap<String, Nbt.Tag> regions = new LinkedHashMap<>();
        regions.put(metadata.name(), new Nbt.Compound(regionTag));

        long volume = (long) width * height * length;
        LinkedHashMap<String, Nbt.Tag> meta = new LinkedHashMap<>();
        meta.put("Name", new Nbt.Text(metadata.name()));
        meta.put("Author", new Nbt.Text(metadata.author()));
        meta.put("Description", new Nbt.Text(""));
        meta.put("RegionCount", integer(1));
        meta.put("TimeCreated", new Nbt.Number(metadata.createdMillis(), Nbt.LONG));
        meta.put("TimeModified", new Nbt.Number(metadata.createdMillis(), Nbt.LONG));
        meta.put("TotalBlocks", integer((int) model.blockTotal()));
        meta.put("TotalVolume", integer((int) volume));
        meta.put("EnclosingSize", vector(width, height, length));

        LinkedHashMap<String, Nbt.Tag> root = new LinkedHashMap<>();
        if (model.dataVersion() != null) {
            root.put("MinecraftDataVersion", integer(model.dataVersion()));
        }
        root.put("Version", integer(VERSION));
        root.put("SubVersion", integer(SUB_VERSION));
        root.put("Metadata", new Nbt.Compound(meta));
        root.put("Regions", new Nbt.Compound(regions));
        return Nbt.writeRoot(new Nbt.Compound(root));
    }

    /** The inverse of the reader's unpacking: entries of {@code bits} bits, an entry may continue into the next long. */
    static long[] pack(int[] values, int paletteSize) {
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(paletteSize, 1) - 1));
        long[] packed = new long[(int) (((long) values.length * bits + 63) / 64)];
        for (int i = 0; i < values.length; i++) {
            long start = (long) i * bits;
            int startLong = (int) (start >> 6);
            int endLong = (int) (((long) (i + 1) * bits - 1) >> 6);
            int offset = (int) (start & 63);
            long value = values[i];
            packed[startLong] |= value << offset;
            if (startLong != endLong) {
                packed[endLong] |= value >>> (64 - offset);
            }
        }
        return packed;
    }

    private static Nbt.Compound paletteEntry(String state) {
        LinkedHashMap<String, Nbt.Tag> entry = new LinkedHashMap<>();
        entry.put("Name", new Nbt.Text(BlockStates.id(state)));
        Map<String, String> properties = BlockStates.properties(state);
        if (!properties.isEmpty()) {
            LinkedHashMap<String, Nbt.Tag> tags = new LinkedHashMap<>();
            properties.forEach((key, value) -> tags.put(key, new Nbt.Text(value)));
            entry.put("Properties", new Nbt.Compound(tags));
        }
        return new Nbt.Compound(entry);
    }

    private static List<Nbt.Tag> tileEntities(SchematicModel model) {
        List<Map.Entry<SchematicModel.Pos, Nbt.Compound>> sorted = new ArrayList<>(model.blockEntities().entrySet());
        sorted.sort(Comparator.comparingInt((Map.Entry<SchematicModel.Pos, Nbt.Compound> e) -> e.getKey().y())
                .thenComparingInt(e -> e.getKey().z()).thenComparingInt(e -> e.getKey().x()));
        List<Nbt.Tag> result = new ArrayList<>(sorted.size());
        for (Map.Entry<SchematicModel.Pos, Nbt.Compound> entry : sorted) {
            LinkedHashMap<String, Nbt.Tag> tag = new LinkedHashMap<>(entry.getValue().entries());
            tag.put("x", integer(entry.getKey().x()));
            tag.put("y", integer(entry.getKey().y()));
            tag.put("z", integer(entry.getKey().z()));
            result.add(new Nbt.Compound(tag));
        }
        return result;
    }

    private static Nbt.Compound vector(int x, int y, int z) {
        LinkedHashMap<String, Nbt.Tag> entries = new LinkedHashMap<>();
        entries.put("x", integer(x));
        entries.put("y", integer(y));
        entries.put("z", integer(z));
        return new Nbt.Compound(entries);
    }

    private static Nbt.Number integer(int value) {
        return new Nbt.Number(value, Nbt.INT);
    }
}
