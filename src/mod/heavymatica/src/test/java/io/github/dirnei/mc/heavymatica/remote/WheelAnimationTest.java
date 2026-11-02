package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WheelAnimationTest {

    private static final double EPS = 1e-9;

    @Test
    void openStartsSmallAndClearAndEndsInPlace() {
        WheelAnimation animation = new WheelAnimation(true, 1000);
        assertEquals(0, animation.openAlpha(1000), EPS);
        assertEquals(0.88, animation.openScale(1000), EPS);
        double alpha = animation.openAlpha(1085);
        assertTrue(alpha > 0.5 && alpha < 1);
        assertEquals(1, animation.openAlpha(1170), EPS);
        assertEquals(1, animation.openScale(1220), EPS);
    }

    @Test
    void openOvershootsSlightly() {
        WheelAnimation animation = new WheelAnimation(true, 0);
        double max = 0;
        for (long t = 0; t <= 220; t++) {
            max = Math.max(max, animation.openScale(t));
        }
        assertTrue(max > 1 && max < 1.05, "overshoot " + max);
    }

    @Test
    void segmentsArriveOneAfterAnother() {
        WheelAnimation animation = new WheelAnimation(true, 0);
        assertEquals(0, animation.arrival(0, 4, 0), EPS);
        assertTrue(animation.arrival(0, 4, 50) > animation.arrival(3, 4, 50));
        assertEquals(1, animation.arrival(3, 4, 250), EPS);
    }

    @Test
    void aFullWheelIsSettledWithin250Ms() {
        WheelAnimation animation = new WheelAnimation(true, 0);
        for (long t = 0; t <= 250; t += 16) {
            animation.update(t, 8, 3);
        }
        animation.update(250, 8, 3);
        assertEquals(1, animation.openAlpha(250), EPS);
        assertEquals(1, animation.openScale(250), EPS);
        assertEquals(1, animation.arrival(7, 8, 250), EPS);
        assertEquals(1, animation.hover(3), EPS);
        assertEquals(0, animation.hover(2), EPS);
        assertEquals(1, animation.accentShown(), EPS);
        assertEquals(WheelGeometry.middle(3, 8), animation.accentAngle(), EPS);
    }

    @Test
    void hoverEasesInAndOut() {
        WheelAnimation animation = new WheelAnimation(true, 0);
        animation.update(0, 4, 1);
        animation.update(25, 4, 1);
        double half = animation.hover(1);
        assertTrue(half > 0.3 && half < 0.6, "hover " + half);
        animation.update(300, 4, 2);
        assertEquals(0, animation.hover(1), EPS);
        assertEquals(1, animation.hover(2), EPS);
    }

    @Test
    void accentTakesTheShortWayAcrossTwelveOClock() {
        WheelAnimation animation = new WheelAnimation(true, 0);
        animation.update(0, 8, 7);
        animation.update(300, 8, 7);
        assertEquals(WheelGeometry.middle(7, 8), animation.accentAngle(), EPS);
        animation.update(316, 8, 1);
        double turned = WheelAnimation.shortest(animation.accentAngle() - WheelGeometry.middle(7, 8));
        assertTrue(turned > 0 && turned < Math.PI / 2, "turned " + turned);
    }

    @Test
    void shortestTurnStaysWithinHalfACircle() {
        assertEquals(-Math.PI / 2, WheelAnimation.shortest(3 * Math.PI / 2), EPS);
        assertEquals(Math.PI / 4, WheelAnimation.shortest(Math.PI / 4 - 2 * WheelMesh.TAU), EPS);
    }

    @Test
    void pageTurnFadesTheOldRingAndTurnsTheNewOneIn() {
        WheelAnimation animation = new WheelAnimation(true, 0);
        animation.update(300, 4, 1);
        animation.turnPage(300);
        assertTrue(animation.previousShown(300));
        assertEquals(1, animation.previousHover(1), EPS);
        assertEquals(1, animation.previousAlpha(300), EPS);
        assertEquals(WheelAnimation.PAGE_TURN, animation.pageRotation(300), EPS);
        assertEquals(0, animation.arrival(0, 3, 300), EPS);
        assertFalse(animation.previousShown(450));
        assertEquals(0, animation.pageRotation(520), EPS);
        assertEquals(-WheelAnimation.PAGE_TURN, animation.previousRotation(450), EPS);
        assertEquals(0, animation.hover(1), EPS);
    }

    @Test
    void closeFadesOutAndGrows() {
        assertEquals(1, WheelAnimation.closeAlpha(true, 0), EPS);
        assertEquals(0, WheelAnimation.closeAlpha(true, 170), EPS);
        assertEquals(1.07, WheelAnimation.closeScale(true, 170), EPS);
        assertEquals(0, WheelAnimation.closeAlpha(false, 0), EPS);
    }

    @Test
    void switchedOffEverythingIsAtItsTargetAtOnce() {
        WheelAnimation animation = new WheelAnimation(false, 0);
        animation.update(0, 8, 5);
        assertEquals(1, animation.openAlpha(0), EPS);
        assertEquals(1, animation.openScale(0), EPS);
        assertEquals(1, animation.arrival(7, 8, 0), EPS);
        assertEquals(1, animation.hover(5), EPS);
        assertEquals(1, animation.accentShown(), EPS);
        assertEquals(WheelGeometry.middle(5, 8), animation.accentAngle(), EPS);
        animation.update(0, 8, 2);
        assertEquals(WheelGeometry.middle(2, 8), animation.accentAngle(), EPS);
        animation.turnPage(0);
        assertFalse(animation.previousShown(0));
        assertEquals(0, animation.pageRotation(0), EPS);
        assertTrue(animation.settled(0));
    }

    @Test
    void settlesOnceStill() {
        WheelAnimation animation = new WheelAnimation(true, 0);
        animation.update(0, 4, -1);
        assertFalse(animation.settled(0));
        animation.update(500, 4, -1);
        assertTrue(animation.settled(500));
    }
}
