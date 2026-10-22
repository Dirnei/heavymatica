package io.github.dirnei.mc.heavymatica.formats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockStatesTest {

    private static final Map<String, String> STAIRS = Map.of("minecraft:oak_stairs", "minecraft:spruce_stairs");

    @Test
    void swappedEntryKeepsItsProperties() {
        assertEquals("minecraft:spruce_stairs[facing=east,half=top,shape=straight,waterlogged=true]",
                BlockStates.swapped("minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=true]", STAIRS));
        assertEquals("minecraft:spruce_planks", BlockStates.swapped("minecraft:oak_planks", Map.of("minecraft:oak_planks", "minecraft:spruce_planks")));
    }

    @Test
    void unswappedEntryStaysAsItIs() {
        assertEquals("minecraft:oak_planks", BlockStates.swapped("minecraft:oak_planks", STAIRS));
        assertEquals("minecraft:oak_slab[type=top,waterlogged=false]", BlockStates.swapped("minecraft:oak_slab[type=top,waterlogged=false]", STAIRS));
    }

    @Test
    void propertiesByName() {
        assertEquals(Map.of("facing", "east", "half", "top"), BlockStates.properties("minecraft:oak_stairs[facing=east,half=top]"));
        assertEquals(Map.of(), BlockStates.properties("minecraft:oak_planks"));
    }

    @Test
    void swapsDoNotChain() {
        Map<String, String> swaps = Map.of("minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:spruce_planks", "minecraft:birch_planks");
        assertEquals("minecraft:spruce_planks", BlockStates.swapped("minecraft:oak_planks", swaps));
        assertEquals("minecraft:birch_planks", BlockStates.swapped("minecraft:spruce_planks", swaps));
    }
}
