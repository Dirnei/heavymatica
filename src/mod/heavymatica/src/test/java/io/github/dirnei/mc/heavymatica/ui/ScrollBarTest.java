package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScrollBarTest {

    private static final int TOP = 40;
    private static final int VIEW = 100;
    private static final int CONTENT = 400;

    @Test
    void thumbShrinksWithTheContentAndTracksTheOffset() {
        ScrollBar bar = new ScrollBar();
        assertEquals(25, bar.thumbHeight(CONTENT, VIEW));
        assertEquals(TOP, bar.thumbTop(TOP, CONTENT, VIEW));
        bar.scrollBy(300, CONTENT, VIEW);
        assertEquals(TOP + 75, bar.thumbTop(TOP, CONTENT, VIEW));
        assertEquals(12, bar.thumbHeight(10_000, VIEW));
    }

    @Test
    void scrollingStaysWithinTheList() {
        ScrollBar bar = new ScrollBar();
        bar.scrollBy(-50, CONTENT, VIEW);
        assertEquals(0, bar.offset());
        bar.scrollBy(1000, CONTENT, VIEW);
        assertEquals(300, bar.offset());
        bar.clamp(150, VIEW);
        assertEquals(50, bar.offset());
    }

    @Test
    void draggingTheThumbKeepsWhereItWasGrabbed() {
        ScrollBar bar = new ScrollBar();
        assertTrue(bar.press(TOP + 5, TOP, CONTENT, VIEW));
        assertEquals(0, bar.offset());
        bar.drag(TOP + 5 + 75, TOP, CONTENT, VIEW);
        assertEquals(300, bar.offset());
        bar.drag(TOP + 5 + 30, TOP, CONTENT, VIEW);
        assertEquals(120, bar.offset());
        bar.release();
        bar.drag(TOP, TOP, CONTENT, VIEW);
        assertEquals(120, bar.offset());
    }

    @Test
    void pressingTheTrackCentresTheThumbThere() {
        ScrollBar bar = new ScrollBar();
        assertTrue(bar.press(TOP + 80, TOP, CONTENT, VIEW));
        assertEquals(270, bar.offset(), 0.001);
        assertTrue(bar.dragging());
    }

    @Test
    void noTrackWhenEverythingFits() {
        ScrollBar bar = new ScrollBar();
        assertFalse(bar.visible(80, VIEW));
        assertFalse(bar.press(TOP + 10, TOP, 80, VIEW));
        assertFalse(bar.press(TOP - 1, TOP, CONTENT, VIEW));
        assertFalse(bar.dragging());
    }
}
