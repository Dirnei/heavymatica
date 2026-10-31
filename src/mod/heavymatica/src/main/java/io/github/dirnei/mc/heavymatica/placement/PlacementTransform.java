package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.placement.Placement.Flip;
import io.github.dirnei.mc.heavymatica.placement.Placement.State;
import io.github.dirnei.mc.heavymatica.placement.Placement.Turn;

/**
 * Placement math, free of the game: bounds from the doubled pivot, rotating and mirroring in place, moving relative
 * to the camera, following the crosshair, and mapping placed blocks back to schematic positions. Mirroring is applied
 * before rotating, in schematic-local space, like vanilla structure templates.
 */
public final class PlacementTransform {

    /** A horizontal direction, in the order of vanilla's {@code Direction.from2DDataValue}. */
    public enum Facing {
        SOUTH(0, 1),
        WEST(-1, 0),
        NORTH(0, -1),
        EAST(1, 0);

        private final int dx;
        private final int dz;

        Facing(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public int dx() {
            return dx;
        }

        public int dz() {
            return dz;
        }

        /** Seen from above, like vanilla's {@code getClockWise}: NORTH to EAST. */
        public Facing clockwise() {
            return values()[(ordinal() + 1) % 4];
        }

        public Facing counterClockwise() {
            return values()[(ordinal() + 3) % 4];
        }

        public Facing opposite() {
            return values()[(ordinal() + 2) % 4];
        }

        /** Snaps a yaw (0 = south, 90 = west) to the nearest direction, like vanilla's {@code Direction.fromYRot}. */
        public static Facing fromYaw(double yaw) {
            return values()[(int) Math.floor(yaw / 90.0 + 0.5) & 3];
        }
    }

    /** Arrow-key directions relative to where the player looks. */
    public enum Relative {
        FORWARD,
        BACK,
        LEFT,
        RIGHT;

        public Facing resolve(Facing facing) {
            return switch (this) {
                case FORWARD -> facing;
                case BACK -> facing.opposite();
                case LEFT -> facing.counterClockwise();
                case RIGHT -> facing.clockwise();
            };
        }
    }

    /** An axis-aligned block box: the minimum corner and the size on each axis. */
    public record Bounds(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {

        public int maxX() {
            return minX + sizeX;
        }

        public int maxY() {
            return minY + sizeY;
        }

        public int maxZ() {
            return minZ + sizeZ;
        }

        public long volume() {
            return (long) sizeX * sizeY * sizeZ;
        }
    }

    private PlacementTransform() {
    }

    /** The world box the placement covers, after rotation. */
    public static Bounds bounds(Placement placement) {
        int sizeX = placement.rotation().swapsAxes() ? placement.length() : placement.width();
        int sizeZ = placement.rotation().swapsAxes() ? placement.width() : placement.length();
        return new Bounds(Math.floorDiv(placement.pivotX2() - (sizeX - 1), 2), placement.y(), Math.floorDiv(placement.pivotZ2() - (sizeZ - 1), 2),
                sizeX, placement.height(), sizeZ);
    }

    /** Turns 90 degrees around the footprint centre; the pivot stays, so four turns land on the same bounds. */
    public static Placement rotate(Placement placement, boolean clockwise) {
        Turn turned = clockwise ? placement.rotation().clockwise() : placement.rotation().counterClockwise();
        return placement.with(turned, placement.mirror());
    }

    /** Cycles none, left-right, front-back; the footprint stays. */
    public static Placement mirror(Placement placement) {
        return placement.with(placement.rotation(), placement.mirror().next());
    }

    /** Moves {@code step} blocks in a direction relative to the player's yaw, snapped to four directions. */
    public static Placement move(Placement placement, Relative direction, double yaw, int step) {
        Facing facing = direction.resolve(Facing.fromYaw(yaw));
        return placement.with(placement.pivotX2() + 2 * step * facing.dx(), placement.y(), placement.pivotZ2() + 2 * step * facing.dz());
    }

    /** Up or down; while following, this changes the offset above the crosshair target instead. */
    public static Placement moveVertical(Placement placement, int step) {
        if (placement.following()) {
            return placement.with(placement.pivotX2(), placement.y() + step, placement.pivotZ2()).with(State.FOLLOWING, placement.followOffset() + step);
        }
        return placement.with(placement.pivotX2(), placement.y() + step, placement.pivotZ2());
    }

    /** Sits the footprint, centred, on the target block (the block in front of the hit face), plus the follow offset. */
    public static Placement follow(Placement placement, int targetX, int targetY, int targetZ) {
        return placement.with(2 * targetX + 1, targetY + placement.followOffset(), 2 * targetZ + 1);
    }

    public static Placement anchor(Placement placement) {
        return placement.with(State.ANCHORED, 0);
    }

    /** Back to following the crosshair, from the target's height. */
    public static Placement release(Placement placement) {
        return placement.with(State.FOLLOWING, 0);
    }

    /**
     * Maps a position inside the placed box (0-based, after rotation) to the schematic position it shows. Writes x
     * and z into {@code out}; y never changes.
     */
    public static void toSchematic(Turn rotation, Flip mirror, int width, int length, int placedX, int placedZ, int[] out) {
        // Undo the rotation (placed box -> mirrored schematic box of width x length) ...
        int x;
        int z;
        switch (rotation) {
            case NONE -> {
                x = placedX;
                z = placedZ;
            }
            case CLOCKWISE_90 -> {
                x = placedZ;
                z = length - 1 - placedX;
            }
            case CLOCKWISE_180 -> {
                x = width - 1 - placedX;
                z = length - 1 - placedZ;
            }
            case COUNTERCLOCKWISE_90 -> {
                x = width - 1 - placedZ;
                z = placedX;
            }
            default -> throw new IllegalStateException();
        }
        // ... then the mirror, which is its own inverse.
        out[0] = mirror == Flip.FRONT_BACK ? width - 1 - x : x;
        out[1] = mirror == Flip.LEFT_RIGHT ? length - 1 - z : z;
    }

    /** The forward mapping: schematic position to placed position (0-based, after mirror and rotation). */
    public static void toPlaced(Turn rotation, Flip mirror, int width, int length, int x, int z, int[] out) {
        int mx = mirror == Flip.FRONT_BACK ? width - 1 - x : x;
        int mz = mirror == Flip.LEFT_RIGHT ? length - 1 - z : z;
        switch (rotation) {
            case NONE -> {
                out[0] = mx;
                out[1] = mz;
            }
            case CLOCKWISE_90 -> {
                out[0] = length - 1 - mz;
                out[1] = mx;
            }
            case CLOCKWISE_180 -> {
                out[0] = width - 1 - mx;
                out[1] = length - 1 - mz;
            }
            case COUNTERCLOCKWISE_90 -> {
                out[0] = mz;
                out[1] = width - 1 - mx;
            }
            default -> throw new IllegalStateException();
        }
    }
}
