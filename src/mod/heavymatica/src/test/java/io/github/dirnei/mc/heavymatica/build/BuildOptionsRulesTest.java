package io.github.dirnei.mc.heavymatica.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildOptionsRulesTest {

    @Test
    void easyPlaceOnly() {
        assertEquals(List.of(Entry.EASY_PLACE), BuildOptionsRules.shown(true, false, true, false, false));
    }

    @Test
    void bothWithEasyPlaceFirst() {
        assertEquals(List.of(Entry.EASY_PLACE, Entry.RESTRICTION), BuildOptionsRules.shown(true, true, true, false, false));
    }

    @Test
    void restrictionOnly() {
        assertEquals(List.of(Entry.RESTRICTION), BuildOptionsRules.shown(false, true, true, false, false));
    }

    @Test
    void nothingWhenBothAreOff() {
        assertEquals(List.of(), BuildOptionsRules.shown(false, false, true, false, false));
    }

    @Test
    void nothingWithoutALockedPlacement() {
        assertEquals(List.of(), BuildOptionsRules.shown(true, true, false, false, false));
    }

    @Test
    void nothingBehindAScreenOrWithF1() {
        assertEquals(List.of(), BuildOptionsRules.shown(true, true, true, true, false));
        assertEquals(List.of(), BuildOptionsRules.shown(true, true, true, false, true));
    }
}
