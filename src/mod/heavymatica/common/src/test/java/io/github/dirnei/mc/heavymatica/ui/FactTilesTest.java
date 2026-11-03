package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class FactTilesTest {

    @Test
    void everyFactWhenTheFileRecordsThem() {
        List<FactTiles.Tile> tiles = FactTiles.of("Litematica", "7 × 5 × 7", 214, 11, "1.20.1", "1 Nov 2026", false);
        assertEquals(List.of("heavymatica.details.fact.format", "heavymatica.details.fact.size", "heavymatica.details.fact.minecraft",
                "heavymatica.details.fact.blocks", "heavymatica.details.fact.types", "heavymatica.details.fact.added"),
                tiles.stream().map(FactTiles.Tile::labelKey).toList());
    }

    @Test
    void noGameVersionLineWithoutOne() {
        List<FactTiles.Tile> tiles = FactTiles.of("Structure", "3 × 3 × 3", 27, 1, null, null, false);
        assertFalse(tiles.stream().anyMatch(tile -> tile.labelKey().endsWith(".minecraft")));
        assertEquals(4, tiles.size());
    }

    @Test
    void uploadsSayUploaded() {
        List<FactTiles.Tile> tiles = FactTiles.of("Sponge", "1 × 1 × 1", 1, 1, null, "2 Nov 2026", true);
        assertEquals("heavymatica.details.fact.uploaded", tiles.getLast().labelKey());
    }
}
