package io.github.dirnei.mc.heavymatica.placement;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Decides when a locked placement is finished and remembers the latest finished one for a short time, so it can be
 * brought back. A brought-back placement does not finish again until it has been seen below complete with every
 * position compared. Game thread only.
 */
final class Finishes {

    static final long BRING_BACK_MILLIS = 10_000;

    private record Finished(Placement placement, long atMillis) {
    }

    private final Set<UUID> disarmed = new HashSet<>();
    private @Nullable Finished latest;

    /**
     * True when the placement with these counts is finished now.
     *
     * @param allCompared true when no position of the placement is unknown
     */
    boolean check(UUID id, int matching, int counted, boolean allCompared) {
        if (counted == 0) {
            return false;
        }
        if (matching < counted) {
            if (allCompared) {
                disarmed.remove(id);
            }
            return false;
        }
        return !disarmed.contains(id);
    }

    /** Remembers the placement as the one that can be brought back, replacing any earlier one. */
    void finish(Placement placement, long nowMillis) {
        latest = new Finished(placement, nowMillis);
    }

    boolean canBringBack(long nowMillis) {
        return latest != null && nowMillis - latest.atMillis() <= BRING_BACK_MILLIS;
    }

    /** The latest finished placement while it can still be brought back; it can be brought back only once. */
    Optional<Placement> bringBack(long nowMillis) {
        if (!canBringBack(nowMillis)) {
            latest = null;
            return Optional.empty();
        }
        Placement placement = latest.placement();
        latest = null;
        disarmed.add(placement.id());
        return Optional.of(placement);
    }

    void clear() {
        disarmed.clear();
        latest = null;
    }
}
