package io.github.dirnei.mc.heavymatica.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TomlSettingsFileTest {

    @TempDir
    Path dir;

    private static String resource(String name) throws IOException {
        try (InputStream in = Objects.requireNonNull(TomlSettingsFileTest.class.getResourceAsStream("/settings/" + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String lines(String text) {
        return text.replace(System.lineSeparator(), "\n");
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(TomlSettingsFile file, String path) {
        return file.get((Setting<T>) Objects.requireNonNull(HeavymaticaConfig.SCHEMA.setting(path), path));
    }

    @Test
    void freshFileMatchesNeoForgesDefaultFile() throws IOException {
        Path file = dir.resolve("heavymatica-client.toml");
        TomlSettingsFile.load(HeavymaticaConfig.SCHEMA, file);
        assertEquals(resource("heavymatica-client.default.toml"), lines(Files.readString(file, StandardCharsets.UTF_8)));
    }

    @Test
    void readsAFileWrittenByNeoForge() throws IOException {
        Path file = dir.resolve("heavymatica-client.toml");
        Files.writeString(file, resource("neoforge-written.toml"), StandardCharsets.UTF_8);
        TomlSettingsFile settings = TomlSettingsFile.load(HeavymaticaConfig.SCHEMA, file);

        assertEquals("http://localhost:5180", value(settings, "cloud.cloudUrl"));
        assertEquals("http://localhost:5180/dev/mojang", value(settings, "cloud.devSessionJoinUrl"));
        assertEquals(0.6, (Double) value(settings, "ghost.opacity"));
        assertEquals(List.of(0.2, 0.4, 0.6, 0.8, 1.0), value(settings, "ghost.opacitySteps"));
        assertEquals(true, value(settings, "overlay.pinned"));
        assertEquals(RadialMenu.Mode.TOGGLE, value(settings, "wheel.mode"));
        assertEquals(true, value(settings, "build.easyPlace"));
        assertEquals(1024, (Integer) value(settings, "cache.cacheSizeMiB"));
    }

    @Test
    void outOfRangeFallsBackToTheDefaultAndIsWrittenBack() throws IOException {
        Path file = dir.resolve("heavymatica-client.toml");
        Files.writeString(file, resource("heavymatica-client.default.toml").replace("cacheSizeMiB = 1024", "cacheSizeMiB = 10"),
                StandardCharsets.UTF_8);
        TomlSettingsFile settings = TomlSettingsFile.load(HeavymaticaConfig.SCHEMA, file);

        assertEquals(1024, (Integer) value(settings, "cache.cacheSizeMiB"));
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("cacheSizeMiB = 1024"));
    }

    @Test
    void savedChangesReachTheFile() throws IOException {
        Path file = dir.resolve("heavymatica-client.toml");
        TomlSettingsFile settings = TomlSettingsFile.load(HeavymaticaConfig.SCHEMA, file);
        @SuppressWarnings("unchecked")
        Setting<Boolean> pinned = (Setting<Boolean>) Objects.requireNonNull(HeavymaticaConfig.SCHEMA.setting("overlay.pinned"));
        settings.set(pinned, true);
        settings.save(pinned);

        assertEquals(true, value(TomlSettingsFile.load(HeavymaticaConfig.SCHEMA, file), "overlay.pinned"));
    }

    @Test
    void unreadableFileIsKeptAndReplacedWithDefaults() throws IOException {
        Path file = dir.resolve("heavymatica-client.toml");
        Files.writeString(file, "[cloud\nbroken = ", StandardCharsets.UTF_8);
        TomlSettingsFile.load(HeavymaticaConfig.SCHEMA, file);

        assertTrue(Files.exists(dir.resolve("heavymatica-client.toml.bak")));
        assertEquals(resource("heavymatica-client.default.toml"), lines(Files.readString(file, StandardCharsets.UTF_8)));
    }
}
