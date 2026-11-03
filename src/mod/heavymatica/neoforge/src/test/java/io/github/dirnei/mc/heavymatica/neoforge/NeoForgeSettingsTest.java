package io.github.dirnei.mc.heavymatica.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlWriter;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NeoForgeSettingsTest {

    /** The default file both loaders must write, shared with the common tests. */
    private static final Path GOLDEN = Path.of(System.getProperty("heavymatica.settings.golden", "missing"));

    @TempDir
    Path dir;

    @Test
    void generatedSpecWritesTheSameFileAsTheHandWrittenOne() throws IOException {
        String legacy = defaultFile(LegacyClientSpec.SPEC, dir.resolve("legacy.toml"));
        String generated = defaultFile(NeoForgeSettings.spec(HeavymaticaConfig.SCHEMA), dir.resolve("generated.toml"));
        assertEquals(legacy, generated);
    }

    @Test
    void defaultFileMatchesTheSharedOne() throws IOException {
        String generated = defaultFile(NeoForgeSettings.spec(HeavymaticaConfig.SCHEMA), dir.resolve("generated.toml"));
        assertEquals(lines(Files.readString(GOLDEN, StandardCharsets.UTF_8)), lines(generated), "Golden file: " + GOLDEN);
    }

    /** NightConfig writes the platform's line separator; the golden file is stored with LF. */
    private static String lines(String text) {
        return text.replace(System.lineSeparator(), "\n");
    }

    /** Writes the default file the way NeoForge's config tracker does for a missing file. */
    private static String defaultFile(ModConfigSpec spec, Path file) throws IOException {
        CommentedConfig config = CommentedConfig.of(LinkedHashMap::new, InMemoryCommentedFormat.defaultInstance());
        spec.correct(config);
        new TomlWriter().write(config, file, WritingMode.REPLACE_ATOMIC);
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
