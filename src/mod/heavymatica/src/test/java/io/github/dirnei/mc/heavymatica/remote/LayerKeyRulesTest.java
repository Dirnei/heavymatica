package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LayerKeyRulesTest {

    @Test
    void activeWithTheRemote() {
        assertTrue(LayerKeyRules.active(false, true, false));
    }

    @Test
    void activeWithoutTheRemoteOnALockedPlacement() {
        assertTrue(LayerKeyRules.active(false, false, true));
        assertTrue(LayerKeyRules.withoutRemote(false, false, true, false));
    }

    @Test
    void inactiveWithoutTheRemoteOnAnUnlockedPlacement() {
        assertFalse(LayerKeyRules.active(false, false, false));
        assertFalse(LayerKeyRules.withoutRemote(false, false, false, false));
    }

    @Test
    void inactiveBehindAScreen() {
        assertFalse(LayerKeyRules.active(true, true, true));
        assertFalse(LayerKeyRules.withoutRemote(true, false, true, false));
    }

    @Test
    void theRemoteKeepsItsOwnHandling() {
        assertFalse(LayerKeyRules.withoutRemote(false, true, true, false));
    }

    @Test
    void notWhileCapturing() {
        assertFalse(LayerKeyRules.withoutRemote(false, false, true, true));
    }
}
