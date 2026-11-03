package io.github.dirnei.mc.heavymatica.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.capture.CaptureBox.Corner;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Relative;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CaptureBoxTest {

    private static final double NORTH = 180;
    private static final double EAST = -90;

    private final CaptureBox box = new CaptureBox(-64, 319);

    @Test
    void two_clicks_set_both_corners_and_the_second_is_active() {
        box.click(10, 64, 10);
        assertEquals(1, box.corners());
        assertEquals(Optional.of(new Corner(10, 64, 10)), box.active());

        box.click(21, 64, 18);

        assertEquals(Optional.of(new Corner(10, 64, 10)), box.first());
        assertEquals(Optional.of(new Corner(21, 64, 18)), box.second());
        assertTrue(box.secondActive());
    }

    @Test
    void a_third_click_moves_the_active_corner() {
        box.click(10, 64, 10);
        box.click(21, 64, 18);
        box.swap();

        box.click(5, 70, 5);

        assertEquals(Optional.of(new Corner(5, 70, 5)), box.first());
        assertEquals(Optional.of(new Corner(21, 64, 18)), box.second());
    }

    @Test
    void numpad_plus_raises_the_top_without_climbing() {
        box.click(10, 64, 10);
        box.click(21, 64, 18);

        for (int i = 0; i < 8; i++) {
            box.vertical(1, false);
        }

        assertEquals(72, box.second().orElseThrow().y());
        assertEquals(9, box.bounds().orElseThrow().height());
    }

    @Test
    void shift_moves_ten_blocks_in_the_facing_direction() {
        box.click(0, 64, 0);

        box.move(Relative.FORWARD, NORTH, true);
        assertEquals(new Corner(0, 64, -10), box.active().orElseThrow());

        box.move(Relative.LEFT, EAST, false);
        assertEquals(new Corner(0, 64, -11), box.active().orElseThrow());

        box.vertical(-1, true);
        assertEquals(new Corner(0, 54, -11), box.active().orElseThrow());
    }

    @Test
    void corners_stay_within_the_build_height() {
        box.click(0, 319, 0);
        box.vertical(1, false);
        assertEquals(319, box.active().orElseThrow().y());

        box.click(0, -64, 0);
        box.vertical(-1, true);
        assertEquals(-64, box.active().orElseThrow().y());

        box.click(0, 400, 0);
        assertEquals(319, box.active().orElseThrow().y());
    }

    @Test
    void keys_do_nothing_without_a_corner() {
        box.move(Relative.FORWARD, NORTH, false);
        box.vertical(1, false);
        box.swap();

        assertEquals(0, box.corners());
    }

    @Test
    void swapping_needs_both_corners() {
        box.click(1, 64, 1);
        box.swap();
        assertFalse(box.secondActive());
        assertEquals(Optional.of(new Corner(1, 64, 1)), box.active());

        box.click(2, 64, 2);
        box.swap();
        assertEquals(Optional.of(new Corner(1, 64, 1)), box.active());
        box.swap();
        assertEquals(Optional.of(new Corner(2, 64, 2)), box.active());
    }

    @Test
    void bounds_include_both_corners_in_any_order() {
        box.click(21, 72, 18);
        box.click(10, 64, 10);

        CaptureBox.Bounds bounds = box.bounds().orElseThrow();

        assertEquals(new CaptureBox.Bounds(10, 64, 10, 12, 9, 9), bounds);
        assertEquals(21, bounds.maxX());
        assertEquals(972, bounds.volume());
        assertTrue(bounds.withinLimits());
    }

    @Test
    void boxes_past_the_format_limits_are_flagged() {
        assertFalse(new CaptureBox.Bounds(0, 0, 0, 1030, 1, 1).withinLimits());
        assertFalse(new CaptureBox.Bounds(0, 0, 0, 1024, 17, 1024).withinLimits());
        assertTrue(new CaptureBox.Bounds(0, 0, 0, 1024, 16, 1024).withinLimits());
    }

    @Test
    void reset_clears_both_corners() {
        box.click(1, 64, 1);
        box.click(2, 64, 2);

        box.reset();

        assertEquals(0, box.corners());
        assertEquals(Optional.empty(), box.bounds());
    }
}
