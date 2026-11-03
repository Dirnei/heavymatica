package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class UnknownBlocksTest {

    @Test
    void countsPositionsPerShownId() {
        // Entries 1 and 3 are both unknown create:shaft (different axes), entry 2 is an unknown cogwheel.
        String[] unknown = {null, "create:shaft", "create:cogwheel", "create:shaft", null};
        int[] blocks = {0, 1, 1, 3, 2, 4, 0, 3};

        UnknownBlocks counted = UnknownBlocks.count(blocks, unknown.length, index -> unknown[index]);

        assertTrue(counted.any());
        assertEquals(Map.of("create:cogwheel", 1, "create:shaft", 4), counted.positions());
        assertEquals(2, counted.types());
        assertEquals(5, counted.total());
        assertEquals("create:cogwheel", counted.positions().keySet().iterator().next());
    }

    @Test
    void nothingUnknown() {
        UnknownBlocks counted = UnknownBlocks.count(new int[] {0, 1, 1}, 2, index -> null);

        assertSame(UnknownBlocks.NONE, counted);
        assertFalse(counted.any());
        assertEquals(0, counted.total());
    }

    @Test
    void unusedEntriesAreNotAsked() {
        UnknownBlocks counted = UnknownBlocks.count(new int[] {0, 0}, 2, index -> {
            if (index == 1) {
                throw new AssertionError("entry 1 has no positions");
            }
            return null;
        });

        assertFalse(counted.any());
    }
}
