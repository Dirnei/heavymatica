package io.github.dirnei.mc.heavymatica.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PointerDirectionTest {

    @Test
    void stepsClockwiseFromWhereThePlayerLooks() {
        // Yaw 0 looks toward +z; +x is then on the left.
        assertEquals(0, PointerDirection.step(0, 0, 10));
        assertEquals(4, PointerDirection.step(0, 0, -10));
        assertEquals(2, PointerDirection.step(0, -10, 0));
        assertEquals(6, PointerDirection.step(0, 10, 0));
        assertEquals(1, PointerDirection.step(0, -10, 10));
        assertEquals(7, PointerDirection.step(0, 10, 10));
    }

    @Test
    void followsTheYaw() {
        // Yaw 90 looks toward -x, yaw 180 toward -z.
        assertEquals(0, PointerDirection.step(90, -10, 0));
        assertEquals(4, PointerDirection.step(180, 0, 10));
        assertEquals(4, PointerDirection.step(-180, 0, 10));
        assertEquals(0, PointerDirection.step(720, 0, 10));
        assertEquals(2, PointerDirection.step(-90, 0, 10));
    }

    @Test
    void roundsToTheNearestEighth() {
        assertEquals(0, PointerDirection.step(20, 0, 10));
        assertEquals(7, PointerDirection.step(25, 0, 10));
        assertEquals(1, PointerDirection.step(-25, 0, 10));
    }

    @Test
    void noStepStraightAboveOrBelow() {
        assertEquals(-1, PointerDirection.step(37, 0.2, -0.3));
        assertEquals("", PointerDirection.arrow(-1));
        assertEquals("↓", PointerDirection.arrow(4));
    }

    @Test
    void heightFromTheEyeBlock() {
        assertEquals(new PointerDirection.Height("heavymatica.pointer.up", 3), PointerDirection.height(68, 65.62));
        assertEquals(new PointerDirection.Height("heavymatica.pointer.down", 2), PointerDirection.height(63, 65.62));
        assertNull(PointerDirection.height(65, 65.62));
        assertEquals(new PointerDirection.Height("heavymatica.pointer.down", 1), PointerDirection.height(-2, -0.5));
    }
}
