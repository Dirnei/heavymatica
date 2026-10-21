package io.github.dirnei.mc.heavymatica.formats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Reads every file of the shared conformance set and compares the result with its expected JSON (D7). */
class ConformanceTest {

    private static final Path CONFORMANCE = locate();
    private static final ObjectMapper JSON = new ObjectMapper();

    static Stream<String> fixtures() throws IOException {
        Path files = CONFORMANCE.resolve("files");
        try (Stream<Path> walk = Files.walk(files)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals(".gitkeep"))
                    .map(path -> files.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void reads_like_the_expected_result(String fixture) throws IOException {
        Path expectedPath = CONFORMANCE.resolve("expected").resolve(fixture + ".json");
        assertTrue(Files.exists(expectedPath), fixture + " has no expected result.");
        JsonNode expected = JSON.readTree(expectedPath.toFile());
        Path file = CONFORMANCE.resolve("files").resolve(fixture);

        if (expected.has("reject")) {
            SchematicRejectedException error = assertThrows(SchematicRejectedException.class, () -> SchematicReader.read(file));
            assertEquals(expected.get("reject").asText(), error.code().wireName(), error.getMessage());
            return;
        }

        SchematicModel model = SchematicReader.read(file);

        assertEquals(expected.get("format").asText(), model.format().wireName());
        assertEquals(List.of(expected.get("size").get(0).asInt(), expected.get("size").get(1).asInt(), expected.get("size").get(2).asInt()),
                List.of(model.width(), model.height(), model.length()));
        assertEquals(expected.get("dataVersion").isNull() ? null : expected.get("dataVersion").asInt(), model.dataVersion());
        assertEquals(expected.get("paletteDigest").asText(), model.paletteDigest());
        assertEquals(expected.get("blockTotal").asLong(), model.blockTotal());
        Map<String, Long> expectedCounts = new TreeMap<>();
        expected.get("blockCounts").properties().forEach(entry -> expectedCounts.put(entry.getKey(), entry.getValue().asLong()));
        Map<String, Long> actualCounts = new TreeMap<>();
        model.blockCounts().forEach(count -> actualCounts.put(count.block(), count.count()));
        assertEquals(expectedCounts, actualCounts);
        List<String> expectedEntities = new ArrayList<>();
        expected.get("blockEntities").forEach(entity -> expectedEntities.add(
                entity.get("pos").get(0).asInt() + "," + entity.get("pos").get(1).asInt() + "," + entity.get("pos").get(2).asInt() + "=" + entity.get("id").asText()));
        List<String> actualEntities = model.blockEntities().entrySet().stream()
                .map(entry -> entry.getKey().x() + "," + entry.getKey().y() + "," + entry.getKey().z() + "=" + entry.getValue().string("id"))
                .sorted().toList();
        assertEquals(expectedEntities.stream().sorted().toList(), actualEntities);
        assertEquals(expected.get("skippedEntities").asInt(), model.skippedEntities());
    }

    private static Path locate() {
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve("src").resolve("conformance");
            if (Files.isDirectory(candidate.resolve("expected"))) {
                return candidate;
            }
            if (Files.isDirectory(directory.resolve("conformance").resolve("expected"))) {
                return directory.resolve("conformance");
            }
        }
        throw new IllegalStateException("Could not find src/conformance above " + Path.of("").toAbsolutePath());
    }
}
