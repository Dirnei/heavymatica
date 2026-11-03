package io.github.dirnei.mc.heavymatica.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CaptureSessionTest {

    private final CaptureSession session = new CaptureSession();

    @Test
    void starts_with_no_corners() {
        session.start("minecraft:overworld", -64, 319);

        assertTrue(session.on());
        assertEquals(0, session.box().orElseThrow().corners());
    }

    @Test
    void leaving_the_world_ends_capture_mode_and_drops_the_corners() {
        session.start("minecraft:overworld", -64, 319);
        session.box().orElseThrow().click(1, 64, 1);

        session.tick("");

        assertFalse(session.on());
        session.start("minecraft:overworld", -64, 319);
        assertEquals(0, session.box().orElseThrow().corners());
    }

    @Test
    void changing_dimension_ends_capture_mode() {
        session.start("minecraft:overworld", -64, 319);
        session.tick("minecraft:overworld");
        assertTrue(session.on());

        session.tick("minecraft:the_nether");

        assertFalse(session.on());
    }

    @Test
    void ending_clears_the_progress() {
        session.start("minecraft:overworld", -64, 319);
        session.setProgress(new CaptureSession.Progress(0.5, false));

        session.end();

        assertFalse(session.capturing());
    }
}
