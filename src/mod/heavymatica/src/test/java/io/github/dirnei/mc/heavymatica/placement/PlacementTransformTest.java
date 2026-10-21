package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dirnei.mc.heavymatica.placement.Placement.Flip;
import io.github.dirnei.mc.heavymatica.placement.Placement.State;
import io.github.dirnei.mc.heavymatica.placement.Placement.Turn;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Bounds;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Facing;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Relative;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlacementTransformTest {

    static Placement placement(int width, int height, int length, State state) {
        return new Placement(UUID.randomUUID(), new PlacementSource.Local("abc"), "Test", width, height, length, 0, 64, 0, Turn.NONE, Flip.NONE,
                "minecraft:overworld", state, 0);
    }

    static Placement at(Placement placement, int minX, int y, int minZ) {
        Bounds bounds = PlacementTransform.bounds(placement);
        return placement.with(2 * minX + bounds.sizeX() - 1, y, 2 * minZ + bounds.sizeZ() - 1);
    }

    @Test
    void fourClockwiseTurnsRestoreTheBounds() {
        for (int[] size : new int[][] {{5, 3, 9}, {4, 2, 7}, {6, 1, 6}, {1, 1, 2}, {8, 5, 3}}) {
            Placement start = at(placement(size[0], size[1], size[2], State.ANCHORED), 10, 64, -7);
            Placement turned = start;
            for (int i = 0; i < 4; i++) {
                turned = PlacementTransform.rotate(turned, true);
            }
            assertEquals(PlacementTransform.bounds(start), PlacementTransform.bounds(turned));
            assertEquals(Turn.NONE, turned.rotation());
        }
    }

    @Test
    void counterClockwiseUndoesClockwise() {
        Placement start = at(placement(4, 2, 7, State.ANCHORED), 3, 64, 3);
        Placement back = PlacementTransform.rotate(PlacementTransform.rotate(start, true), false);
        assertEquals(PlacementTransform.bounds(start), PlacementTransform.bounds(back));
    }

    @Test
    void rotatingTurnsTheFootprintAroundItsCentre() {
        // an anchored 5 x 3 x 9 placement rotated clockwise becomes 9 x 5 around the same centre (rounded down).
        Placement start = at(placement(5, 3, 9, State.ANCHORED), 0, 64, 0);
        Bounds before = PlacementTransform.bounds(start);
        Placement turned = PlacementTransform.rotate(start, true);
        Bounds after = PlacementTransform.bounds(turned);

        assertEquals(9, after.sizeX());
        assertEquals(5, after.sizeZ());
        assertEquals(3, after.sizeY());
        assertEquals(Math.floorDiv(before.minX() + before.maxX() - 1, 2), Math.floorDiv(after.minX() + after.maxX() - 1, 2));
        assertEquals(Math.floorDiv(before.minZ() + before.maxZ() - 1, 2), Math.floorDiv(after.minZ() + after.maxZ() - 1, 2));
        assertEquals(State.ANCHORED, turned.state());
    }

    @Test
    void mirroringCyclesAndKeepsTheFootprint() {
        Placement start = at(placement(5, 3, 9, State.ANCHORED), 2, 64, 2);
        Placement once = PlacementTransform.mirror(start);
        Placement twice = PlacementTransform.mirror(once);

        assertEquals(Flip.LEFT_RIGHT, once.mirror());
        assertEquals(Flip.FRONT_BACK, twice.mirror());
        assertEquals(Flip.NONE, PlacementTransform.mirror(twice).mirror());
        assertEquals(PlacementTransform.bounds(start), PlacementTransform.bounds(twice));
    }

    @Test
    void placedMappingFollowsVanillaMirrorThenRotate() {
        int width = 4;
        int length = 7;
        int[] out = new int[2];
        for (Turn rotation : Turn.values()) {
            for (Flip mirror : Flip.values()) {
                // Vanilla StructureTemplate.transform around pivot 0, then shifted into the positive box.
                int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE};
                int[][] vanilla = new int[width * length][];
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < length; z++) {
                        int[] position = vanilla(rotation, mirror, x, z);
                        vanilla[x * length + z] = position;
                        min[0] = Math.min(min[0], position[0]);
                        min[1] = Math.min(min[1], position[1]);
                    }
                }
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < length; z++) {
                        PlacementTransform.toPlaced(rotation, mirror, width, length, x, z, out);
                        int[] expected = vanilla[x * length + z];
                        assertArrayEquals(new int[] {expected[0] - min[0], expected[1] - min[1]}, out, rotation + " " + mirror + " at " + x + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void schematicMappingInvertsPlacedMapping() {
        int width = 5;
        int length = 3;
        int[] placed = new int[2];
        int[] back = new int[2];
        for (Turn rotation : Turn.values()) {
            for (Flip mirror : Flip.values()) {
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < length; z++) {
                        PlacementTransform.toPlaced(rotation, mirror, width, length, x, z, placed);
                        PlacementTransform.toSchematic(rotation, mirror, width, length, placed[0], placed[1], back);
                        assertArrayEquals(new int[] {x, z}, back, rotation + " " + mirror);
                    }
                }
            }
        }
    }

    @Test
    void clockwiseTurnsNorthIntoEast() {
        // A north-facing stair in the middle of a 3 x 3 schematic "looks" at z - 1; after a clockwise turn it looks at x + 1.
        int[] stair = new int[2];
        int[] front = new int[2];
        PlacementTransform.toPlaced(Turn.CLOCKWISE_90, Flip.NONE, 3, 3, 1, 1, stair);
        PlacementTransform.toPlaced(Turn.CLOCKWISE_90, Flip.NONE, 3, 3, 1, 0, front);
        assertArrayEquals(new int[] {1, 0}, new int[] {front[0] - stair[0], front[1] - stair[1]});
    }

    @ParameterizedTest
    @CsvSource({
            // yaw, key, dx, dz
            "0, FORWARD, 0, 1",
            "90, FORWARD, -1, 0",
            "180, FORWARD, 0, -1",
            "-90, FORWARD, 1, 0",
            "270, FORWARD, 1, 0",
            "260, FORWARD, 1, 0",
            "180, BACK, 0, 1",
            "180, LEFT, -1, 0",
            "180, RIGHT, 1, 0",
            "-90, LEFT, 0, -1",
            "-90, RIGHT, 0, 1",
            "44, FORWARD, 0, 1",
            "46, FORWARD, -1, 0",
    })
    void movesRelativeToTheCamera(double yaw, Relative key, int dx, int dz) {
        Placement start = at(placement(3, 3, 3, State.ANCHORED), 0, 64, 0);
        Bounds before = PlacementTransform.bounds(start);
        Bounds after = PlacementTransform.bounds(PlacementTransform.move(start, key, yaw, 1));
        assertEquals(dx, after.minX() - before.minX());
        assertEquals(dz, after.minZ() - before.minZ());
        assertEquals(before.minY(), after.minY());
    }

    @Test
    void shiftMovesTenBlocks() {
        // Shift + left arrow while facing north moves 10 blocks west.
        Placement start = at(placement(4, 3, 6, State.ANCHORED), 0, 64, 0);
        Bounds after = PlacementTransform.bounds(PlacementTransform.move(start, Relative.LEFT, 180, 10));
        assertEquals(-10, after.minX());
        assertEquals(0, after.minZ());
    }

    @Test
    void facingSnapsLikeVanilla() {
        assertEquals(Facing.SOUTH, Facing.fromYaw(0));
        assertEquals(Facing.WEST, Facing.fromYaw(90));
        assertEquals(Facing.NORTH, Facing.fromYaw(180));
        assertEquals(Facing.EAST, Facing.fromYaw(-90));
        assertEquals(Facing.SOUTH, Facing.fromYaw(-360));
        assertEquals(Facing.NORTH, Facing.fromYaw(540));
    }

    @Test
    void followingCentresOnTheTargetBlock() {
        // looking at the top of 10, 63, 20 puts the bottom layer at y 64, centred on x 10, z 20.
        Placement following = placement(5, 4, 3, State.FOLLOWING);
        Bounds bounds = PlacementTransform.bounds(PlacementTransform.follow(following, 10, 64, 20));
        assertEquals(64, bounds.minY());
        assertEquals(8, bounds.minX());
        assertEquals(19, bounds.minZ());
        assertEquals(10, bounds.minX() + bounds.sizeX() / 2);
        assertEquals(20, bounds.minZ() + bounds.sizeZ() / 2);
    }

    @Test
    void followOffsetIsKeptWhileFollowing() {
        // numpad - twice while following puts the ghost two blocks below the target as it keeps following.
        Placement following = placement(3, 3, 3, State.FOLLOWING);
        following = PlacementTransform.moveVertical(PlacementTransform.moveVertical(following, -1), -1);
        assertEquals(-2, following.followOffset());
        assertEquals(62, PlacementTransform.bounds(PlacementTransform.follow(following, 0, 64, 0)).minY());
        assertEquals(98, PlacementTransform.bounds(PlacementTransform.follow(following, 0, 100, 0)).minY());
    }

    @Test
    void anchoringClearsTheFollowOffset() {
        Placement following = PlacementTransform.follow(PlacementTransform.moveVertical(placement(3, 3, 3, State.FOLLOWING), 2), 0, 64, 0);
        Placement anchored = PlacementTransform.anchor(following);
        assertEquals(State.ANCHORED, anchored.state());
        assertEquals(0, anchored.followOffset());
        assertEquals(66, anchored.y());
        assertEquals(67, PlacementTransform.moveVertical(anchored, 1).y());
    }

    /** {@code StructureTemplate.transform(pos, mirror, rotation, BlockPos.ZERO)} on x and z. */
    private static int[] vanilla(Turn rotation, Flip mirror, int x, int z) {
        int mx = mirror == Flip.FRONT_BACK ? -x : x;
        int mz = mirror == Flip.LEFT_RIGHT ? -z : z;
        return switch (rotation) {
            case NONE -> new int[] {mx, mz};
            case CLOCKWISE_90 -> new int[] {-mz, mx};
            case CLOCKWISE_180 -> new int[] {-mx, -mz};
            case COUNTERCLOCKWISE_90 -> new int[] {mz, -mx};
        };
    }
}
