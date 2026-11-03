package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LayerKeyRulesTest {

    @Test
    void layerKeysActiveWithTheRemote() {
        assertTrue(LayerKeyRules.active(false, true, false));
    }

    @Test
    void layerKeysActiveWithoutTheRemoteOnALockedPlacement() {
        assertTrue(LayerKeyRules.active(false, false, true));
    }

    @Test
    void layerKeysInactiveWithoutTheRemoteOnAnUnlockedPlacement() {
        assertFalse(LayerKeyRules.active(false, false, false));
    }

    @Test
    void layerKeysInactiveBehindAScreen() {
        assertFalse(LayerKeyRules.active(true, true, true));
    }

    @Test
    void wheelKeysActiveWithTheRemote() {
        assertTrue(LayerKeyRules.wheelActive(false, true, false, false));
        assertTrue(LayerKeyRules.wheelActive(false, true, false, true));
    }

    @Test
    void wheelKeysActiveWithoutTheRemoteWithASelection() {
        assertTrue(LayerKeyRules.wheelActive(false, false, true, false));
    }

    @Test
    void wheelKeysNeedTheRemoteWithoutASelection() {
        assertFalse(LayerKeyRules.wheelActive(false, false, false, false));
    }

    @Test
    void wheelKeysNeedTheRemoteInCaptureMode() {
        assertFalse(LayerKeyRules.wheelActive(false, false, true, true));
        assertFalse(LayerKeyRules.wheelAllowed(false, true, true));
    }

    @Test
    void wheelKeysInactiveBehindAScreen() {
        assertFalse(LayerKeyRules.wheelActive(true, true, true, false));
    }

    @Test
    void openWheelStaysOpenWithASelectionAfterTheRemoteIsPutAway() {
        assertTrue(LayerKeyRules.wheelAllowed(false, true, false));
        assertTrue(LayerKeyRules.wheelAllowed(true, false, false));
        assertFalse(LayerKeyRules.wheelAllowed(false, false, false));
    }
}
