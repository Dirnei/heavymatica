package io.github.dirnei.mc.heavymatica.library;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExporterTest {

    @TempDir
    Path directory;

    private Path source;
    private Exporter exporter;
    private Path schematics;

    @BeforeEach
    void start() throws Exception {
        source = Files.write(directory.resolve("stored.bin"), new byte[] {1, 2, 3});
        schematics = directory.resolve("game/schematics");
        exporter = Exporter.schematicsFolder(directory.resolve("game"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Starter Hut|Starter Hut",
            "Tower: v2/final|Tower_ v2_final",
            "a<b>c\"d\\e?g*h|a_b_c_d_e_g_h",
            "dots...  |dots",
            "'  '|schematic",
            "///|schematic",
    })
    void names_are_cleaned(String name, String expected) {
        assertEquals(expected, Exporter.fileName(name));
    }

    @Test
    void writes_the_file_unchanged_into_schematics() throws Exception {
        Path written = exporter.export(source, "Tower: v2/final", "litematic", Exporter.Conflict.OVERWRITE);

        assertEquals(schematics.resolve("Tower_ v2_final.litematic"), written);
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(written));
        assertEquals(List.of(written), files());
    }

    @Test
    void keep_both_takes_the_first_free_number() throws Exception {
        Files.createDirectories(schematics);
        Files.write(schematics.resolve("Starter Hut.litematic"), new byte[] {9});
        Files.write(schematics.resolve("Starter Hut (2).litematic"), new byte[] {9});

        assertTrue(exporter.exists("Starter Hut", "litematic"));
        Path written = exporter.export(source, "Starter Hut", "litematic", Exporter.Conflict.KEEP_BOTH);

        assertEquals(schematics.resolve("Starter Hut (3).litematic"), written);
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(schematics.resolve("Starter Hut.litematic")));
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(schematics.resolve("Starter Hut (2).litematic")));
    }

    @Test
    void keep_both_without_a_conflict_uses_the_plain_name() throws Exception {
        assertEquals(schematics.resolve("Barn.schem"), exporter.export(source, "Barn", "schem", Exporter.Conflict.KEEP_BOTH));
    }

    @Test
    void overwrite_replaces_the_existing_file() throws Exception {
        Files.createDirectories(schematics);
        Files.write(schematics.resolve("Starter Hut.litematic"), new byte[] {9});

        exporter.export(source, "Starter Hut", "litematic", Exporter.Conflict.OVERWRITE);

        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(schematics.resolve("Starter Hut.litematic")));
        assertEquals(1, files().size());
    }

    @Test
    void checking_for_a_conflict_writes_nothing() throws Exception {
        Files.createDirectories(schematics);
        Files.write(schematics.resolve("Starter Hut.litematic"), new byte[] {9});

        assertTrue(exporter.exists("Starter Hut", "litematic"));

        assertEquals(List.of(schematics.resolve("Starter Hut.litematic")), files());
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(schematics.resolve("Starter Hut.litematic")));
    }

    private List<Path> files() throws Exception {
        try (Stream<Path> list = Files.list(schematics)) {
            return list.sorted().toList();
        }
    }
}
