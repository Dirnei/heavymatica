package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CarriedSharesTest {

    private static final int CONCRETE = 0;
    private static final int POWDER = 1;

    private static final int DIRT = 0;
    private static final int GRASS = 1;
    private static final int PATH = 2;
    private static final int PODZOL = 3;
    private static final int MYCELIUM = 4;
    private static final int COARSE_DIRT = 5;
    private static final int ROOTED_DIRT = 6;
    private static final int FARMLAND = 7;

    /** Planned white concrete and white concrete powder, in block ID order. */
    private static final int[][] CONCRETE_ACCEPTS = {{CONCRETE, POWDER}, {POWDER}};

    private static CarriedShares.Shares share(int[] need, int[][] accepts, int[] carried) {
        CarriedShares.Shares shares = CarriedShares.share(need, accepts, carried);
        assertTrue(Arrays.stream(shares.given()).sum() <= Arrays.stream(carried).sum());
        for (int item = 0; item < carried.length; item++) {
            assertTrue(shares.left()[item] >= 0 && shares.left()[item] <= carried[item]);
        }
        return shares;
    }

    @Test
    void concreteAndPowderEachTakeTheirOwn() {
        CarriedShares.Shares shares = share(new int[] {64, 64}, CONCRETE_ACCEPTS, new int[] {64, 64});
        assertArrayEquals(new int[] {64, 64}, shares.given());
        assertArrayEquals(new int[] {0, 0}, shares.left());
    }

    @Test
    void powderIsServedBeforeConcrete() {
        CarriedShares.Shares shares = share(new int[] {64, 64}, CONCRETE_ACCEPTS, new int[] {0, 96});
        assertArrayEquals(new int[] {32, 64}, shares.given());
    }

    @Test
    void concreteIsNotPowder() {
        CarriedShares.Shares shares = share(new int[] {64, 64}, CONCRETE_ACCEPTS, new int[] {64, 0});
        assertArrayEquals(new int[] {64, 0}, shares.given());
        CarriedShares.Shares powderOnly = share(new int[] {0, 64}, CONCRETE_ACCEPTS, new int[] {64, 0});
        assertArrayEquals(new int[] {0, 0}, powderOnly.given());
        assertArrayEquals(new int[] {64, 0}, powderOnly.left());
    }

    @Test
    void dirtGoesToDirtAndPodzolToPaths() {
        // Planned dirt and dirt paths; items: dirt, grass, path, podzol, mycelium, coarse dirt, rooted dirt.
        int[][] accepts = {{DIRT, GRASS}, {PATH, DIRT, GRASS, PODZOL, MYCELIUM, COARSE_DIRT, ROOTED_DIRT}};
        CarriedShares.Shares shares = share(new int[] {10, 10}, accepts, new int[] {10, 0, 0, 10, 0, 0, 0});
        assertArrayEquals(new int[] {10, 10}, shares.given());
        CarriedShares.Shares mixed = share(new int[] {0, 12}, accepts, new int[] {0, 0, 0, 3, 3, 3, 3});
        assertArrayEquals(new int[] {0, 12}, mixed.given());
    }

    @Test
    void pathItemsCoverFarmlandAndPaths() {
        // Planned dirt paths and farmland; items: dirt, grass, path, podzol, mycelium, coarse dirt, rooted dirt, farmland.
        int[][] accepts = {{PATH, DIRT, GRASS, PODZOL, MYCELIUM, COARSE_DIRT, ROOTED_DIRT}, {FARMLAND, DIRT, GRASS, PATH}};
        CarriedShares.Shares shares = share(new int[] {10, 10}, accepts, new int[] {0, 0, 20, 0, 0, 0, 0, 0});
        assertArrayEquals(new int[] {10, 10}, shares.given());
        assertArrayEquals(new int[8], shares.left());
    }

    @Test
    void spareStockOfABlockWithoutEquivalentsIsLeftOver() {
        CarriedShares.Shares shares = share(new int[] {300}, new int[][] {{0}}, new int[] {512, 7});
        assertArrayEquals(new int[] {300}, shares.given());
        assertArrayEquals(new int[] {212, 7}, shares.left());
        CarriedShares.Shares nothingNeeded = share(new int[] {0}, new int[][] {{0}}, new int[] {512, 7});
        assertArrayEquals(new int[] {512, 7}, nothingNeeded.left());
    }

    @Test
    void tiesGoByIndex() {
        // Two planned blocks that both accept one shared item: the first index is served first.
        CarriedShares.Shares shares = share(new int[] {5, 5}, new int[][] {{0}, {0}}, new int[] {7});
        assertArrayEquals(new int[] {5, 2}, shares.given());
    }
}
