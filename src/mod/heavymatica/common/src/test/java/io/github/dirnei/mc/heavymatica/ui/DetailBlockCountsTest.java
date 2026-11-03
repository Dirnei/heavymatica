package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DetailBlockCountsTest {

    private static final Set<String> KNOWN = Set.of("minecraft:short_grass", "minecraft:stone", "minecraft:grass_block");

    private static String current(String block) {
        return block.equals("minecraft:grass") ? "minecraft:short_grass" : block;
    }

    @Test
    void oldBlocksShowTheirCurrentName() {
        DetailBlockCounts counts = DetailBlockCounts.of(Map.of("minecraft:grass", 12L, "minecraft:stone", 40L), DetailBlockCountsTest::current, KNOWN::contains);
        assertEquals(List.of(new DetailBlockCounts.Row("minecraft:stone", 40, true), new DetailBlockCounts.Row("minecraft:short_grass", 12, true)),
                counts.rows());
        assertEquals(0, counts.unknownTypes());
    }

    @Test
    void unknownBlocksAreMarkedAndCounted() {
        DetailBlockCounts counts = DetailBlockCounts.of(Map.of("create:shaft", 40L, "create:cogwheel", 12L, "minecraft:stone", 5L),
                DetailBlockCountsTest::current, KNOWN::contains);
        assertEquals(List.of(new DetailBlockCounts.Row("create:shaft", 40, false), new DetailBlockCounts.Row("create:cogwheel", 12, false),
                new DetailBlockCounts.Row("minecraft:stone", 5, true)), counts.rows());
        assertEquals(2, counts.unknownTypes());
    }

    @Test
    void blocksThatBecomeTheSameAreAddedUp() {
        DetailBlockCounts counts = DetailBlockCounts.of(Map.of("minecraft:grass", 2L, "minecraft:short_grass", 3L), DetailBlockCountsTest::current,
                KNOWN::contains);
        assertEquals(List.of(new DetailBlockCounts.Row("minecraft:short_grass", 5, true)), counts.rows());
    }
}
