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
    private static final int GRASS = 30;
    private static final int PATH = 31;
    private static final int FARMLAND = 32;
    private static final int PODZOL = 33;
    private static final int OAK_LOG = 34;
    private static final int STRIPPED_OAK_LOG = 35;
    private static final int WHITE_CONCRETE = 36;
    private static final int WHITE_POWDER = 37;
    private static final int Y_AXIS = 40;
    private static final int X_AXIS = 41;
    private static final int[] NOTHING = {};

    private static Expected planned(int block, int orientation, boolean waterlogged, int[] same, int[] finishFrom, BlockEquivalents.Finish finish) {
        return Expected.block(block, orientation, waterlogged, same, finishFrom, finish == null ? -1 : finish.ordinal());
    }

    private static final Expected GRASS_PLANNED = planned(GRASS, NONE, false, new int[] {DIRT}, NOTHING, null);
    private static final Expected DIRT_PLANNED = planned(DIRT, NONE, false, new int[] {GRASS}, NOTHING, null);
    private static final Expected PATH_PLANNED = planned(PATH, NONE, false, NOTHING, new int[] {DIRT, GRASS, PODZOL}, BlockEquivalents.Finish.SHOVEL);
    private static final Expected FARMLAND_PLANNED = planned(FARMLAND, NONE, false, NOTHING, new int[] {DIRT, GRASS, PATH}, BlockEquivalents.Finish.HOE);
    private static final Expected WHITE_CONCRETE_PLANNED = planned(WHITE_CONCRETE, NONE, false, NOTHING, new int[] {WHITE_POWDER},
            BlockEquivalents.Finish.WATER);
    private static final Expected WHITE_POWDER_PLANNED = planned(WHITE_POWDER, NONE, false, NOTHING, NOTHING, null);

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

    @Test
    void dirtOnPlannedGrassAndGrassOnPlannedDirtMatch() {
        assertEquals(CellState.MATCHING, CellState.classify(GRASS_PLANNED, Found.block(DIRT, NONE, false)));
        assertEquals(CellState.MATCHING, CellState.classify(DIRT_PLANNED, Found.block(GRASS, NONE, false)));
        assertEquals(CellState.MATCHING, CellState.classify(DIRT_PLANNED, Found.block(DIRT, NONE, false)));
    }

    @Test
    void grassOrPodzolOnAPlannedPathNeedsFinishing() {
        assertEquals(CellState.NEEDS_FINISHING, CellState.classify(PATH_PLANNED, Found.block(GRASS, NONE, false)));
        assertEquals(CellState.NEEDS_FINISHING, CellState.classify(PATH_PLANNED, Found.block(PODZOL, NONE, false)));
    }

    @Test
    void aPathOnPlannedFarmlandNeedsFinishing() {
        assertEquals(CellState.NEEDS_FINISHING, CellState.classify(FARMLAND_PLANNED, Found.block(PATH, NONE, false)));
        assertEquals(CellState.NEEDS_FINISHING, CellState.classify(FARMLAND_PLANNED, Found.block(DIRT, NONE, false)));
    }

    @Test
    void theWrongFinishIsWrongBlock() {
        assertEquals(CellState.WRONG_BLOCK, CellState.classify(PATH_PLANNED, Found.block(FARMLAND, NONE, false)));
        assertEquals(CellState.WRONG_BLOCK, CellState.classify(WHITE_POWDER_PLANNED, Found.block(WHITE_CONCRETE, NONE, false)));
    }

    @Test
    void anUnstrippedLogKeepsItsAxis() {
        Expected stripped = planned(STRIPPED_OAK_LOG, Y_AXIS, false, NOTHING, new int[] {OAK_LOG}, BlockEquivalents.Finish.AXE);
        assertEquals(CellState.NEEDS_FINISHING, CellState.classify(stripped, Found.block(OAK_LOG, Y_AXIS, false)));
        assertEquals(CellState.WRONG_ORIENTATION, CellState.classify(stripped, Found.block(OAK_LOG, X_AXIS, false)));
        assertEquals(CellState.MATCHING, CellState.classify(stripped, Found.block(STRIPPED_OAK_LOG, Y_AXIS, false)));
    }

    @Test
    void waterBeforeFinishing() {
        Expected waterlogged = planned(STRIPPED_OAK_LOG, Y_AXIS, true, NOTHING, new int[] {OAK_LOG}, BlockEquivalents.Finish.AXE);
        assertEquals(CellState.WRONG_WATER, CellState.classify(waterlogged, Found.block(OAK_LOG, Y_AXIS, false)));
    }

    @Test
    void powderOnPlannedConcreteNeedsFinishing() {
        assertEquals(CellState.NEEDS_FINISHING, CellState.classify(WHITE_CONCRETE_PLANNED, Found.block(WHITE_POWDER, NONE, false)));
        assertEquals(CellState.MISSING, CellState.classify(WHITE_CONCRETE_PLANNED, Found.NOTHING));
    }

    @Test
    void needsFinishingShowsABoxButNoGhostAndNeedsNoItem() {
        assertEquals(false, CellState.NEEDS_FINISHING.showsGhost());
        assertEquals(false, CellState.NEEDS_FINISHING.needsItem());
        assertEquals(false, CellState.NEEDS_FINISHING.mistake());
        assertEquals(true, CellState.NEEDS_FINISHING.boxed());
        for (CellState state : CellState.values()) {
            assertEquals(state.mistake() || state == CellState.NEEDS_FINISHING, state.boxed(), state.name());
        }
        assertEquals(8, CellState.NEEDS_FINISHING.ordinal());
    }

    private static final int CANDLES = 50;
    private static final int PICKLES = 51;
    private static final int SNOW = 52;
    private static final int STONE_SLAB = 53;
    private static final int SINGLE = 0;
    private static final int LOWER = 1;
    private static final int UPPER = 2;

    private static Expected amount(int block, boolean waterlogged, int amount, int half) {
        return Expected.block(block, NONE, waterlogged).withAmount(amount, half);
    }

    private static Found found(int block, boolean waterlogged, int amount, int half) {
        return Found.block(block, NONE, waterlogged, amount, half);
    }

    @Test
    void newStatesKeepTheStoredOrdinals() {
        assertEquals(9, CellState.INCOMPLETE.ordinal());
        assertEquals(10, CellState.TOO_MANY.ordinal());
        assertEquals(11, CellState.values().length);
    }

    @Test
    void incompleteShowsAGhostAndNeedsItems() {
        assertEquals(true, CellState.INCOMPLETE.showsGhost());
        assertEquals(true, CellState.INCOMPLETE.needsItem());
        assertEquals(false, CellState.INCOMPLETE.mistake());
        assertEquals(false, CellState.INCOMPLETE.boxed());
    }

    @Test
    void tooManyIsABoxedMistakeWithoutGhostOrItem() {
        assertEquals(false, CellState.TOO_MANY.showsGhost());
        assertEquals(false, CellState.TOO_MANY.needsItem());
        assertEquals(true, CellState.TOO_MANY.mistake());
        assertEquals(true, CellState.TOO_MANY.boxed());
    }

    @Test
    void candlesAreComparedByCount() {
        Expected three = amount(CANDLES, false, 3, SINGLE);
        assertEquals(CellState.INCOMPLETE, CellState.classify(three, found(CANDLES, false, 1, SINGLE)));
        assertEquals(CellState.INCOMPLETE, CellState.classify(three, found(CANDLES, false, 2, SINGLE)));
        assertEquals(CellState.MATCHING, CellState.classify(three, found(CANDLES, false, 3, SINGLE)));
        assertEquals(CellState.TOO_MANY, CellState.classify(three, found(CANDLES, false, 4, SINGLE)));
        assertEquals(CellState.MISSING, CellState.classify(three, Found.NOTHING));
    }

    @Test
    void snowLayersAreComparedByCount() {
        Expected five = amount(SNOW, false, 5, SINGLE);
        assertEquals(CellState.INCOMPLETE, CellState.classify(five, found(SNOW, false, 2, SINGLE)));
        assertEquals(CellState.MATCHING, CellState.classify(five, found(SNOW, false, 5, SINGLE)));
    }

    @Test
    void eitherSingleSlabUnderAPlannedDoubleIsIncomplete() {
        Expected doubleSlab = amount(STONE_SLAB, false, 2, SINGLE);
        assertEquals(CellState.INCOMPLETE, CellState.classify(doubleSlab, found(STONE_SLAB, false, 1, LOWER)));
        assertEquals(CellState.INCOMPLETE, CellState.classify(doubleSlab, found(STONE_SLAB, false, 1, UPPER)));
        assertEquals(CellState.MATCHING, CellState.classify(doubleSlab, found(STONE_SLAB, false, 2, SINGLE)));
    }

    @Test
    void aDoubleSlabWhereASingleIsPlannedIsTooMany() {
        assertEquals(CellState.TOO_MANY, CellState.classify(amount(STONE_SLAB, false, 1, LOWER), found(STONE_SLAB, false, 2, SINGLE)));
        assertEquals(CellState.TOO_MANY, CellState.classify(amount(STONE_SLAB, false, 1, UPPER), found(STONE_SLAB, false, 2, SINGLE)));
    }

    @Test
    void topAgainstBottomSlabIsWrongOrientation() {
        assertEquals(CellState.WRONG_ORIENTATION, CellState.classify(amount(STONE_SLAB, false, 1, LOWER), found(STONE_SLAB, false, 1, UPPER)));
        assertEquals(CellState.MATCHING, CellState.classify(amount(STONE_SLAB, false, 1, UPPER), found(STONE_SLAB, false, 1, UPPER)));
    }

    @Test
    void amountComesBeforeWater() {
        assertEquals(CellState.INCOMPLETE, CellState.classify(amount(STONE_SLAB, false, 2, SINGLE), found(STONE_SLAB, true, 1, LOWER)));
        assertEquals(CellState.TOO_MANY, CellState.classify(amount(PICKLES, true, 2, SINGLE), found(PICKLES, false, 3, SINGLE)));
        assertEquals(CellState.INCOMPLETE, CellState.classify(amount(PICKLES, true, 2, SINGLE), found(PICKLES, false, 1, SINGLE)));
        assertEquals(CellState.WRONG_WATER, CellState.classify(amount(PICKLES, true, 2, SINGLE), found(PICKLES, false, 2, SINGLE)));
        assertEquals(CellState.WRONG_WATER, CellState.classify(amount(STONE_SLAB, true, 1, LOWER), found(STONE_SLAB, false, 1, LOWER)));
    }

    @Test
    void orientationComesBeforeAmount() {
        Expected north = Expected.block(CANDLES, NORTH, false).withAmount(3, SINGLE);
        assertEquals(CellState.WRONG_ORIENTATION, CellState.classify(north, Found.block(CANDLES, EAST, false, 4, SINGLE)));
        assertEquals(CellState.WRONG_ORIENTATION, CellState.classify(north, Found.block(CANDLES, EAST, false, 1, SINGLE)));
        assertEquals(CellState.WRONG_BLOCK, CellState.classify(north, Found.block(PICKLES, NORTH, false, 1, SINGLE)));
    }

    @Test
    void amountComesBeforeFinishing() {
        Expected path = PATH_PLANNED.withAmount(2, SINGLE);
        assertEquals(CellState.INCOMPLETE, CellState.classify(path, found(DIRT, false, 1, SINGLE)));
        assertEquals(CellState.NEEDS_FINISHING, CellState.classify(path, found(DIRT, false, 2, SINGLE)));
    }

    @Test
    void blocksWithoutAmountsHaveOne() {
        assertEquals(1, Expected.block(OAK_PLANKS, NONE, false).amount());
        assertEquals(1, Found.block(OAK_PLANKS, NONE, false).amount());
        assertEquals(CellState.MATCHING, CellState.classify(Expected.block(OAK_PLANKS, NONE, false), Found.block(OAK_PLANKS, NONE, false)));
    }
}
