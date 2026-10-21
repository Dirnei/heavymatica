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
        /** Pressed once on an anchored placement: press again within {@link #CONFIRM_WINDOW_MILLIS}. */
        CONFIRM,
        REMOVED,
        NOTHING,
    }

    public enum Unlock {
        /** Pressed once on a locked placement: press again within {@link #CONFIRM_WINDOW_MILLIS}. */
        CONFIRM,
        UNLOCKED,
    }

    /** An action that waits for a second press. */
    public enum Pending {
        REMOVE,
        UNLOCK,
    }

    public static final long CONFIRM_WINDOW_MILLIS = 3_000;
    public static final int SHIFT_STEP = 10;

    private final Placements placements;
    private @Nullable Pending pending;
    private @Nullable UUID pendingId;
    private long pendingUntil;

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
                State.FOLLOWING, 0, Layers.NONE, false);
        placement = PlacementTransform.follow(placement, at.x(), at.y(), at.z());
        placements.add(placement);
        pending = null;
        return placement;
    }

    public Use use(boolean sneaking, String dimension, Ray ray) {
        Optional<Placement> selected = placements.selected();
        if (selected.isPresent() && selected.get().following()) {
            placements.update(PlacementTransform.anchor(selected.get()));
            return Use.ANCHORED;
        }
        if (sneaking) {
            if (selected.isPresent() && selected.get().state() == State.ANCHORED) {
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
        pending = null;
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
        placements.selected().filter(placement -> placement.state() == State.ANCHORED)
                .ifPresent(placement -> placements.update(PlacementTransform.move(placement, direction, yaw, shift ? SHIFT_STEP : 1)));
    }

    /** Up / down keys; while following this moves the offset above the crosshair target. */
    public void vertical(int direction, boolean shift) {
        movable().ifPresent(placement -> placements.update(PlacementTransform.moveVertical(placement, direction * (shift ? SHIFT_STEP : 1))));
    }

    public void rotate(boolean counterClockwise) {
        movable().ifPresent(placement -> placements.update(PlacementTransform.rotate(placement, !counterClockwise)));
    }

    public void mirror() {
        movable().ifPresent(placement -> placements.update(PlacementTransform.mirror(placement)));
    }

    /** Remove key: a following placement goes at once, an anchored one on the second press within the window. */
    public Remove remove(long nowMillis) {
        Optional<Placement> selected = placements.selected();
        if (selected.isEmpty()) {
            return Remove.NOTHING;
        }
        Placement placement = selected.get();
        if (placement.locked()) {
            return Remove.NOTHING;
        }
        if (placement.following() || confirmed(Pending.REMOVE, placement, nowMillis)) {
            placements.remove(placement.id());
            pending = null;
            return Remove.REMOVED;
        }
        await(Pending.REMOVE, placement, nowMillis);
        return Remove.CONFIRM;
    }

    /** True while a first press of the remove key waits for the second, for the overlay. */
    public boolean removePending(long nowMillis) {
        return pending(Pending.REMOVE, nowMillis);
    }

    /** Lock key: locks an ANCHORED placement, and unlocks a LOCKED one on the second press within the window. */
    public Optional<Unlock> lockOrUnlock(long nowMillis) {
        Optional<Placement> selected = placements.selected();
        if (selected.isEmpty() || selected.get().following()) {
            return Optional.empty();
        }
        Placement placement = selected.get();
        if (placement.state() == State.ANCHORED) {
            placements.update(placement.with(State.LOCKED, 0));
            pending = null;
            return Optional.empty();
        }
        if (confirmed(Pending.UNLOCK, placement, nowMillis)) {
            placements.update(placement.with(State.ANCHORED, 0));
            pending = null;
            return Optional.of(Unlock.UNLOCKED);
        }
        await(Pending.UNLOCK, placement, nowMillis);
        return Optional.of(Unlock.CONFIRM);
    }

    /** True while a first press of the lock key on a locked placement waits for the second, for the overlay. */
    public boolean unlockPending(long nowMillis) {
        return pending(Pending.UNLOCK, nowMillis);
    }

    /** Cycles off, from-to and follow feet; the first switch to from-to opens a one-layer window at the feet. */
    public void cycleLayers(int direction, int feetLayer) {
        locked().ifPresent(placement -> {
            Layers layers = placement.layers();
            Layers.Mode mode = layers.mode().step(direction);
            if (mode == Layers.Mode.FROM_TO && !layers.windowSet()) {
                int layer = Layers.clamp(feetLayer, placement.height() - 1);
                placements.update(placement.with(new Layers(mode, layer, layer)));
            } else {
                placements.update(placement.with(new Layers(mode, layers.bottom(), layers.top())));
            }
        });
    }

    /** Moves the from-to window by one layer, keeping its size, within the placement. */
    public void moveWindow(int direction) {
        fromTo().ifPresent(placement -> {
            Layers layers = placement.layers();
            int size = layers.top() - layers.bottom();
            int bottom = Math.max(0, Math.min(layers.bottom() + direction, placement.height() - 1 - size));
            placements.update(placement.with(new Layers(layers.mode(), bottom, bottom + size)));
        });
    }

    /** Raises or lowers the top of the from-to window; it keeps at least one layer. */
    public void resizeWindow(int direction) {
        fromTo().ifPresent(placement -> {
            Layers layers = placement.layers();
            int top = Math.max(layers.bottom(), Math.min(layers.top() + direction, placement.height() - 1));
            placements.update(placement.with(new Layers(layers.mode(), layers.bottom(), top)));
        });
    }

    public void toggleInHand() {
        locked().ifPresent(placement -> placements.update(placement.withInHand(!placement.inHand())));
    }

    private Optional<Placement> movable() {
        return placements.selected().filter(placement -> !placement.locked());
    }

    private Optional<Placement> locked() {
        return placements.selected().filter(Placement::locked);
    }

    private Optional<Placement> fromTo() {
        return locked().filter(placement -> placement.layers().mode() == Layers.Mode.FROM_TO && placement.layers().windowSet());
    }

    private boolean confirmed(Pending action, Placement placement, long nowMillis) {
        return pending == action && placement.id().equals(pendingId) && nowMillis <= pendingUntil;
    }

    private void await(Pending action, Placement placement, long nowMillis) {
        pending = action;
        pendingId = placement.id();
        pendingUntil = nowMillis + CONFIRM_WINDOW_MILLIS;
    }

    private boolean pending(Pending action, long nowMillis) {
        return pending == action && nowMillis <= pendingUntil
                && placements.selected().map(placement -> placement.id().equals(pendingId)).orElse(false);
    }

    /** The block a following ghost's bottom centre sits in: the one in front of the hit face. */
    public record Target(int x, int y, int z) {
    }
}
