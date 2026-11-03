package io.github.dirnei.mc.heavymatica.ui.glass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class TextFitTest {

    /** Six units per character, as the game's font roughly is. */
    private static final ToIntFunction<String> WIDTH = text -> text.length() * 6;

    @Test
    void fittingTextIsUnchanged() {
        TextFit fit = TextFit.of("Starter Hut", 200, WIDTH);
        assertEquals("Starter Hut", fit.shown());
        assertFalse(fit.shortened());
    }

    @Test
    void longTextEndsInAnEllipsisAndFits() {
        TextFit fit = TextFit.of("Medieval Gatehouse with Drawbridge and Towers", 120, WIDTH);
        assertTrue(fit.shown().endsWith(TextFit.ELLIPSIS));
        assertTrue(WIDTH.applyAsInt(fit.shown()) <= 120);
        assertTrue(fit.shortened());
        assertEquals("Medieval Gatehouse with Drawbridge and Towers", fit.full());
    }

    @Test
    void noRoomLeavesOnlyTheEllipsis() {
        assertEquals(TextFit.ELLIPSIS, TextFit.of("Starter Hut", 0, WIDTH).shown());
    }

    @Test
    void noSpaceBeforeTheEllipsis() {
        assertEquals("Starter" + TextFit.ELLIPSIS, TextFit.of("Starter Hut", 8 * 6, WIDTH).shown());
    }
}
