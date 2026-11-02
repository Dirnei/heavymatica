package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    /** A point 50 away at the given angle, clockwise from 12 o'clock in degrees. */
    private static double[] direction(double degrees) {
        double radians = Math.toRadians(degrees);
        return new double[] {Math.sin(radians) * 50, -Math.cos(radians) * 50};
    }

}
