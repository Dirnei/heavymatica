package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dirnei.mc.heavymatica.placement.CellState.Expected;
import io.github.dirnei.mc.heavymatica.placement.CellState.Found;
import org.junit.jupiter.api.Test;

class CellStateTest {

    private static final int OAK_PLANKS = 1;
    private static final int DIRT = 2;
    private static final int OAK_STAIRS = 3;
    private static final int OAK_SLAB = 4;
    private static final int OAK_FENCE = 5;
    private static final int TALL_GRASS = 6;
    private static final int NORTH = 10;
    private static final int EAST = 11;
    private static final int SOUTH = 12;
    private static final int BOTTOM = 20;
    private static final int TOP = 21;
    private static final int NONE = 0;

    @Test
    void missingWhenTheWorldIsAir() {
        assertEquals(CellState.MISSING, CellState.classify(Expected.block(OAK_PLANKS, NONE, false), Found.NOTHING));
    }

    @Test
    void wrongBlock() {
        assertEquals(CellState.WRONG_BLOCK, CellState.classify(Expected.block(OAK_PLANKS, NONE, false), Found.block(DIRT, NONE, false)));
    }

    @Test
    void structureVoidIsIgnored() {
        assertEquals(CellState.IGNORED, CellState.classify(Expected.IGNORE, Found.block(TALL_GRASS, NONE, false)));
    }

    @Test
    void airInBothMatches() {
        assertEquals(CellState.MATCHING, CellState.classify(Expected.AIR, Found.NOTHING));
    }

    @Test
    void blockWhereTheSchematicHasAirIsExtra() {
        assertEquals(CellState.EXTRA, CellState.classify(Expected.AIR, Found.block(TALL_GRASS, NONE, false)));
    }

    @Test
    void stairsFacingTheWrongWay() {
        assertEquals(CellState.WRONG_ORIENTATION, CellState.classify(Expected.block(OAK_STAIRS, NORTH, false), Found.block(OAK_STAIRS, EAST, false)));
    }

    @Test
    void sameOrientationMatchesWhateverElseDiffers() {
        // Fence connections are not part of the orientation key, so two fences with different connections compare equal.
        assertEquals(CellState.MATCHING, CellState.classify(Expected.block(OAK_FENCE, NONE, false), Found.block(OAK_FENCE, NONE, false)));
    }

    @Test
    void upsideDownSlab() {
        assertEquals(CellState.WRONG_ORIENTATION, CellState.classify(Expected.block(OAK_SLAB, BOTTOM, false), Found.block(OAK_SLAB, TOP, false)));
    }

    @Test
    void needsWater() {
        assertEquals(CellState.WRONG_WATER, CellState.classify(Expected.block(OAK_SLAB, BOTTOM, true), Found.block(OAK_SLAB, BOTTOM, false)));
    }

    @Test
    void hasWaterItShouldNot() {
        assertEquals(CellState.WRONG_WATER, CellState.classify(Expected.block(OAK_SLAB, BOTTOM, false), Found.block(OAK_SLAB, BOTTOM, true)));
    }

    @Test
    void orientationBeforeWater() {
        assertEquals(CellState.WRONG_ORIENTATION, CellState.classify(Expected.block(OAK_STAIRS, NORTH, true), Found.block(OAK_STAIRS, SOUTH, false)));
    }

    @Test
    void blockBeforeOrientation() {
        assertEquals(CellState.WRONG_BLOCK, CellState.classify(Expected.block(OAK_STAIRS, NORTH, true), Found.block(OAK_SLAB, BOTTOM, false)));
    }
}
