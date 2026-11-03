package io.github.dirnei.mc.heavymatica.formats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Mirrors the cloud's SchematicModelTests, so both models follow the same rules. */
class SchematicModelTest {

    private static final String STAIRS_NORTH = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]";
    private static final String STAIRS_EAST = "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]";

    @Test
    void stairs_facing_different_ways_count_as_one_block_and_air_is_not_counted() {
        int[] blocks = IntStream.concat(IntStream.concat(IntStream.generate(() -> 0).limit(4), IntStream.generate(() -> 1).limit(6)),
                IntStream.generate(() -> 2).limit(10)).toArray();
        SchematicModel model = model(List.of(STAIRS_NORTH, STAIRS_EAST, BlockStates.AIR), blocks, 10, 1, 2);

        assertEquals(List.of(new SchematicModel.BlockCount("minecraft:oak_stairs", 10)), model.blockCounts());
    }

    @Test
    void every_air_variant_and_structure_void_are_excluded_from_counts() {
        List<String> palette = List.of(BlockStates.AIR, "minecraft:cave_air", "minecraft:void_air", BlockStates.STRUCTURE_VOID, "minecraft:stone");
        SchematicModel model = model(palette, new int[] {0, 1, 2, 3, 4, 4}, 6, 1, 1);

        assertEquals(List.of(new SchematicModel.BlockCount("minecraft:stone", 2)), model.blockCounts());
    }

    @Test
    void counts_are_most_common_first() {
        SchematicModel model = model(List.of("minecraft:glass", "minecraft:stone"), new int[] {0, 1, 1, 1}, 4, 1, 1);

        assertEquals(List.of("minecraft:stone", "minecraft:glass"), model.blockCounts().stream().map(SchematicModel.BlockCount::block).toList());
    }

    @Test
    void palette_digest_hashes_each_position_with_a_newline_in_canonical_order() throws Exception {
        SchematicModel model = model(List.of("minecraft:b", "minecraft:a"), new int[] {1, 0, 0}, 3, 1, 1);
        byte[] expected = MessageDigest.getInstance("SHA-256").digest("minecraft:a\nminecraft:b\nminecraft:b\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(HexFormat.of().formatHex(expected), model.paletteDigest());
    }

    @Test
    void index_runs_x_fastest_then_z_then_y() {
        assertEquals(0, SchematicModel.index(0, 0, 0, 3, 4));
        assertEquals(1, SchematicModel.index(1, 0, 0, 3, 4));
        assertEquals(3, SchematicModel.index(0, 0, 1, 3, 4));
        assertEquals(12, SchematicModel.index(0, 1, 0, 3, 4));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "minecraft:stone|minecraft:stone",
            "stone|minecraft:stone",
            "minecraft:oak_stairs[waterlogged=false,facing=north]|minecraft:oak_stairs[facing=north,waterlogged=false]",
            "servus_foundation:utility_duct[north=true]|servus_foundation:utility_duct[north=true]",
    })
    void block_states_are_canonical(String input, String canonical) {
        assertEquals(canonical, BlockStates.canonical(input));
    }

    private static SchematicModel model(List<String> palette, int[] blocks, int width, int height, int length) {
        return new SchematicModel(SchematicFormat.SPONGE, width, height, length, palette, blocks, Map.of(), null, 0);
    }
}
