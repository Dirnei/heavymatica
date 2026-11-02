package io.github.dirnei.mc.heavymatica.targeting;

import org.jetbrains.annotations.Nullable;

/** The mistake pointer's arrow and height text, from the player's view and the candidate's position. */
public final class PointerDirection {

    /** Arrows by step, clockwise from straight ahead (up the screen). */
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /** A translation key and the number of blocks it names. */
    public record Height(String key, int blocks) {
    }

    private PointerDirection() {
    }

    /**
     * The direction to a point relative to where the player looks, in eighths clockwise from straight ahead: 0 ahead,
     * 2 right, 4 behind, 6 left; -1 when the point is straight above or below.
     *
     * @param yaw the player's look yaw in degrees, 0 facing +z and growing clockwise seen from above
     * @param dx  the point's x minus the eye's x
     * @param dz  the point's z minus the eye's z
     */
    public static int step(float yaw, double dx, double dz) {
        if (dx * dx + dz * dz < 0.25) {
            return -1;
        }
        double toPoint = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = toPoint - yaw;
        return Math.floorMod((int) Math.round(relative / 45), 8);
    }

    /** The arrow for a step, empty for -1. */
    public static String arrow(int step) {
        return step < 0 ? "" : ARROWS[step];
    }

    /** The height of a block above or below the eye's block, or null on the same level. */
    public static @Nullable Height height(int blockY, double eyeY) {
        int blocks = blockY - (int) Math.floor(eyeY);
        if (blocks == 0) {
            return null;
        }
        return blocks > 0 ? new Height("heavymatica.pointer.up", blocks) : new Height("heavymatica.pointer.down", -blocks);
    }
}
