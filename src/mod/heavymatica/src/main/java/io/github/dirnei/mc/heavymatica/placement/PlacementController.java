package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.placement.Placement.Flip;
import io.github.dirnei.mc.heavymatica.placement.Placement.State;
import io.github.dirnei.mc.heavymatica.placement.Placement.Turn;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Relative;
import io.github.dirnei.mc.heavymatica.placement.Placements.Ray;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * What the remote's inputs do to the placements, free of the game: the caller turns key presses, clicks and the
 * crosshair into these calls. Every method acts on the selected placement only.
 */
public final class PlacementController {

    /** What a right-click did, so the caller can react (for example open the library). */
    public enum Use {
        ANCHORED,
        RELEASED,
        SELECTED,
        OPEN_LIBRARY,
        NOTHING,
    }

    public enum Remove {
        /** Pressed once on an anchored placement: press again within {@link #REMOVE_WINDOW_MILLIS}. */
        CONFIRM,
        REMOVED,
        NOTHING,
    }

    public static final long REMOVE_WINDOW_MILLIS = 3_000;
    public static final int SHIFT_STEP = 10;

    private final Placements placements;
    private @Nullable UUID removing;
    private long removingUntil;

    public PlacementController(Placements placements) {
        this.placements = placements;
    }

    public Placements placements() {
        return placements;
    }

    /**
     * Starts a new placement of a version, selected and FOLLOWING, at {@code at} until the crosshair finds a target. A
     * placement that was still following is dropped, since it was never anchored.
     */
    public Placement start(PlacementSource source, String name, int width, int height, int length, String dimension, Target at) {
        placements.selected().filter(Placement::following).ifPresent(previous -> placements.remove(previous.id()));
        Placement placement = new Placement(UUID.randomUUID(), source, name, width, height, length, 0, 0, 0, Turn.NONE, Flip.NONE, dimension,
                State.FOLLOWING, 0);
        placement = PlacementTransform.follow(placement, at.x(), at.y(), at.z());
        placements.add(placement);
        removing = null;
        return placement;
    }

    public Use use(boolean sneaking, String dimension, Ray ray) {
        Optional<Placement> selected = placements.selected();
        if (selected.isPresent() && selected.get().following()) {
            placements.update(PlacementTransform.anchor(selected.get()));
            return Use.ANCHORED;
        }
        if (sneaking) {
            if (selected.isPresent()) {
                placements.update(PlacementTransform.release(selected.get()));
                return Use.RELEASED;
            }
            return Use.NOTHING;
        }
        Optional<Placement> hit = placements.firstHit(dimension, ray);
        if (hit.isEmpty()) {
            return Use.OPEN_LIBRARY;
        }
        if (selected.isPresent() && selected.get().id().equals(hit.get().id())) {
            return Use.NOTHING;
        }
        placements.select(hit.get().id());
        removing = null;
        return Use.SELECTED;
    }

    /** Keeps a FOLLOWING selected placement on the crosshair target; a null target (sky) leaves it where it is. */
    public void follow(@Nullable Target target, String dimension) {
        placements.selected().filter(Placement::following).ifPresent(placement -> {
            if (target != null) {
                placements.update(PlacementTransform.follow(placement, target.x(), target.y(), target.z()).inDimension(dimension));
            }
        });
    }

    /** Arrow keys: only an ANCHORED placement moves; a following one stays on the crosshair. */
    public void move(Relative direction, double yaw, boolean shift) {
        placements.selected().filter(placement -> !placement.following())
                .ifPresent(placement -> placements.update(PlacementTransform.move(placement, direction, yaw, shift ? SHIFT_STEP : 1)));
    }

    /** Up / down keys; while following this moves the offset above the crosshair target. */
    public void vertical(int direction, boolean shift) {
        placements.selected().ifPresent(placement -> placements.update(PlacementTransform.moveVertical(placement, direction * (shift ? SHIFT_STEP : 1))));
    }

    public void rotate(boolean counterClockwise) {
        placements.selected().ifPresent(placement -> placements.update(PlacementTransform.rotate(placement, !counterClockwise)));
    }

    public void mirror() {
        placements.selected().ifPresent(placement -> placements.update(PlacementTransform.mirror(placement)));
    }

    /** Remove key: a following placement goes at once, an anchored one on the second press within the window. */
    public Remove remove(long nowMillis) {
        Optional<Placement> selected = placements.selected();
        if (selected.isEmpty()) {
            return Remove.NOTHING;
        }
        Placement placement = selected.get();
        if (placement.following() || placement.id().equals(removing) && nowMillis <= removingUntil) {
            placements.remove(placement.id());
            removing = null;
            return Remove.REMOVED;
        }
        removing = placement.id();
        removingUntil = nowMillis + REMOVE_WINDOW_MILLIS;
        return Remove.CONFIRM;
    }

    /** True while a first press of the remove key waits for the second, for the overlay. */
    public boolean removePending(long nowMillis) {
        return removing != null && nowMillis <= removingUntil && placements.selected().map(placement -> placement.id().equals(removing)).orElse(false);
    }

    /** The block a following ghost's bottom centre sits in: the one in front of the hit face. */
    public record Target(int x, int y, int z) {
    }
}
