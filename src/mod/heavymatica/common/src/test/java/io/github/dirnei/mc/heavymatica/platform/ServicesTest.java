package io.github.dirnei.mc.heavymatica.platform;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServicesTest {

    interface Unprovided {
    }

    @Test
    void missingImplementationNamesTheService() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Services.load(Unprovided.class));
        assertTrue(error.getMessage().contains(Unprovided.class.getName()), error.getMessage());
        assertTrue(error.getMessage().contains("META-INF/services"), error.getMessage());
    }
}
