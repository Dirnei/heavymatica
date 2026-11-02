package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The smallest size the game offers for an 854 × 480 window is 427 × 240 GUI units, at scale 2. */
class ScreenLayoutTest {

    private static void apart(Rect screen, List<Rect> parts) {
        for (int i = 0; i < parts.size(); i++) {
            Rect part = parts.get(i);
            if (part.w() == 0 && part.h() == 0) {
                continue;
            }
            assertTrue(part.inside(screen), part + " lies outside " + screen);
            assertTrue(part.w() > 0 && part.h() > 0, part + " has no room");
            for (int j = i + 1; j < parts.size(); j++) {
                Rect other = parts.get(j);
                if (other.w() == 0 && other.h() == 0) {
                    continue;
                }
                assertFalse(part.overlaps(other), part + " overlaps " + other);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"427,240", "640,360", "960,540"})
    void libraryPartsStayApart(int width, int height) {
        for (boolean cloud : new boolean[] {false, true}) {
            LibraryLayout layout = LibraryLayout.of(width, height, cloud);
            apart(layout.screen(), layout.parts());
            assertTrue(layout.cards().inside(layout.gridPanel()));
            assertTrue(layout.chips().inside(layout.gridPanel()));
            assertTrue(layout.status().inside(layout.gridPanel()));
            assertFalse(layout.cards().overlaps(layout.chips()));
            assertFalse(layout.cards().overlaps(layout.status()));
            assertTrue(layout.cards().h() >= LibraryLayout.CARD_HEIGHT, "at least one row of cards fits at " + width + "x" + height);
            assertTrue(layout.cardWidth() >= LibraryLayout.MIN_CARD);
        }
    }

    @ParameterizedTest
    @CsvSource({"427,170", "640,192", "960,260", "1200,260"})
    void detailsPanelFollowsAThirdOfTheWidth(int width, int expected) {
        assertEquals(expected, LibraryLayout.of(width, 360, false).details().w());
    }

    @ParameterizedTest
    @CsvSource({"427,240", "960,540"})
    void searchShrinksBeforeAnyHeaderButton(int width, int height) {
        LibraryLayout layout = LibraryLayout.of(width, height, true);
        assertEquals(LibraryLayout.IMPORT, layout.importButton().w());
        assertEquals(LibraryLayout.ACCOUNT, layout.account().w());
        assertEquals(LibraryLayout.BUTTON, layout.settings().w());
        assertTrue(layout.search().w() <= LibraryLayout.MAX_SEARCH);
    }

    @ParameterizedTest
    @CsvSource({"427,240", "640,360", "960,540"})
    void importPartsStayApart(int width, int height) {
        for (boolean firstRun : new boolean[] {false, true}) {
            ImportLayout layout = ImportLayout.of(width, height, firstRun);
            apart(layout.screen(), layout.parts());
            assertTrue(layout.list().inside(layout.main()));
            assertTrue(layout.actions().inside(layout.main()));
            assertFalse(layout.list().overlaps(layout.actions()));
        }
    }

    @ParameterizedTest
    @CsvSource({"427,240", "640,360", "960,540"})
    void detailsPagePartsStayApart(int width, int height) {
        EntryDetailsLayout layout = EntryDetailsLayout.of(width, height);
        apart(layout.screen(), layout.parts());
        assertTrue(layout.actions().inside(layout.side()));
        assertTrue(layout.list().inside(layout.side()));
    }

    @ParameterizedTest
    @CsvSource({"427,240", "640,360", "960,540"})
    void materialsPartsStayApart(int width, int height) {
        for (boolean locked : new boolean[] {false, true}) {
            MaterialsLayout layout = MaterialsLayout.of(width, height, locked);
            apart(layout.screen(), layout.parts());
            assertTrue(layout.rows().inside(layout.table()));
            MaterialsLayout.Columns columns = layout.columnsFor(locked);
            if (locked) {
                assertTrue(columns.name() + columns.nameWidth() < columns.countRight());
                assertTrue(columns.countRight() < columns.bar());
                assertTrue(columns.bar() + columns.barWidth() < columns.shortRight());
                assertTrue(columns.shortRight() <= layout.rows().right());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"427,240", "640,360", "960,540"})
    void accountPartsStayApart(int width, int height) {
        AccountLayout layout = AccountLayout.of(width, height);
        apart(layout.screen(), layout.parts());
        assertTrue(layout.linkAnother().inside(layout.signIns()));
        assertTrue(layout.list().inside(layout.signIns()));
        assertFalse(layout.list().overlaps(layout.linkAnother()));
        assertTrue(layout.visibleRows() >= 3, "three sign-ins fit at " + width + "x" + height);
    }
}
