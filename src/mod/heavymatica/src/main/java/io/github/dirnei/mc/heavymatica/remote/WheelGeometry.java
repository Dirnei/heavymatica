package io.github.dirnei.mc.heavymatica.remote;

import java.util.ArrayList;
import java.util.List;

/**
 * The wheel's shape, free of the game. Directions are screen offsets from the centre (y grows downwards). Segment 0 is
 * centred at 12 o'clock and the others follow clockwise.
 */
public final class WheelGeometry {

    /** A horizontal run of pixels in row {@code y}, from {@code x0} to {@code x1} exclusive, relative to the centre. */
    public record Span(int y, int x0, int x1, int segment) {
    }

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

    /**
     * The ring between {@code inner} and {@code outer} as runs per segment, leaving out pixels closer than half the
     * {@code gap} to a sector edge so the segments stand apart. A single segment has no edges.
     */
    public static List<Span> spans(int inner, int outer, int count, double gap) {
        List<Span> spans = new ArrayList<>();
        double sector = 2 * Math.PI / count;
        for (int y = -outer; y < outer; y++) {
            int start = 0;
            int current = -1;
            for (int x = -outer; x <= outer; x++) {
                int segment = x == outer ? -1 : pixel(x + 0.5, y + 0.5, inner, outer, count, sector, gap);
                if (segment != current) {
                    if (current >= 0) {
                        spans.add(new Span(y, start, x, current));
                    }
                    current = segment;
                    start = x;
                }
            }
        }
        return spans;
    }

    private static int pixel(double px, double py, int inner, int outer, int count, double sector, double gap) {
        double radius = Math.hypot(px, py);
        if (radius < inner || radius >= outer) {
            return -1;
        }
        if (count > 1) {
            double offset = (angle(px, py) + sector / 2) % sector;
            double fromEdge = Math.min(offset, sector - offset);
            // The epsilon keeps pixel rows that sit exactly on the gap's border from flickering in and out.
            if (radius * Math.sin(fromEdge) < gap / 2 + 1e-6) {
                return -1;
            }
        }
        return segmentAt(px, py, count, 0);
    }

    /** Clockwise from 12 o'clock, in [0, 2 pi). */
    private static double angle(double dx, double dy) {
        double angle = Math.atan2(dx, -dy);
        return angle < 0 ? angle + 2 * Math.PI : angle;
    }
}
