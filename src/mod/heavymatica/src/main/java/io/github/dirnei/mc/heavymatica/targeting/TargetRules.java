package io.github.dirnei.mc.heavymatica.targeting;

import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.placement.CellState.Expected.Kind;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Which positions of a locked placement can be targeted, and which placement a shared position belongs to. */
public final class TargetRules {

    /** A placement that draws a targetable position, with what it holds there. */
    public record Candidate<T>(UUID placement, T value) {
    }

    private TargetRules() {
    }

    /**
     * True when the position is drawn: a ghost the in-hand filter shows (dimmed counts), or a mistake or finishing box,
     * in a visible layer.
     *
     * @param inHandShows true when the in-hand filter is off or shows the planned block at least dimmed
     */
    public static boolean drawn(Kind planned, CellState state, boolean inLayers, boolean inHandShows) {
        if (!inLayers || planned != Kind.BLOCK) {
            return false;
        }
        if (state.showsGhost()) {
            return inHandShows;
        }
        return state.boxed() && state != CellState.EXTRA;
    }

    /** The selected placement's candidate, otherwise the first; null without candidates. */
    public static <T> @Nullable Candidate<T> choose(List<Candidate<T>> candidates, @Nullable UUID selected) {
        for (Candidate<T> candidate : candidates) {
            if (candidate.placement().equals(selected)) {
                return candidate;
            }
        }
        return candidates.isEmpty() ? null : candidates.getFirst();
    }
}
