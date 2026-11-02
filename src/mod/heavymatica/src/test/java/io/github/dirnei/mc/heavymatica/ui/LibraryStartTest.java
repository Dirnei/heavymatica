package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LibraryStartTest {

    @Test
    void anEmptyLibraryOpensTheImportScreen() {
        assertEquals(LibraryStart.IMPORT, LibraryStart.decide(0, false));
    }

    @Test
    void anEmptyLibraryWaitsForTheCloudListing() {
        assertEquals(LibraryStart.WAIT, LibraryStart.decide(0, true));
    }

    @Test
    void entriesShowTheGrid() {
        assertEquals(LibraryStart.GRID, LibraryStart.decide(1, false));
        assertEquals(LibraryStart.GRID, LibraryStart.decide(3, true));
    }
}
