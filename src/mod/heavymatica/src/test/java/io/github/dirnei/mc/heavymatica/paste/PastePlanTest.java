package io.github.dirnei.mc.heavymatica.paste;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.formats.BlockStates;
import io.github.dirnei.mc.heavymatica.formats.Nbt;
import io.github.dirnei.mc.heavymatica.formats.SchematicFormat;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.Placement.Flip;
import io.github.dirnei.mc.heavymatica.placement.Placement.Turn;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PastePlanTest {

    private static final Set<String> KNOWN = Set.of("minecraft:air", "minecraft:stone", "minecraft:oak_planks", "minecraft:spruce_planks",
            "minecraft:chest", "minecraft:structure_void", "minecraft:sand", "minecraft:dirt");

    /** A view like the ghost's, with block states as strings and blocks outside {@link #KNOWN} unknown. */
    private record FakeView(SchematicModel model, Turn rotation, Flip mirror, Map<String, String> swaps) implements PastePlan.View<String> {

        @Override
        public int sizeX() {
            return rotation.swapsAxes() ? model.length() : model.width();
        }

        @Override
        public int sizeY() {
            return model.height();
        }

        @Override
        public int sizeZ() {
            return rotation.swapsAxes() ? model.width() : model.length();
        }

        @Override
        public int paletteIndex(int x, int y, int z, int[] scratch) {
            PlacementTransform.toSchematic(rotation, mirror, model.width(), model.length(), x, z, scratch);
            return model.blocks()[SchematicModel.index(scratch[0], y, scratch[1], model.width(), model.length())];
        }

        @Override
        public String entry(int paletteIndex) {
            String original = model.palette().get(paletteIndex);
            String swapped = BlockStates.swapped(original, swaps);
            return KNOWN.contains(BlockStates.id(swapped)) ? swapped : original;
        }

        @Override
        public String placed(int paletteIndex) {
            String entry = entry(paletteIndex);
            return PastePlan.unknown(entry, !KNOWN.contains(BlockStates.id(entry))) ? null : entry;
        }
    }

    private record Cell(int x, int y, int z, String state) {
    }

    private static SchematicModel model(int width, int height, int length, List<String> palette, int[] blocks, Map<SchematicModel.Pos, Nbt.Compound> entities) {
        return new SchematicModel(SchematicFormat.LITEMATICA, width, height, length, palette, blocks, entities, null, 0);
    }

    private static PastePlan<String> plan(SchematicModel model, Turn rotation, Flip mirror, Map<String, String> swaps, int x, int y, int z) {
        return PastePlan.build(new FakeView(model, rotation, mirror, swaps), model, rotation, mirror, x, y, z);
    }

    private static List<Cell> cells(PastePlan<String> plan) {
        List<Cell> cells = new ArrayList<>();
        for (PastePlan.Section<String> section : plan.sections()) {
            for (int i = 0; i < section.size(); i++) {
                cells.add(new Cell(section.x(i), section.y(i), section.z(i), section.state(i)));
            }
        }
        return cells;
    }

    /** Width 3, length 2, one layer: a different block at every position, named by its schematic x and z. */
    private static SchematicModel numbered() {
        List<String> palette = new ArrayList<>();
        int[] blocks = new int[6];
        for (int z = 0; z < 2; z++) {
            for (int x = 0; x < 3; x++) {
                blocks[SchematicModel.index(x, 0, z, 3, 2)] = palette.size();
                palette.add("minecraft:stone[x=" + x + ",z=" + z + "]");
            }
        }
        return model(3, 1, 2, palette, blocks, Map.of());
    }

    @Test
    void rotatedPositionsFollowTheTransform() {
        PastePlan<String> plan = plan(numbered(), Turn.CLOCKWISE_90, Flip.NONE, Map.of(), 10, 64, 20);
        // Turned clockwise the 3 x 2 footprint is 2 wide and 3 long: schematic (x, z) lands on (1 - z, x).
        assertEquals(List.of(
                new Cell(10, 64, 20, "minecraft:stone[x=0,z=1]"), new Cell(11, 64, 20, "minecraft:stone[x=0,z=0]"),
                new Cell(10, 64, 21, "minecraft:stone[x=1,z=1]"), new Cell(11, 64, 21, "minecraft:stone[x=1,z=0]"),
                new Cell(10, 64, 22, "minecraft:stone[x=2,z=1]"), new Cell(11, 64, 22, "minecraft:stone[x=2,z=0]")), cells(plan));
        assertEquals(6, plan.total());
    }

    @Test
    void mirroredPositionsFollowTheTransform() {
        PastePlan<String> plan = plan(numbered(), Turn.NONE, Flip.FRONT_BACK, Map.of(), 0, 0, 0);
        assertEquals(List.of(
                new Cell(0, 0, 0, "minecraft:stone[x=2,z=0]"), new Cell(1, 0, 0, "minecraft:stone[x=1,z=0]"), new Cell(2, 0, 0, "minecraft:stone[x=0,z=0]"),
                new Cell(0, 0, 1, "minecraft:stone[x=2,z=1]"), new Cell(1, 0, 1, "minecraft:stone[x=1,z=1]"), new Cell(2, 0, 1, "minecraft:stone[x=0,z=1]")),
                cells(plan));
    }

    @Test
    void swapsChangeTheBlock() {
        SchematicModel model = model(2, 1, 1, List.of("minecraft:oak_planks", "minecraft:stone"), new int[] {0, 1}, Map.of());
        assertEquals(List.of(new Cell(0, 0, 0, "minecraft:spruce_planks"), new Cell(1, 0, 0, "minecraft:stone")),
                cells(plan(model, Turn.NONE, Flip.NONE, Map.of("minecraft:oak_planks", "minecraft:spruce_planks"), 0, 0, 0)));
    }

    @Test
    void unknownBlocksAndStructureVoidsAreLeftOut() {
        SchematicModel model = model(4, 1, 1, List.of("othermod:gadget[on=true]", "minecraft:structure_void", "minecraft:air", "minecraft:stone"),
                new int[] {0, 1, 2, 3}, Map.of());
        PastePlan<String> plan = plan(model, Turn.NONE, Flip.NONE, Map.of(), 0, 0, 0);
        assertEquals(List.of(new Cell(2, 0, 0, "minecraft:air"), new Cell(3, 0, 0, "minecraft:stone")), cells(plan));
        assertEquals(2, plan.total());
    }

    @Test
    void unknownMeansResolvedToAirWithoutNamingAir() {
        assertTrue(PastePlan.unknown("othermod:gadget", true));
        assertFalse(PastePlan.unknown("minecraft:air", true));
        assertFalse(PastePlan.unknown("minecraft:cave_air", true));
        assertFalse(PastePlan.unknown("minecraft:stone", false));
    }

    @Test
    void blockEntityDataMovesToTheWorldPosition() {
        LinkedHashMap<String, Nbt.Tag> chest = new LinkedHashMap<>();
        chest.put("id", new Nbt.Text("minecraft:chest"));
        chest.put("Items", new Nbt.ListTag(Nbt.COMPOUND, List.of()));
        SchematicModel model = model(3, 1, 2, List.of("minecraft:stone", "minecraft:chest"), new int[] {0, 0, 0, 0, 0, 1},
                Map.of(new SchematicModel.Pos(2, 0, 1), new Nbt.Compound(chest)));
        PastePlan<String> plan = plan(model, Turn.CLOCKWISE_90, Flip.NONE, Map.of(), 100, 70, -30);
        PastePlan.Section<String> section = plan.sections().getFirst();
        int found = 0;
        for (int i = 0; i < section.size(); i++) {
            Nbt.Compound tag = section.blockEntity(i);
            if (section.state(i).equals("minecraft:chest")) {
                // Schematic (2, 1) turned clockwise lands on (1 - 1, 2).
                assertEquals(List.of(100, 70, -28), List.of(section.x(i), section.y(i), section.z(i)));
                assertEquals("minecraft:chest", tag.string("id"));
                assertEquals(List.of(100, 70, -28), List.of(tag.integer("x"), tag.integer("y"), tag.integer("z")));
                assertTrue(tag.contains("Items"));
                found++;
            } else {
                assertNull(tag);
            }
        }
        assertEquals(1, found);
    }

    @Test
    void sectionsGoUpThenAlongZThenAlongX() {
        SchematicModel model = model(2, 2, 2, List.of("minecraft:stone"), new int[8], Map.of());
        PastePlan<String> plan = plan(model, Turn.NONE, Flip.NONE, Map.of(), -1, 15, -1);
        List<int[]> origins = plan.sections().stream().map(section -> new int[] {Math.floorDiv(section.x(0), 16), Math.floorDiv(section.y(0), 16),
                Math.floorDiv(section.z(0), 16)}).toList();
        assertEquals(8, origins.size());
        assertEquals(List.of(-1, 0, -1), List.of(origins.get(0)[0], origins.get(0)[1], origins.get(0)[2]));
        assertEquals(List.of(0, 0, -1), List.of(origins.get(1)[0], origins.get(1)[1], origins.get(1)[2]));
        assertEquals(List.of(-1, 0, 0), List.of(origins.get(2)[0], origins.get(2)[1], origins.get(2)[2]));
        assertEquals(List.of(-1, 1, -1), List.of(origins.get(4)[0], origins.get(4)[1], origins.get(4)[2]));
        assertEquals(8, plan.total());
        assertEquals(new Cell(-1, 15, -1, "minecraft:stone"), cells(plan).getFirst());
    }
}
