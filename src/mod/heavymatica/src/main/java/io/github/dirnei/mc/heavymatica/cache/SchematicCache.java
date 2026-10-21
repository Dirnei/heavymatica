package io.github.dirnei.mc.heavymatica.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Read-through cache of downloaded version files and thumbnails, keyed by content hash (client/schematic-library,
 * D10). Version files are verified against their SHA-256 when stored and when read; a file that does not match is
 * discarded. The cache stays within its size by evicting the least recently used files. Local-only entries live
 * elsewhere and are never touched.
 */
public final class SchematicCache {

    /** What the cache index remembers about one file. */
    public record Entry(long size, long lastAccess) {
    }

    private final Path directory;
    private final long maxBytes;
    private final Clock clock;
    private final ObjectMapper json;
    private final Map<String, Entry> index;

    public SchematicCache(Path directory, long maxBytes, Clock clock, ObjectMapper json) {
        this.directory = directory;
        this.maxBytes = maxBytes;
        this.clock = clock;
        this.json = json;
        this.index = loadIndex();
    }

    /** A cached version file whose content still hashes to {@code sha256}, or empty. */
    public synchronized Optional<Path> file(String sha256) {
        String key = fileKey(sha256);
        Path path = directory.resolve(key);
        if (!index.containsKey(key) || !Files.isRegularFile(path)) {
            forget(key);
            return Optional.empty();
        }
        if (!sha256.equals(hash(path))) {
            forget(key);
            return Optional.empty();
        }
        touch(key);
        return Optional.of(path);
    }

    /** Stores a downloaded version file after checking its hash. Fails with {@link CorruptDownloadException} otherwise. */
    public synchronized Path putFile(String sha256, InputStream content) {
        String key = fileKey(sha256);
        Path temporary = temporaryFile();
        try {
            MessageDigest digest = sha256();
            try (InputStream in = new DigestInputStream(content, digest)) {
                Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equals(sha256)) {
                throw new CorruptDownloadException("Downloaded file hashes to " + actual + ", expected " + sha256);
            }
            return store(key, temporary);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(temporary);
        }
    }

    /** A cached thumbnail of the version with this content hash, or empty. */
    public synchronized Optional<Path> thumbnail(String versionSha256) {
        String key = thumbnailKey(versionSha256);
        Path path = directory.resolve(key);
        if (!index.containsKey(key) || !Files.isRegularFile(path)) {
            forget(key);
            return Optional.empty();
        }
        touch(key);
        return Optional.of(path);
    }

    public synchronized Path putThumbnail(String versionSha256, byte[] png) {
        Path temporary = temporaryFile();
        try {
            Files.write(temporary, png);
            return store(thumbnailKey(versionSha256), temporary);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(temporary);
        }
    }

    /** Total bytes of all cached files. */
    public synchronized long size() {
        return index.values().stream().mapToLong(Entry::size).sum();
    }

    private Path store(String key, Path temporary) throws IOException {
        long size = Files.size(temporary);
        forget(key);
        evictFor(size);
        Path target = directory.resolve(key);
        Files.createDirectories(target.getParent());
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        index.put(key, new Entry(size, clock.millis()));
        saveIndex();
        return target;
    }

    /** Removes least recently used files until {@code incoming} more bytes fit. */
    private void evictFor(long incoming) {
        long used = size();
        var oldest = index.entrySet().stream().sorted(Comparator.comparingLong(entry -> entry.getValue().lastAccess())).map(Map.Entry::getKey).toList();
        for (String key : oldest) {
            if (used + incoming <= maxBytes) {
                break;
            }
            used -= index.get(key).size();
            forget(key);
        }
    }

    private void touch(String key) {
        index.computeIfPresent(key, (ignored, entry) -> new Entry(entry.size(), clock.millis()));
        saveIndex();
    }

    private void forget(String key) {
        if (index.remove(key) != null) {
            saveIndex();
        }
        deleteQuietly(directory.resolve(key));
    }

    private Map<String, Entry> loadIndex() {
        Path file = directory.resolve("index.json");
        if (Files.isRegularFile(file)) {
            try {
                return new LinkedHashMap<>(json.readValue(file.toFile(), new TypeReference<Map<String, Entry>>() { }));
            } catch (IOException e) {
                // A damaged index only costs downloads again.
            }
        }
        return new LinkedHashMap<>();
    }

    private void saveIndex() {
        try {
            Files.createDirectories(directory);
            Path temporary = directory.resolve("index.json.tmp");
            json.writeValue(temporary.toFile(), index);
            Files.move(temporary, directory.resolve("index.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path temporaryFile() {
        try {
            Files.createDirectories(directory);
            return Files.createTempFile(directory, "download", ".tmp");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String fileKey(String sha256) {
        return sha256;
    }

    private static String thumbnailKey(String versionSha256) {
        return "thumbs/" + versionSha256 + ".png";
    }

    static String hash(Path file) {
        MessageDigest digest = sha256();
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
        } catch (IOException e) {
            return "";
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Retried by the next eviction or write.
        }
    }

    /** A download did not match its content hash. */
    public static final class CorruptDownloadException extends RuntimeException {
        public CorruptDownloadException(String message) {
            super(message);
        }
    }
}
