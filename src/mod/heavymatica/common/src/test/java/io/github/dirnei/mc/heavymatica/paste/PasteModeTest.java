package io.github.dirnei.mc.heavymatica.paste;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PasteModeTest {

    private static final String AIR = "minecraft:air";
    private static final String DIRT = "minecraft:dirt";
    private static final String STONE = "minecraft:stone";
    private static final String PLANKS = "minecraft:oak_planks";
    private static final String GRASS = "minecraft:short_grass";
    private static final String WATER = "minecraft:water";

    private static final PasteMode.States<String> STATES = new PasteMode.States<>() {
        @Override
        public boolean air(String state) {
            return state.equals(AIR);
        }

        @Override
        public boolean replaceable(String state) {
            return Set.of(AIR, GRASS, WATER).contains(state);
        }
    };

    private static boolean changes(PasteMode mode, String planned, String found) {
        return mode.changes(planned, false, found, STATES);
    }

    @Test
    void replaceAllClearsTerrain() {
        assertTrue(changes(PasteMode.REPLACE_ALL, AIR, DIRT));
        assertTrue(changes(PasteMode.REPLACE_ALL, PLANKS, STONE));
        assertTrue(changes(PasteMode.REPLACE_ALL, PLANKS, AIR));
    }

    @Test
    void onlyIntoAirKeepsBlocks() {
        assertFalse(changes(PasteMode.ONLY_INTO_AIR, PLANKS, STONE));
        assertTrue(changes(PasteMode.ONLY_INTO_AIR, PLANKS, AIR));
        assertTrue(changes(PasteMode.ONLY_INTO_AIR, PLANKS, GRASS));
        assertTrue(changes(PasteMode.ONLY_INTO_AIR, PLANKS, WATER));
        assertFalse(changes(PasteMode.ONLY_INTO_AIR, AIR, GRASS));
        assertFalse(changes(PasteMode.ONLY_INTO_AIR, AIR, DIRT));
    }

    @Test
    void skipAirKeepsTerrain() {
        assertFalse(changes(PasteMode.SKIP_AIR, AIR, DIRT));
        assertTrue(changes(PasteMode.SKIP_AIR, PLANKS, STONE));
        assertTrue(changes(PasteMode.SKIP_AIR, PLANKS, AIR));
    }

    @Test
    void positionsThatAlreadyMatchStay() {
        for (PasteMode mode : PasteMode.values()) {
            assertFalse(changes(mode, STONE, STONE));
            assertFalse(changes(mode, AIR, AIR));
        }
    }

    @Test
    void blockEntityDataIsPlacedEvenWhenTheBlockMatches() {
        assertTrue(PasteMode.REPLACE_ALL.changes("minecraft:chest", true, "minecraft:chest", STATES));
        assertTrue(PasteMode.SKIP_AIR.changes("minecraft:chest", true, "minecraft:chest", STATES));
        assertFalse(PasteMode.ONLY_INTO_AIR.changes("minecraft:chest", true, "minecraft:chest", STATES));
    }
}
