package io.github.dirnei.mc.heavymatica.account;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

class CloudAddressTest {

    @Test
    void the_property_wins_over_the_config() {
        assertEquals(URI.create("http://localhost:5180"), CloudAddress.choose("http://localhost:5180", "https://api.heavymatica.com"));
    }

    @Test
    void without_the_property_the_config_is_used() {
        assertEquals(URI.create("https://api.heavymatica.com"), CloudAddress.choose(null, "https://api.heavymatica.com"));
        assertEquals(URI.create("https://api.heavymatica.com"), CloudAddress.choose("  ", "https://api.heavymatica.com"));
    }
}
