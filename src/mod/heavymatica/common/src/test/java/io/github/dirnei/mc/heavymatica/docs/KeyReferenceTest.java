package io.github.dirnei.mc.heavymatica.docs;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.dirnei.mc.heavymatica.HeavymaticaClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;

/** Keeps the documentation's key reference, docs/src/data/keys.json, equal to the keys the mod registers. */
class KeyReferenceTest {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String REWRITE = "Rewrite it with ./gradlew :heavymatica:common:test -PwriteDocs";

    @Test
    void docsListEveryRegisteredKey() throws IOException {
        String location = System.getProperty("heavymatica.docs.keys");
        assertNotNull(location, "Gradle passes heavymatica.docs.keys");
        Path file = Path.of(location);
        List<Map<String, Object>> actual = registeredKeys();

        if (Boolean.getBoolean("heavymatica.docs.write")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.writeValueAsString(actual).replace("\r\n", "\n") + "\n", StandardCharsets.UTF_8);
            return;
        }
        if (!Files.exists(file)) {
            fail(file + " is missing. " + REWRITE);
        }
        List<Map<String, Object>> documented = JSON.readerForListOf(Map.class).readValue(file.toFile());
        List<String> differences = differences(documented, actual);
        if (!differences.isEmpty()) {
            fail("The docs' key reference differs from the mod: " + String.join("; ", differences) + ". " + REWRITE);
        }
    }

    private static List<Map<String, Object>> registeredKeys() {
        RecordingPlatform.Registered registry = new RecordingPlatform.Registered();
        HeavymaticaClient.registerKeys(registry);
        List<Map<String, Object>> keys = new ArrayList<>();
        for (KeyMapping mapping : registry.mappings) {
            Map<String, Object> key = new LinkedHashMap<>();
            key.put("id", mapping.getName());
            String defaultKey = mapping.getDefaultKey().getName();
            key.put("default", "key.keyboard.unknown".equals(defaultKey) ? null : defaultKey);
            key.put("context", String.valueOf(RecordingPlatform.CONTEXTS.get(mapping)));
            keys.add(key);
        }
        return keys;
    }

    private static List<String> differences(List<Map<String, Object>> documented, List<Map<String, Object>> actual) {
        Map<Object, Map<String, Object>> byId = new LinkedHashMap<>();
        documented.forEach(key -> byId.put(key.get("id"), key));
        List<String> differences = new ArrayList<>();
        for (Map<String, Object> key : actual) {
            Map<String, Object> listed = byId.remove(key.get("id"));
            if (listed == null) {
                differences.add(key.get("id") + " is not listed");
            } else if (!Objects.equals(listed, key)) {
                differences.add(key.get("id") + " is listed as " + listed + " but is " + key);
            }
        }
        byId.keySet().forEach(id -> differences.add(id + " is listed but no longer exists"));
        if (differences.isEmpty() && !documented.equals(actual)) {
            differences.add("the order differs");
        }
        return differences;
    }
}
