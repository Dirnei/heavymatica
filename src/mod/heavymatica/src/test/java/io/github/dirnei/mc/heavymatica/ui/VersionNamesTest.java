package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class VersionNamesTest {

    @Test
    void knownReleaseIsNamed() {
        assertEquals(Optional.of("1.20.1"), VersionNames.name(3465, 4790, "26.1.2"));
    }

    @Test
    void theGamesOwnDataVersionUsesItsName() {
        assertEquals(Optional.of("26.1.2"), VersionNames.name(4790, 4790, "26.1.2"));
    }

    @Test
    void unknownDataVersionHasNoName() {
        assertTrue(VersionNames.name(3466, 4790, "26.1.2").isEmpty());
    }
}
