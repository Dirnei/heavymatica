package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SettingsTableTest {

    private static final Pattern STEP = Pattern.compile("\\.push\\(\"([^\"]+)\"\\)|\\.pop\\(\\)|\\.define\\w*\\(\"([^\"]+)\"");

    /** Every config path, read from the config's source the way its builder nests them. */
    private static Set<String> configPaths() throws IOException {
        String source = Files.readString(Path.of("src/main/java/io/github/dirnei/mc/heavymatica/HeavymaticaConfig.java"));
        Deque<String> groups = new ArrayDeque<>();
        Set<String> paths = new HashSet<>();
        Matcher matcher = STEP.matcher(source);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                groups.addLast(matcher.group(1));
            } else if (matcher.group(2) != null) {
                paths.add(String.join(".", groups) + "." + matcher.group(2));
            } else if (!groups.isEmpty()) {
                groups.removeLast();
            }
        }
        return paths;
    }

    @Test
    void everySettingButTheFileOnlyOnesIsInExactlyOneSection() throws IOException {
        Set<String> config = configPaths();
        assertTrue(config.size() > 10, "read the config's paths: " + config);
        Set<String> shown = SettingsTable.ALL.stream().map(SettingsTable.Setting::path).collect(Collectors.toSet());
        assertEquals(SettingsTable.ALL.size(), shown.size(), "a setting is listed twice");
        Set<String> expected = new HashSet<>(config);
        expected.removeAll(SettingsTable.FILE_ONLY);
        assertEquals(expected, shown);
    }

    @Test
    void cloudAddressStaysInTheFile() {
        assertFalse(SettingsTable.ALL.stream().anyMatch(setting -> setting.path().equals("cloud.cloudUrl")));
    }

    @Test
    void numbersOutsideTheRangeAreRefused() {
        SettingsTable.Setting budget = SettingsTable.ALL.stream().filter(setting -> setting.path().equals("ghost.blockBudget")).findFirst().orElseThrow();
        assertFalse(SettingsTable.accepts(budget, 5));
        assertTrue(SettingsTable.accepts(budget, 1_000));
    }
}
