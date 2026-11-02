package io.github.dirnei.mc.heavymatica.paste;

import io.github.dirnei.mc.heavymatica.placement.Placement;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Which placement a paste finished, so undoing the paste can put it back. A finish counts while the paste runs and for
 * {@value #GRACE_MILLIS} ms after it is done, since the comparison sees the last pasted blocks a little later; only the
 * first finish counts. Game thread only.
 */
final class PasteFinish {

    static final long GRACE_MILLIS = 30_000;

    private final UUID placementId;
    private long doneAtMillis = -1;
    private @Nullable Placement finished;

    PasteFinish(UUID placementId) {
        this.placementId = placementId;
    }

    void pasteDone(long nowMillis) {
        if (doneAtMillis < 0) {
            doneAtMillis = nowMillis;
        }
    }

    /** Called for every finished placement, as it was just before it was removed. */
    void finished(Placement placement, long nowMillis) {
        if (finished == null && placement.id().equals(placementId) && (doneAtMillis < 0 || nowMillis - doneAtMillis <= GRACE_MILLIS)) {
            finished = placement;
        }
    }

    Optional<Placement> finished() {
        return Optional.ofNullable(finished);
    }
}
