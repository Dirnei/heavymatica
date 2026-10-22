package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.dirnei.mc.heavymatica.placement.BlockEquivalents.Equivalence;
import io.github.dirnei.mc.heavymatica.placement.BlockEquivalents.Finish;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlockEquivalentsTest {

    private static final String GRASS = "minecraft:grass_block";
    private static final String DIRT = "minecraft:dirt";
    private static final String PATH = "minecraft:dirt_path";
    private static final String FARMLAND = "minecraft:farmland";
    private static final String PODZOL = "minecraft:podzol";
    private static final String MYCELIUM = "minecraft:mycelium";
    private static final String COARSE_DIRT = "minecraft:coarse_dirt";
    private static final String ROOTED_DIRT = "minecraft:rooted_dirt";
    private static final List<String> COLOURS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
            "cyan", "purple", "blue", "brown", "green", "red", "black");

    private static Equivalence of(String planned) {
        return BlockEquivalents.of(planned);
    }

    @Test
    void grassIsPlacedWithDirtAndAcceptsDirt() {
        assertEquals(new Equivalence(DIRT, List.of(DIRT), List.of(), null), of(GRASS));
    }

    @Test
    void dirtIsPlacedWithDirtAndAcceptsGrass() {
        assertEquals(new Equivalence(DIRT, List.of(GRASS), List.of(), null), of(DIRT));
    }

    @Test
    void dirtPathNeedsAShovelOnEveryBlockAShovelFlattens() {
        assertEquals(new Equivalence(DIRT, List.of(), List.of(DIRT, GRASS, PODZOL, MYCELIUM, COARSE_DIRT, ROOTED_DIRT), Finish.SHOVEL), of(PATH));
    }

    @Test
    void farmlandNeedsAHoeOnDirtGrassOrAPath() {
        assertEquals(new Equivalence(DIRT, List.of(), List.of(DIRT, GRASS, PATH), Finish.HOE), of(FARMLAND));
    }

    @Test
    void strippedWoodNeedsAnAxeOnItsUnstrippedBlock() {
        assertEquals(new Equivalence("minecraft:oak_log", List.of(), List.of("minecraft:oak_log"), Finish.AXE), of("minecraft:stripped_oak_log"));
        assertEquals(new Equivalence("minecraft:dark_oak_wood", List.of(), List.of("minecraft:dark_oak_wood"), Finish.AXE),
                of("minecraft:stripped_dark_oak_wood"));
        assertEquals(new Equivalence("minecraft:crimson_stem", List.of(), List.of("minecraft:crimson_stem"), Finish.AXE),
                of("minecraft:stripped_crimson_stem"));
        assertEquals(new Equivalence("minecraft:warped_hyphae", List.of(), List.of("minecraft:warped_hyphae"), Finish.AXE),
                of("minecraft:stripped_warped_hyphae"));
        assertEquals(new Equivalence("minecraft:bamboo_block", List.of(), List.of("minecraft:bamboo_block"), Finish.AXE),
                of("minecraft:stripped_bamboo_block"));
    }

    @Test
    void moddedStrippedLogsUseTheirOwnNamespace() {
        assertEquals(new Equivalence("othermod:fir_log", List.of(), List.of("othermod:fir_log"), Finish.AXE), of("othermod:stripped_fir_log"));
    }

    @Test
    void everyConcreteColourNeedsWaterOnItsPowder() {
        for (String colour : COLOURS) {
            String powder = "minecraft:" + colour + "_concrete_powder";
            assertEquals(new Equivalence(powder, List.of(), List.of(powder), Finish.WATER), of("minecraft:" + colour + "_concrete"), colour);
        }
    }

    @Test
    void redPowderDoesNotFinishWhiteConcrete() {
        assertFalse(of("minecraft:white_concrete").finishFrom().contains("minecraft:red_concrete_powder"));
    }

    @Test
    void blocksWithoutARowAreThemselves() {
        assertEquals(Equivalence.self("minecraft:stripped_marble_block"), of("minecraft:stripped_marble_block"));
        assertEquals(Equivalence.self(PODZOL), of(PODZOL));
        assertEquals(Equivalence.self("minecraft:white_concrete_powder"), of("minecraft:white_concrete_powder"));
        assertEquals(new Equivalence(PODZOL, List.of(), List.of(), null), of(PODZOL));
        assertNull(of("minecraft:oak_planks").finish());
    }

    @Test
    void lookupsAreRemembered() {
        assertSame(of(PATH), of(PATH));
    }

    @Test
    void acceptedListsThePlannedBlockPlacingBlockSameThenFinishing() {
        assertEquals(List.of(GRASS, DIRT), of(GRASS).accepted(GRASS));
        assertEquals(List.of(DIRT, GRASS), of(DIRT).accepted(DIRT));
        assertEquals(List.of(PATH, DIRT, GRASS, PODZOL, MYCELIUM, COARSE_DIRT, ROOTED_DIRT), of(PATH).accepted(PATH));
        assertEquals(List.of(FARMLAND, DIRT, GRASS, PATH), of(FARMLAND).accepted(FARMLAND));
        assertEquals(List.of("minecraft:white_concrete", "minecraft:white_concrete_powder"),
                of("minecraft:white_concrete").accepted("minecraft:white_concrete"));
        assertEquals(List.of("minecraft:white_concrete_powder"), of("minecraft:white_concrete_powder").accepted("minecraft:white_concrete_powder"));
    }

    private static final List<String> PLANNED = List.of(GRASS, DIRT, PATH, FARMLAND, PODZOL, "minecraft:oak_log", "minecraft:stripped_oak_log",
            "minecraft:stripped_oak_wood", "minecraft:stripped_spruce_log", "minecraft:white_concrete", "minecraft:white_concrete_powder",
            "minecraft:oak_planks");

    @Test
    void holdingDirtOrGrassHighlightsGrassDirtPathAndFarmland() {
        assertEquals(List.of(GRASS, DIRT, PATH, FARMLAND), BlockEquivalents.highlights(DIRT, PLANNED));
        assertEquals(List.of(GRASS, DIRT, PATH, FARMLAND), BlockEquivalents.highlights(GRASS, PLANNED));
    }

    @Test
    void holdingPodzolHighlightsPodzolAndPathsNotFarmland() {
        assertEquals(List.of(PATH, PODZOL), BlockEquivalents.highlights(PODZOL, PLANNED));
    }

    @Test
    void holdingALogHighlightsItsOwnStrippedLogOnly() {
        assertEquals(List.of("minecraft:oak_log", "minecraft:stripped_oak_log"), BlockEquivalents.highlights("minecraft:oak_log", PLANNED));
    }

    @Test
    void powderHighlightsConcreteButConcreteNotPowder() {
        assertEquals(List.of("minecraft:white_concrete", "minecraft:white_concrete_powder"),
                BlockEquivalents.highlights("minecraft:white_concrete_powder", PLANNED));
        assertEquals(List.of("minecraft:white_concrete"), BlockEquivalents.highlights("minecraft:white_concrete", PLANNED));
    }

    @Test
    void holdingABlockWithoutARowHighlightsItself() {
        assertEquals(List.of("minecraft:oak_planks"), BlockEquivalents.highlights("minecraft:oak_planks", PLANNED));
        assertEquals(List.of(), BlockEquivalents.highlights("minecraft:stone", PLANNED));
    }
}
