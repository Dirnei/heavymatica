package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;
import io.github.dirnei.mc.heavymatica.remote.WheelStyle.Kind;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WheelStyleTest {

    @Test
    void everyEntryHasAKind() {
        for (Entry entry : Entry.values()) {
            assertNotNull(WheelStyle.kind(entry));
        }
    }

    @Test
    void dangerAndConfirmEntries() {
        Set<Entry> danger = EnumSet.of(Entry.REMOVE, Entry.CANCEL, Entry.CANCEL_CAPTURE, Entry.NO, Entry.UNDO_PASTE);
        Set<Entry> confirm = EnumSet.of(Entry.YES, Entry.BRING_BACK);
        for (Entry entry : Entry.values()) {
            Kind expected = danger.contains(entry) ? Kind.DANGER : confirm.contains(entry) ? Kind.CONFIRM : Kind.NORMAL;
            assertEquals(expected, WheelStyle.kind(entry), entry.name());
        }
    }

    @Test
    void mixBlendsEveryChannel() {
        assertEquals(0x80808080, WheelStyle.mix(0x00000000, 0xFFFFFFFF, 0.5) | 0);
        assertEquals(0xFF000000, WheelStyle.mix(0xFF000000, 0x00FFFFFF, 0));
        assertEquals(0x1A7FD8FF, WheelStyle.withAlpha(Kind.NORMAL.accent(), 0x1A));
    }
}
