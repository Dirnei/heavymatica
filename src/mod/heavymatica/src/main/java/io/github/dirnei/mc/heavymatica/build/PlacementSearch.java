package io.github.dirnei.mc.heavymatica.build;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a click a vanilla client can send that makes the held item place a wanted state: tries candidate clicks on the
 * target position in order of preference and asks a placement function which state each gives. {@link VanillaPlacement}
 * supplies the game's own placement logic as that function.
 */
public final class PlacementSearch {

    /** A face of the clicked block, in the order and with the normals of the game's directions. */
    public enum Face {
        DOWN(0, -1, 0),
        UP(0, 1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);

        final int x;
        final int y;
        final int z;

        Face(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public boolean horizontal() {
            return y == 0;
        }
    }

    /**
     * A click on the target position.
     *
     * @param hitX   where on the block it lands, 0 to 1 from the block's minimum corner
     * @param turned true when the rotation differs from the player's own, so it has to be sent first
     */
    public record Click(Face face, double hitX, double hitY, double hitZ, float yaw, float pitch, boolean turned) {
    }

    /** A winning click and the state it places. */
    public record Found<S>(Click click, S state) {
    }

    private static final double EDGE = 0.1;

    private PlacementSearch() {
    }

    /**
     * The candidate clicks in order of preference: the player's own rotation first, then the four horizontal ones
     * level, then straight down and up; per rotation every face, and on side faces the centre and points near the
     * upper and lower edge.
     */
    public static List<Click> candidates(float ownYaw, float ownPitch) {
        List<float[]> rotations = new ArrayList<>();
        rotations.add(new float[] {ownYaw, ownPitch});
        for (float yaw : new float[] {0, 90, 180, 270}) {
            rotations.add(new float[] {yaw, 0});
        }
        rotations.add(new float[] {ownYaw, 90});
        rotations.add(new float[] {ownYaw, -90});
        List<Click> clicks = new ArrayList<>();
        for (int i = 0; i < rotations.size(); i++) {
            float yaw = rotations.get(i)[0];
            float pitch = rotations.get(i)[1];
            boolean turned = i > 0;
            for (Face face : Face.values()) {
                double x = 0.5 + face.x * 0.5;
                double z = 0.5 + face.z * 0.5;
                if (!face.horizontal()) {
                    clicks.add(new Click(face, x, face.y > 0 ? 1 : 0, z, yaw, pitch, turned));
                    continue;
                }
                for (double y : new double[] {0.5, 1 - EDGE, EDGE}) {
                    clicks.add(new Click(face, x, y, z, yaw, pitch, turned));
                }
            }
        }
        return clicks;
    }

    /**
     * A click on the centre of the face of the target the eye sees most squarely, with the player's own rotation; the
     * top face when the eye sees none.
     *
     * @param eyeX the eye's position relative to the target's minimum corner
     */
    public static Click visibleFace(double eyeX, double eyeY, double eyeZ, float yaw, float pitch) {
        Face best = Face.UP;
        double bestDistance = eyeY - 1;
        double[] distances = {-eyeY, eyeY - 1, -eyeZ, eyeZ - 1, -eyeX, eyeX - 1};
        for (Face face : Face.values()) {
            if (distances[face.ordinal()] > bestDistance) {
                best = face;
                bestDistance = distances[face.ordinal()];
            }
        }
        if (bestDistance <= 0) {
            best = Face.UP;
        }
        return new Click(best, 0.5 + best.x * 0.5, 0.5 + best.y * 0.5, 0.5 + best.z * 0.5, yaw, pitch, false);
    }

    /**
     * The first click whose placed state is accepted.
     *
     * @param place the state a click places, or null when it places nothing at the target position
     */
    public static <S> @Nullable Found<S> search(List<Click> clicks, Function<Click, @Nullable S> place, Predicate<S> accept) {
        for (Click click : clicks) {
            S state = place.apply(click);
            if (state != null && accept.test(state)) {
                return new Found<>(click, state);
            }
        }
        return null;
    }
}
