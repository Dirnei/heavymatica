package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OverlayRulesTest {

    @Test
    void showsWithTheRemote() {
        assertTrue(OverlayRules.shows(false, false, true, false, false, false));
    }

    @Test
    void hiddenWithoutTheRemoteWhenNotPinned() {
        assertFalse(OverlayRules.shows(false, false, false, false, true, false));
    }

    @Test
    void pinnedShowsWithoutTheRemoteWithASelection() {
        assertTrue(OverlayRules.shows(false, false, false, true, true, false));
    }

    @Test
    void pinnedNeedsASelection() {
        assertFalse(OverlayRules.shows(false, false, false, true, false, false));
    }

    @Test
    void pinnedNotInCaptureModeWithoutTheRemote() {
        assertFalse(OverlayRules.shows(false, false, false, true, true, true));
    }

    @Test
    void hiddenWithF1OrAScreen() {
        assertFalse(OverlayRules.shows(true, false, true, true, true, false));
        assertFalse(OverlayRules.shows(false, true, true, true, true, false));
    }
}
