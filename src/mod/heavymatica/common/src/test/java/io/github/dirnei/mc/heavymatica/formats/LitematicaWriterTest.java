package io.github.dirnei.mc.heavymatica.formats;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LitematicaWriterTest {

    private static final LitematicaWriter.Metadata METADATA = new LitematicaWriter.Metadata("Starter Hut", "Dirnei", 1_793_000_000_000L);

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 17})
    void round_trips_palettes_of_every_bit_width(int paletteSize) {
        List<String> palette = new ArrayList<>(List.of(BlockStates.AIR));
        for (int i = 1; i < paletteSize; i++) {
            palette.add("minecraft:block_" + i);
        }
        int width = 7, height = 5, length = 3;
        int[] blocks = new int[width * height * length];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = (i * 7) % paletteSize;
        }
        SchematicModel model = new SchematicModel(SchematicFormat.LITEMATICA, width, height, length, palette, blocks, Map.of(), 4671, 0);

        assertReadsBack(model, SchematicReader.read(LitematicaWriter.write(model, METADATA)));
    }

    @Test
    void round_trips_stairs_air_modded_blocks_and_block_entities() {
        SchematicModel model = sample();

        SchematicModel read = SchematicReader.read(LitematicaWriter.write(model, METADATA));

        assertReadsBack(model, read);
        Nbt.Compound chest = read.blockEntities().get(new SchematicModel.Pos(1, 0, 1));
        assertEquals("minecraft:chest", chest.string("id"));
        assertEquals(1, chest.list("Items").items().size());
    }

    @Test
    void air_does_not_need_to_be_first_in_the_model_palette() {
        SchematicModel model = new SchematicModel(SchematicFormat.SPONGE, 3, 1, 1, List.of("minecraft:stone", BlockStates.AIR), new int[] {0, 1, 0},
                Map.of(), null, 0);

        SchematicModel read = SchematicReader.read(LitematicaWriter.write(model, METADATA));

        assertEquals(model.paletteDigest(), read.paletteDigest());
        assertEquals(BlockStates.AIR, read.palette().getFirst());
        assertEquals(null, read.dataVersion());
    }

    @Test
    void writes_one_region_and_the_metadata() throws IOException {
        Nbt.Compound root = Nbt.readRoot(new GZIPInputStream(new ByteArrayInputStream(LitematicaWriter.write(sample(), METADATA))));

        assertEquals(7, root.integer("Version"));
        assertEquals(1, root.compound("Regions").entries().size());
        Nbt.Compound metadata = root.compound("Metadata");
        assertEquals("Starter Hut", metadata.string("Name"));
        assertEquals("Dirnei", metadata.string("Author"));
        assertEquals(3, metadata.compound("EnclosingSize").integer("x"));
        assertEquals(2, metadata.compound("EnclosingSize").integer("y"));
        assertEquals(4, metadata.compound("EnclosingSize").integer("z"));
        Nbt.Compound region = root.compound("Regions").compound("Starter Hut");
        assertEquals(3, region.compound("Size").integer("x"));
        assertEquals(0, region.list("Entities").items().size());
    }

    @Test
    void same_input_gives_the_same_bytes() {
        assertArrayEquals(LitematicaWriter.write(sample(), METADATA), LitematicaWriter.write(sample(), METADATA));
    }

    private static void assertReadsBack(SchematicModel expected, SchematicModel read) {
        assertEquals(SchematicFormat.LITEMATICA, read.format());
        assertEquals(List.of(expected.width(), expected.height(), expected.length()), List.of(read.width(), read.height(), read.length()));
        assertEquals(expected.paletteDigest(), read.paletteDigest());
        assertEquals(expected.dataVersion(), read.dataVersion());
        assertEquals(ids(expected), ids(read));
    }

    private static Map<SchematicModel.Pos, String> ids(SchematicModel model) {
        Map<SchematicModel.Pos, String> ids = new java.util.TreeMap<>(java.util.Comparator.comparingInt(SchematicModel.Pos::y)
                .thenComparingInt(SchematicModel.Pos::z).thenComparingInt(SchematicModel.Pos::x));
        model.blockEntities().forEach((pos, tag) -> ids.put(pos, tag.string("id")));
        return ids;
    }

    /** 3 x 2 x 4 with stairs, air, a modded block and a chest holding one item. */
    static SchematicModel sample() {
        List<String> palette = List.of(BlockStates.AIR, "minecraft:stone",
                "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]",
                "servus_foundation:utility_duct[north=true,south=false]", "minecraft:chest[facing=north,type=single,waterlogged=false]");
        int width = 3, height = 2, length = 4;
        int[] blocks = new int[width * height * length];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                blocks[SchematicModel.index(x, 0, z, width, length)] = 1;
            }
        }
        blocks[SchematicModel.index(0, 1, 0, width, length)] = 2;
        blocks[SchematicModel.index(2, 1, 3, width, length)] = 3;
        blocks[SchematicModel.index(1, 0, 1, width, length)] = 4;

        LinkedHashMap<String, Nbt.Tag> item = new LinkedHashMap<>();
        item.put("Slot", new Nbt.Number(0, Nbt.BYTE));
        item.put("id", new Nbt.Text("minecraft:apple"));
        item.put("count", new Nbt.Number(3, Nbt.INT));
        LinkedHashMap<String, Nbt.Tag> chest = new LinkedHashMap<>();
        chest.put("id", new Nbt.Text("minecraft:chest"));
        chest.put("Items", new Nbt.ListTag(Nbt.COMPOUND, List.of(new Nbt.Compound(item))));
        return new SchematicModel(SchematicFormat.LITEMATICA, width, height, length, palette, blocks,
                Map.of(new SchematicModel.Pos(1, 0, 1), new Nbt.Compound(chest)), 4671, 0);
    }
}
