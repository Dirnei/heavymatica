package io.github.dirnei.mc.heavymatica.targeting;

import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.placement.ComparisonState;
import io.github.dirnei.mc.heavymatica.placement.Layers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the position of a locked placement nearest to the eye that the mistake pointer points at. Only sections that
 * hold candidates are scanned, nearest box first, until no remaining section can hold a nearer one.
 */
public final class MistakeSearch {

    /** A candidate in placement-local coordinates, with its distance from the eye to its centre. */
    public record Candidate(int x, int y, int z, CellState state, double distance) {
    }

    private record Box(int section, int[] min, int[] max, double distance) {
    }

    private MistakeSearch() {
    }

    /** True when a state is a candidate in a mode. */
    public static boolean candidate(CellState state, PointerMode mode) {
        return switch (mode) {
            case OFF -> false;
            case MISTAKES -> state.boxed();
            case MISTAKES_AND_MISSING -> state.boxed() || state == CellState.MISSING;
        };
    }

    /**
     * The nearest candidate in the visible layers, or null when there is none.
     *
     * @param eyeX the eye position relative to the placement's minimum corner
     */
    public static @Nullable Candidate nearest(ComparisonState state, double eyeX, double eyeY, double eyeZ, PointerMode mode, Layers.Range visible) {
        if (mode == PointerMode.OFF) {
            return null;
        }
        int fromY = Math.max(0, visible.from());
        int toY = Math.min(state.sizeY() - 1, visible.to());
        List<Box> boxes = new ArrayList<>();
        for (int section = 0; section < state.sectionCount(); section++) {
            int count = state.boxed(section) + (mode == PointerMode.MISTAKES_AND_MISSING ? state.missing(section) : 0);
            if (count == 0) {
                continue;
            }
            int[] min = state.sectionMin(section);
            int[] max = {Math.min(min[0] + ComparisonState.SECTION, state.sizeX()) - 1, Math.min(min[1] + ComparisonState.SECTION, state.sizeY()) - 1,
                    Math.min(min[2] + ComparisonState.SECTION, state.sizeZ()) - 1};
            min[1] = Math.max(min[1], fromY);
            max[1] = Math.min(max[1], toY);
            if (min[1] > max[1]) {
                continue;
            }
            boxes.add(new Box(section, min, max, boxDistance(min, max, eyeX, eyeY, eyeZ)));
        }
        boxes.sort(Comparator.comparingDouble(Box::distance));
        Candidate best = null;
        double bestSquared = Double.MAX_VALUE;
        for (Box box : boxes) {
            if (best != null && box.distance() * box.distance() >= bestSquared) {
                break;
            }
            for (int y = box.min()[1]; y <= box.max()[1]; y++) {
                for (int z = box.min()[2]; z <= box.max()[2]; z++) {
                    for (int x = box.min()[0]; x <= box.max()[0]; x++) {
                        CellState cell = state.state(state.index(x, y, z));
                        if (!candidate(cell, mode)) {
                            continue;
                        }
                        double dx = x + 0.5 - eyeX;
                        double dy = y + 0.5 - eyeY;
                        double dz = z + 0.5 - eyeZ;
                        double squared = dx * dx + dy * dy + dz * dz;
                        if (squared < bestSquared) {
                            bestSquared = squared;
                            best = new Candidate(x, y, z, cell, 0);
                        }
                    }
                }
            }
        }
        return best == null ? null : new Candidate(best.x(), best.y(), best.z(), best.state(), Math.sqrt(bestSquared));
    }

    /** The distance from the eye to the nearest cell centre a box could hold, 0 inside it. */
    private static double boxDistance(int[] min, int[] max, double eyeX, double eyeY, double eyeZ) {
        double dx = axis(eyeX, min[0] + 0.5, max[0] + 0.5);
        double dy = axis(eyeY, min[1] + 0.5, max[1] + 0.5);
        double dz = axis(eyeZ, min[2] + 0.5, max[2] + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double axis(double eye, double low, double high) {
        return eye < low ? low - eye : eye > high ? eye - high : 0;
    }
}
