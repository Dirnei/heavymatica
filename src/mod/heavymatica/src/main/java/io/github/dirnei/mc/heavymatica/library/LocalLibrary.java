package io.github.dirnei.mc.heavymatica.library;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.formats.SchematicReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Local-only library entries (client/schematic-import): each keeps its own copy of the file under
 * {@code local/<sha256>.<ext>}, so it stays usable when the source file is moved or deleted, and works signed out.
 * Never evicted (D10).
 */
public final class LocalLibrary {

    public record LocalEntry(String sha256, String name, String extension, String format, int width, int height, int length,
                             long blockTotal, OffsetDateTime importedAt) {
    }

    private final Path directory;
    private final ObjectMapper json;
    private final Clock clock;
    private final List<LocalEntry> entries;

    public LocalLibrary(Path directory, ObjectMapper json, Clock clock) {
        this.directory = directory;
        this.json = json;
        this.clock = clock;
        this.entries = load();
    }

    public synchronized List<LocalEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized Optional<LocalEntry> find(String sha256) {
        return entries.stream().filter(entry -> entry.sha256().equals(sha256)).findFirst();
    }

    /** The entry's own copy of the file. */
    public Path file(LocalEntry entry) {
        return directory.resolve(entry.sha256() + "." + entry.extension());
    }

    /**
     * Validates the file and adds it as a local entry. Rejected files leave the library and the source unchanged
     * ({@link io.github.dirnei.mc.heavymatica.formats.SchematicRejectedException}).
     */
    public synchronized LocalEntry add(Path source) {
        byte[] content = read(source);
        SchematicModel model = SchematicReader.read(content);
        String sha256 = sha256(content);
        Optional<LocalEntry> existing = find(sha256);
        if (existing.isPresent()) {
            return existing.get();
        }
        String fileName = source.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String name = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "bin";
        LocalEntry entry = new LocalEntry(sha256, name, extension, model.format().wireName(), model.width(), model.height(), model.length(),
                model.blockTotal(), OffsetDateTime.now(clock));
        try {
            Files.createDirectories(directory);
            Files.write(file(entry), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        entries.add(entry);
        save();
        return entry;
    }

    /** Renames an entry. Only the library's name changes; the stored copy stays as it is. */
    public synchronized void rename(String sha256, String name) {
        find(sha256).ifPresent(entry -> {
            entries.set(entries.indexOf(entry), new LocalEntry(entry.sha256(), name, entry.extension(), entry.format(), entry.width(),
                    entry.height(), entry.length(), entry.blockTotal(), entry.importedAt()));
            save();
        });
    }

    /** Removes an entry and its copy, for example after it was uploaded. */
    public synchronized void remove(String sha256) {
        find(sha256).ifPresent(entry -> {
            entries.remove(entry);
            try {
                Files.deleteIfExists(file(entry));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            save();
        });
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] read(Path source) {
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<LocalEntry> load() {
        Path index = directory.resolve("index.json");
        if (Files.isRegularFile(index)) {
            try {
                return new ArrayList<>(json.readValue(index.toFile(), new TypeReference<List<LocalEntry>>() { }));
            } catch (IOException e) {
                // A damaged index loses the list, not the files; they can be imported again.
            }
        }
        return new ArrayList<>();
    }

    private void save() {
        try {
            Files.createDirectories(directory);
            Path temporary = directory.resolve("index.json.tmp");
            json.writeValue(temporary.toFile(), entries);
            Files.move(temporary, directory.resolve("index.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
