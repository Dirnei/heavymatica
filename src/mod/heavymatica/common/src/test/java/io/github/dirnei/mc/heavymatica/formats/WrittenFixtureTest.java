package io.github.dirnei.mc.heavymatica.formats;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Keeps {@code conformance/files/written/capture.litematic} equal to what the writer produces, so the cloud's
 * conformance tests read the writer's real output. Set {@code HEAVYMATICA_REGENERATE_WRITTEN=1} to rewrite the file.
 */
class WrittenFixtureTest {

    static final LitematicaWriter.Metadata METADATA = new LitematicaWriter.Metadata("Capture", "Heavymatica", 1_793_000_000_000L);

    @Test
    void the_written_fixture_is_the_writers_output() throws IOException {
        Path file = ConformanceTest.conformance().resolve("files").resolve("written").resolve("capture.litematic");
        byte[] written = LitematicaWriter.write(LitematicaWriterTest.sample(), METADATA);
        if ("1".equals(System.getenv("HEAVYMATICA_REGENERATE_WRITTEN"))) {
            Files.createDirectories(file.getParent());
            Files.write(file, written);
        }

        assertArrayEquals(Files.readAllBytes(file), written);
    }
}
