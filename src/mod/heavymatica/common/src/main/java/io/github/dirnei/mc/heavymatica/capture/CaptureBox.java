package io.github.dirnei.mc.heavymatica.capture;

import io.github.dirnei.mc.heavymatica.formats.SchematicLimits;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Facing;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Relative;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * The area being captured: up to two corner blocks, one of them active. Clicks set corner 1, then corner 2, then
 * move the active corner; keys move the active corner. Corners stay within the world's build height.
 */
public final class CaptureBox {

    public static final int SHIFT_STEP = 10;

    public record Corner(int x, int y, int z) {
    }

    /** The box both corners span, both included. */
    public record Bounds(int minX, int minY, int minZ, int width, int height, int length) {

        public long volume() {
            return (long) width * height * length;
        }

        public int maxX() {
            return minX + width - 1;
        }

        public int maxY() {
            return minY + height - 1;
        }

        public int maxZ() {
            return minZ + length - 1;
        }

        public boolean withinLimits() {
            return width <= SchematicLimits.MAX_DIMENSION && height <= SchematicLimits.MAX_DIMENSION && length <= SchematicLimits.MAX_DIMENSION
                    && volume() <= SchematicLimits.MAX_VOLUME;
        }
    }

    private final int minBuildY;
    private final int maxBuildY;
    private @Nullable Corner first;
    private @Nullable Corner second;
    private boolean secondActive;

    /** @param minBuildY lowest block y of the world, @param maxBuildY highest block y of the world */
    public CaptureBox(int minBuildY, int maxBuildY) {
        this.minBuildY = minBuildY;
        this.maxBuildY = maxBuildY;
    }

    public Optional<Corner> first() {
        return Optional.ofNullable(first);
    }

    public Optional<Corner> second() {
        return Optional.ofNullable(second);
    }

    public int corners() {
        return first == null ? 0 : second == null ? 1 : 2;
    }

    public boolean secondActive() {
        return secondActive;
    }

    public Optional<Corner> active() {
        return Optional.ofNullable(secondActive ? second : first);
    }

    public void click(int x, int y, int z) {
        Corner clicked = new Corner(x, clampY(y), z);
        if (first == null) {
            first = clicked;
            secondActive = false;
        } else if (second == null) {
            second = clicked;
            secondActive = true;
        } else {
            setActive(clicked);
        }
    }

    /** Moves the active corner one block, or {@link #SHIFT_STEP} with Shift, relative to where the player looks. */
    public void move(Relative direction, double yaw, boolean shift) {
        Corner corner = active().orElse(null);
        if (corner == null) {
            return;
        }
        Facing facing = direction.resolve(Facing.fromYaw(yaw));
        int step = shift ? SHIFT_STEP : 1;
        setActive(new Corner(corner.x() + facing.dx() * step, corner.y(), corner.z() + facing.dz() * step));
    }

    /** Moves the active corner up ({@code direction} 1) or down (-1). */
    public void vertical(int direction, boolean shift) {
        Corner corner = active().orElse(null);
        if (corner == null) {
            return;
        }
        setActive(new Corner(corner.x(), clampY(corner.y() + direction * (shift ? SHIFT_STEP : 1)), corner.z()));
    }

    /** Makes the other corner active; needs both corners. */
    public void swap() {
        if (second != null) {
            secondActive = !secondActive;
        }
    }

    public void reset() {
        first = null;
        second = null;
        secondActive = false;
    }

    public Optional<Bounds> bounds() {
        if (first == null || second == null) {
            return Optional.empty();
        }
        int minX = Math.min(first.x(), second.x()), minY = Math.min(first.y(), second.y()), minZ = Math.min(first.z(), second.z());
        return Optional.of(new Bounds(minX, minY, minZ, Math.abs(first.x() - second.x()) + 1, Math.abs(first.y() - second.y()) + 1,
                Math.abs(first.z() - second.z()) + 1));
    }

    private void setActive(Corner corner) {
        if (secondActive) {
            second = corner;
        } else {
            first = corner;
        }
    }

    private int clampY(int y) {
        return Math.clamp(y, minBuildY, maxBuildY);
    }
}
