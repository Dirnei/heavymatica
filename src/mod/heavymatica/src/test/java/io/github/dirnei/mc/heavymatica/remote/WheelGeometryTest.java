package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.remote.WheelGeometry.Span;
import java.util.List;
import org.junit.jupiter.api.Test;

class WheelGeometryTest {

    @Test
    void deadZoneIsTheCentre() {
        assertEquals(-1, WheelGeometry.segmentAt(0, 0, 5, 10));
        assertEquals(-1, WheelGeometry.segmentAt(6, -6, 5, 10));
        assertEquals(0, WheelGeometry.segmentAt(0, -11, 5, 10));
    }

    @Test
    void segmentZeroIsAtTheTopAndTheRestFollowClockwise() {
        assertEquals(0, WheelGeometry.segmentAt(0, -50, 4, 10));
        assertEquals(1, WheelGeometry.segmentAt(50, 0, 4, 10));
        assertEquals(2, WheelGeometry.segmentAt(0, 50, 4, 10));
        assertEquals(3, WheelGeometry.segmentAt(-50, 0, 4, 10));
    }

    @Test
    void twoSegmentsSplitTopAndBottom() {
        assertEquals(0, WheelGeometry.segmentAt(49, -1, 2, 10));
        assertEquals(1, WheelGeometry.segmentAt(49, 1, 2, 10));
        assertEquals(1, WheelGeometry.segmentAt(-49, 1, 2, 10));
        assertEquals(0, WheelGeometry.segmentAt(-49, -1, 2, 10));
    }

    @Test
    void sectorEdgesForFiveAndSixSegments() {
        // Five segments are 72 degrees wide; the edge between 0 and 1 is at 36 degrees.
        assertEquals(0, WheelGeometry.segmentAt(direction(35)[0], direction(35)[1], 5, 1));
        assertEquals(1, WheelGeometry.segmentAt(direction(37)[0], direction(37)[1], 5, 1));
        assertEquals(4, WheelGeometry.segmentAt(direction(-37)[0], direction(-37)[1], 5, 1));
        // Six segments are 60 degrees wide; segment 3 is centred at the bottom.
        assertEquals(3, WheelGeometry.segmentAt(direction(180)[0], direction(180)[1], 6, 1));
        assertEquals(2, WheelGeometry.segmentAt(direction(149)[0], direction(149)[1], 6, 1));
        assertEquals(3, WheelGeometry.segmentAt(direction(151)[0], direction(151)[1], 6, 1));
    }

    @Test
    void directionsBeyondTheRingStillPick() {
        assertEquals(1, WheelGeometry.segmentAt(5_000, 0, 4, 10));
    }

    @Test
    void oneSegmentTakesEveryDirection() {
        assertEquals(0, WheelGeometry.segmentAt(0, 40, 1, 10));
        assertEquals(0, WheelGeometry.segmentAt(-40, 0, 1, 10));
    }

    @Test
    void spansCoverEverySegmentAndLeaveTheGapsOut() {
        int inner = 20;
        int outer = 60;
        List<Span> spans = WheelGeometry.spans(inner, outer, 5, 4);
        for (int segment = 0; segment < 5; segment++) {
            int index = segment;
            assertTrue(spans.stream().anyMatch(span -> span.segment() == index), "segment " + segment);
        }
        for (Span span : spans) {
            for (int x = span.x0(); x < span.x1(); x++) {
                double px = x + 0.5;
                double py = span.y() + 0.5;
                double radius = Math.hypot(px, py);
                assertTrue(radius >= inner && radius < outer, "inside the ring");
                assertEquals(WheelGeometry.segmentAt(px, py, 5, 0), span.segment());
            }
        }
        // Straight up from the centre lies in segment 0; the edge to segment 1 at 36 degrees is a gap.
        assertTrue(covered(spans, 0, -40) == 0);
        double[] edge = direction(36);
        assertEquals(-1, covered(spans, (int) Math.floor(edge[0] * 40 / 50), (int) Math.floor(edge[1] * 40 / 50)));
        assertEquals(-1, covered(spans, 0, 0));
    }

    @Test
    void oneSegmentHasNoGaps() {
        List<Span> spans = WheelGeometry.spans(20, 60, 1, 4);
        assertEquals(0, covered(spans, 0, -40));
        assertEquals(0, covered(spans, 0, 39));
    }

    /** A point 50 away at the given angle, clockwise from 12 o'clock in degrees. */
    private static double[] direction(double degrees) {
        double radians = Math.toRadians(degrees);
        return new double[] {Math.sin(radians) * 50, -Math.cos(radians) * 50};
    }

    private static int covered(List<Span> spans, int x, int y) {
        return spans.stream().filter(span -> span.y() == y && x >= span.x0() && x < span.x1()).mapToInt(Span::segment).findFirst().orElse(-1);
    }
}
