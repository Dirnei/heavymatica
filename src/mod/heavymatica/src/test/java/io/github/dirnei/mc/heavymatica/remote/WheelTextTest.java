package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Capture;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Mode;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Opacity;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Paste;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Segment;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Selection;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.State;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WheelTextTest {

    private static final Opacity OPACITY = new Opacity(List.of(0.2, 0.6, 1.0), 0.6);
    private static Set<String> keys;

    @BeforeAll
    static void readLanguage() throws IOException {
        try (InputStream in = WheelTextTest.class.getClassLoader().getResourceAsStream("assets/heavymatica/lang/en_us.json")) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            keys = new HashSet<>();
            Matcher matcher = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(json);
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }
    }

    @Test
    void everyEntryHasAHint() {
        for (Entry entry : Entry.values()) {
            String key = WheelText.hintKey(new Segment(entry, true, false));
            assertTrue(keys.contains(key), key);
        }
    }

    @Test
    void greyedOutSegmentsSayWhy() {
        List<Segment> disabled = new ArrayList<>();
        disabled.addAll(new RadialMenu(Mode.HOLD, Selection.NONE, OPACITY).segments());
        for (int corners = 0; corners <= 2; corners++) {
            disabled.addAll(new RadialMenu(Mode.HOLD, Selection.NONE, OPACITY, false, new Capture(true, corners)).segments());
        }
        Selection pasting = new Selection(State.LOCKED, "Castle Gate", Layers.Mode.OFF, false, false, true, new Paste(true, true, true));
        disabled.addAll(new RadialMenu(Mode.HOLD, pasting, OPACITY).segments());
        Set<Entry> seen = new HashSet<>();
        for (Segment segment : disabled) {
            if (!segment.enabled()) {
                seen.add(segment.entry());
                assertTrue(keys.contains(WheelText.hintKey(segment)), WheelText.hintKey(segment));
            }
        }
        assertEquals(Set.of(Entry.MATERIALS, Entry.CAPTURE, Entry.SWAP_CORNER, Entry.RESET_CORNERS, Entry.PASTE, Entry.UNDO_PASTE), seen);
    }

    @Test
    void greyedOutHintUsesItsOwnKey() {
        assertEquals("heavymatica.wheel.hint.materials.disabled", WheelText.hintKey(new Segment(Entry.MATERIALS, false, false)));
        assertEquals("heavymatica.wheel.hint.materials", WheelText.hintKey(new Segment(Entry.MATERIALS, true, false)));
    }

    @Test
    void everyStateHasAName() {
        for (State state : State.values()) {
            if (state != State.NONE) {
                assertTrue(keys.contains(WheelText.stateKey(state)), state.name());
            }
        }
    }
}
