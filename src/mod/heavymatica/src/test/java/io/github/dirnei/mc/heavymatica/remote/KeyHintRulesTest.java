package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.remote.KeyHintRules.Hint;
import io.github.dirnei.mc.heavymatica.remote.KeyHintRules.Selected;
import io.github.dirnei.mc.heavymatica.remote.KeyHintRules.Situation;
import io.github.dirnei.mc.heavymatica.remote.KeyHintRules.WheelKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KeyHintRulesTest {

    private static Situation held(Selected selected, Layers.Mode layers, Set<WheelKey> bound) {
        return new Situation(true, false, 0, false, selected, layers, false, false, bound);
    }

    private static Situation withoutRemote(Selected selected, Layers.Mode layers, Set<WheelKey> bound) {
        return new Situation(false, false, 0, false, selected, layers, false, false, bound);
    }

    private static Situation capture(boolean remote, int corners, boolean busy) {
        return new Situation(remote, true, corners, busy, Selected.NONE, Layers.Mode.OFF, false, false, Set.of());
    }

    private static final Set<WheelKey> NONE_BOUND = Set.of();
    private static final Set<WheelKey> ALL_BOUND = EnumSet.allOf(WheelKey.class);

    @Test
    void nothingSelected() {
        assertEquals(List.of(Hint.SELECT, Hint.MENU), KeyHintRules.hints(held(Selected.NONE, Layers.Mode.OFF, NONE_BOUND)));
        assertFalse(KeyHintRules.header(held(Selected.NONE, Layers.Mode.OFF, NONE_BOUND)));
    }

    @Test
    void followingAnchorsAndDoesNotMove() {
        assertEquals(List.of(Hint.ANCHOR, Hint.UP_DOWN, Hint.ROTATE, Hint.MIRROR, Hint.MENU),
                KeyHintRules.hints(held(Selected.FOLLOWING, Layers.Mode.OFF, NONE_BOUND)));
        assertEquals(List.of(Hint.ANCHOR, Hint.UP_DOWN, Hint.ROTATE, Hint.MIRROR, Hint.MENU, Hint.CANCEL, Hint.MATERIALS),
                KeyHintRules.hints(held(Selected.FOLLOWING, Layers.Mode.OFF, ALL_BOUND)));
    }

    @Test
    void anchoredWithDefaultBindingsHasNoLockOrRemove() {
        assertEquals(List.of(Hint.MOVE, Hint.UP_DOWN, Hint.ROTATE, Hint.MIRROR, Hint.RELEASE, Hint.MENU, Hint.DESELECT),
                KeyHintRules.hints(held(Selected.ANCHORED, Layers.Mode.OFF, NONE_BOUND)));
    }

    @Test
    void anchoredWithBoundLock() {
        assertEquals(List.of(Hint.MOVE, Hint.UP_DOWN, Hint.ROTATE, Hint.MIRROR, Hint.RELEASE, Hint.MENU, Hint.DESELECT, Hint.LOCK),
                KeyHintRules.hints(held(Selected.ANCHORED, Layers.Mode.OFF, Set.of(WheelKey.LOCK))));
    }

    @Test
    void lockedStartsWithTheLayerHints() {
        Situation fromTo = held(Selected.LOCKED, Layers.Mode.FROM_TO, ALL_BOUND);
        assertTrue(KeyHintRules.header(fromTo));
        assertEquals(List.of(Hint.WINDOW, Hint.WINDOW_SIZE, Hint.CYCLE_LAYERS, Hint.MENU, Hint.DESELECT, Hint.IN_HAND, Hint.MATERIALS, Hint.UNLOCK),
                KeyHintRules.hints(fromTo));
        assertEquals(List.of(Hint.FOLLOW_HEIGHT, Hint.MENU, Hint.DESELECT), KeyHintRules.hints(held(Selected.LOCKED, Layers.Mode.FOLLOW, NONE_BOUND)));
        assertEquals(List.of(Hint.MENU, Hint.DESELECT), KeyHintRules.hints(held(Selected.LOCKED, Layers.Mode.OFF, NONE_BOUND)));
    }

    @Test
    void pendingConfirmationsReplaceTheHints() {
        Situation remove = new Situation(true, false, 0, false, Selected.ANCHORED, Layers.Mode.OFF, true, false, NONE_BOUND);
        assertEquals(List.of(Hint.REMOVE_AGAIN), KeyHintRules.hints(remove));
        Situation unlock = new Situation(true, false, 0, false, Selected.LOCKED, Layers.Mode.FROM_TO, false, true, NONE_BOUND);
        assertEquals(List.of(Hint.UNLOCK_AGAIN), KeyHintRules.hints(unlock));
        assertTrue(KeyHintRules.header(unlock));
    }

    @Test
    void withoutTheRemoteOnlyLockedLayerHints() {
        assertEquals(List.of(Hint.WINDOW, Hint.WINDOW_SIZE, Hint.CYCLE_LAYERS),
                KeyHintRules.hints(withoutRemote(Selected.LOCKED, Layers.Mode.FROM_TO, ALL_BOUND)));
        assertEquals(List.of(), KeyHintRules.hints(withoutRemote(Selected.ANCHORED, Layers.Mode.OFF, ALL_BOUND)));
        assertEquals(List.of(), KeyHintRules.hints(withoutRemote(Selected.NONE, Layers.Mode.OFF, ALL_BOUND)));
    }

    @Test
    void captureHintsFollowTheCorners() {
        assertEquals(List.of(Hint.CORNER1, Hint.CAPTURE_MENU), KeyHintRules.hints(capture(true, 0, false)));
        assertEquals(List.of(Hint.CORNER2, Hint.MOVE_CORNER, Hint.CORNER_UP_DOWN, Hint.CAPTURE_MENU), KeyHintRules.hints(capture(true, 1, false)));
        assertEquals(List.of(Hint.MOVE_CORNER_CLICK, Hint.MOVE_CORNER, Hint.CORNER_UP_DOWN, Hint.SWAP_CORNER, Hint.CAPTURE_MENU),
                KeyHintRules.hints(capture(true, 2, false)));
        assertEquals(List.of(), KeyHintRules.hints(capture(true, 2, true)));
        assertEquals(List.of(), KeyHintRules.hints(capture(false, 2, false)));
    }

    @Test
    void showsWithTheRemote() {
        assertTrue(KeyHintRules.shows(false, false, true, false, held(Selected.ANCHORED, Layers.Mode.OFF, NONE_BOUND)));
    }

    @Test
    void hiddenWithF1AScreenOrHintsOff() {
        Situation situation = held(Selected.ANCHORED, Layers.Mode.OFF, NONE_BOUND);
        assertFalse(KeyHintRules.shows(true, false, true, true, situation));
        assertFalse(KeyHintRules.shows(false, true, true, true, situation));
        assertFalse(KeyHintRules.shows(false, false, false, true, situation));
    }

    @Test
    void pinnedWithoutTheRemoteOnlyForALockedPlacement() {
        assertTrue(KeyHintRules.shows(false, false, true, true, withoutRemote(Selected.LOCKED, Layers.Mode.OFF, NONE_BOUND)));
        assertFalse(KeyHintRules.shows(false, false, true, false, withoutRemote(Selected.LOCKED, Layers.Mode.FROM_TO, NONE_BOUND)));
        assertFalse(KeyHintRules.shows(false, false, true, true, withoutRemote(Selected.ANCHORED, Layers.Mode.OFF, NONE_BOUND)));
        assertFalse(KeyHintRules.shows(false, false, true, true, withoutRemote(Selected.NONE, Layers.Mode.OFF, NONE_BOUND)));
        assertFalse(KeyHintRules.shows(false, false, true, true, capture(false, 1, false)));
    }

    @Test
    void hiddenWhileACaptureIsBusy() {
        assertFalse(KeyHintRules.shows(false, false, true, false, capture(true, 2, true)));
    }

    @Test
    void bottomRightOnAWideScreen() {
        assertEquals(480 - KeyHintRules.MARGIN, KeyHintRules.bottom(854, 480, 150, false));
    }

    @Test
    void liftedAboveTheSubtitles() {
        int bottom = KeyHintRules.bottom(854, 480, 150, true);
        assertEquals(480 - KeyHintRules.LIFTED, bottom);
        assertTrue(bottom <= 480 - 30 - 4 * 10, "clears four subtitle rows");
    }

    @Test
    void liftedAboveTheHotbarOnANarrowScreen() {
        assertEquals(240 - KeyHintRules.LIFTED, KeyHintRules.bottom(320, 240, 150, false));
    }

    @Test
    void headerAndSettingTextsExist() throws IOException {
        String lang = Files.readString(Path.of("src/main/resources/assets/heavymatica/lang/en_us.json"));
        for (String key : List.of("heavymatica.hints.layers.off", "heavymatica.hints.layers.from_to", "heavymatica.hints.layers.follow",
                "heavymatica.settings.overlay.keyHints.name", "heavymatica.settings.overlay.keyHints.hint")) {
            assertTrue(lang.contains("\"" + key + "\""), key);
        }
    }

    @Test
    void compactOverlayTurnsKeyHintsOff() {
        assertFalse(KeyHintRules.keyHintsAfterMigration(true, true));
        assertTrue(KeyHintRules.keyHintsAfterMigration(false, true));
        assertFalse(KeyHintRules.keyHintsAfterMigration(false, false));
    }
}
