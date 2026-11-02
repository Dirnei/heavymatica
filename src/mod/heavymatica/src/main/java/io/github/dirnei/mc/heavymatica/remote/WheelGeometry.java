package io.github.dirnei.mc.heavymatica.remote;

/**
 * The wheel's shape, free of the game. Directions are screen offsets from the centre (y grows downwards). Segment 0 is
 * centred at 12 o'clock and the others follow clockwise.
 */
public final class WheelGeometry {

    private WheelGeometry() {
    }

    /** The segment pointed at, or -1 within the dead zone. Beyond the ring the direction alone decides. */
    public static int segmentAt(double dx, double dy, int count, double deadZone) {
        if (count <= 0 || Math.hypot(dx, dy) < deadZone) {
            return -1;
        }
        double sector = 2 * Math.PI / count;
        return Math.floorMod((int) Math.floor((angle(dx, dy) + sector / 2) / sector), count);
    }

    /** The angle of a segment's middle, clockwise from 12 o'clock, in radians. */
    public static double middle(int index, int count) {
        return index * 2 * Math.PI / count;
    }

    /** Clockwise from 12 o'clock, in [0, 2 pi). */
    private static double angle(double dx, double dy) {
        double angle = Math.atan2(dx, -dy);
        return angle < 0 ? angle + 2 * Math.PI : angle;
    }
}
