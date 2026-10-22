package io.github.dirnei.mc.heavymatica.targeting;

import org.jetbrains.annotations.Nullable;

/** Walks the block cells a ray passes through, nearest first (Amanatides and Woo's voxel traversal). */
public final class GhostRay {

    public enum Answer {
        TARGET,
        BLOCKED,
        PASS,
    }

    /** What a cell holds for the ray. */
    @FunctionalInterface
    public interface Cells {

        /** @param entryDistance how far along the ray it enters the cell, 0 for the cell the ray starts in */
        Answer at(int x, int y, int z, double entryDistance);
    }

    /** The cell answered with {@link Answer#TARGET}, and where the ray enters it. */
    public record Hit(int x, int y, int z, double distance) {
    }

    private GhostRay() {
    }

    /**
     * The first cell answered with {@link Answer#TARGET}; null when a cell answers {@link Answer#BLOCKED} first, when
     * no cell entered within {@code maxDistance} is a target, or when the direction is zero. The direction need not be
     * normalised; distances are in blocks.
     */
    public static @Nullable Hit cast(double ox, double oy, double oz, double dx, double dy, double dz, double maxDistance, Cells cells) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length == 0.0) {
            return null;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        Axis ax = new Axis(ox, dx);
        Axis ay = new Axis(oy, dy);
        Axis az = new Axis(oz, dz);
        double t = 0.0;
        while (t <= maxDistance) {
            switch (cells.at(ax.cell, ay.cell, az.cell, t)) {
                case TARGET -> {
                    return new Hit(ax.cell, ay.cell, az.cell, t);
                }
                case BLOCKED -> {
                    return null;
                }
                case PASS -> {
                }
            }
            Axis next = ax.tMax < ay.tMax && ax.tMax < az.tMax ? ax : ay.tMax < az.tMax ? ay : az;
            t = next.tMax;
            next.step();
        }
        return null;
    }

    /** One axis of the traversal: the current cell, the distance to its next boundary and between boundaries. */
    private static final class Axis {

        private final int step;
        private final double tDelta;
        private int cell;
        private double tMax;

        Axis(double origin, double direction) {
            if (direction > 0.0) {
                step = 1;
                cell = (int) Math.floor(origin);
                tMax = (cell + 1 - origin) / direction;
                tDelta = 1.0 / direction;
            } else if (direction < 0.0) {
                step = -1;
                // On a boundary the ray leaves the cell above it at once, so it starts in the cell below.
                cell = (int) Math.ceil(origin) - 1;
                tMax = (origin - cell) / -direction;
                tDelta = 1.0 / -direction;
            } else {
                step = 0;
                cell = (int) Math.floor(origin);
                tMax = Double.POSITIVE_INFINITY;
                tDelta = Double.POSITIVE_INFINITY;
            }
        }

        void step() {
            cell += step;
            tMax += tDelta;
        }
    }
}
