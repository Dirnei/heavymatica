package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class BlockChoicesTest {

    record Block(String id, String name, String kind) {
    }

    private static final Block OAK_STAIRS = new Block("minecraft:oak_stairs", "Oak Stairs", "stairs");
    private static final Block SPRUCE_STAIRS = new Block("minecraft:spruce_stairs", "Spruce Stairs", "stairs");
    private static final Block DEEPSLATE_STAIRS = new Block("minecraft:cobbled_deepslate_stairs", "Cobbled Deepslate Stairs", "stairs");
    private static final Block DEEPSLATE = new Block("minecraft:deepslate", "Deepslate", "pillar");
    private static final Block STONE = new Block("minecraft:stone", "Stone", "block");
    private static final List<Block> ALL = List.of(STONE, SPRUCE_STAIRS, DEEPSLATE, OAK_STAIRS, DEEPSLATE_STAIRS);

    private static BlockChoices<Block> choices(String query) {
        return BlockChoices.of(ALL, block -> block.kind().equals("stairs"), Block::name, Block::id, query);
    }

    @Test
    void sameKindComesFirstThenEverythingElseByName() {
        BlockChoices<Block> choices = choices("");
        assertEquals(List.of(DEEPSLATE_STAIRS, OAK_STAIRS, SPRUCE_STAIRS), choices.suggested());
        assertEquals(List.of(DEEPSLATE, STONE), choices.others());
    }

    @Test
    void searchMatchesNameOrIdIgnoringCase() {
        BlockChoices<Block> choices = choices("DeepSlate");
        assertEquals(List.of(DEEPSLATE_STAIRS), choices.suggested());
        assertEquals(List.of(DEEPSLATE), choices.others());
        assertEquals(List.of(STONE), choices("minecraft:sto").others());
        assertEquals(0, choices("nothing like it").size());
    }
}
