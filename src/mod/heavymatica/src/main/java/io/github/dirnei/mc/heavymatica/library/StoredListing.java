package io.github.dirnei.mc.heavymatica.library;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dirnei.mc.heavymatica.cloud.model.SchematicSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * The last known cloud listing of each Minecraft account ({@code library/<uuid>/listing.json}), shown while the cloud
 * is unreachable or the session expired, and deleted when the player signs out.
 */
public final class StoredListing {

    private final Path directory;
    private final ObjectMapper json;

    public StoredListing(Path directory, ObjectMapper json) {
        this.directory = directory;
        this.json = json;
    }

    public List<SchematicSummary> load(UUID uuid) {
        Path file = file(uuid);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            return json.readValue(file.toFile(), new TypeReference<List<SchematicSummary>>() { });
        } catch (IOException e) {
            return List.of();
        }
    }

    public void save(UUID uuid, List<SchematicSummary> listing) {
        try {
            Path file = file(uuid);
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling("listing.json.tmp");
            json.writeValue(temporary.toFile(), listing);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(UUID uuid) {
        try {
            Files.deleteIfExists(file(uuid));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path file(UUID uuid) {
        return directory.resolve(uuid.toString()).resolve("listing.json");
    }
}
