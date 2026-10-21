package io.github.dirnei.mc.heavymatica.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** client/schematic-library: read-through cache, integrity and size limit. */
class SchematicCacheTest {

    @TempDir
    Path directory;

    private final MutableClock clock = new MutableClock();

    @Test
    void a_stored_file_is_used_again_without_a_download() {
        SchematicCache cache = cache(1024);
        byte[] content = bytes(100, 1);

        cache.putFile(sha(content), new ByteArrayInputStream(content));

        assertTrue(cache.file(sha(content)).isPresent());
    }

    @Test
    void a_cache_survives_a_restart() {
        byte[] content = bytes(100, 1);
        cache(1024).putFile(sha(content), new ByteArrayInputStream(content));

        assertTrue(cache(1024).file(sha(content)).isPresent());
    }

    @Test
    void a_download_that_does_not_match_its_hash_is_not_stored() {
        SchematicCache cache = cache(1024);
        byte[] content = bytes(100, 1);
        String wrong = sha(bytes(100, 2));

        assertThrows(SchematicCache.CorruptDownloadException.class, () -> cache.putFile(wrong, new ByteArrayInputStream(content)));

        assertTrue(cache.file(wrong).isEmpty());
        assertEquals(0, cache.size());
    }

    @Test
    void a_cached_file_changed_on_disk_is_discarded() throws Exception {
        SchematicCache cache = cache(1024);
        byte[] content = bytes(100, 1);
        Path stored = cache.putFile(sha(content), new ByteArrayInputStream(content));

        Files.write(stored, bytes(100, 9));

        assertTrue(cache.file(sha(content)).isEmpty());
        assertFalse(Files.exists(stored));
    }

    @Test
    void the_least_recently_used_files_are_evicted_to_stay_within_the_limit() {
        SchematicCache cache = cache(250);
        byte[] first = bytes(100, 1);
        byte[] second = bytes(100, 2);
        byte[] third = bytes(100, 3);
        cache.putFile(sha(first), new ByteArrayInputStream(first));
        clock.advance();
        cache.putFile(sha(second), new ByteArrayInputStream(second));
        clock.advance();
        cache.file(sha(first));
        clock.advance();

        cache.putFile(sha(third), new ByteArrayInputStream(third));

        assertTrue(cache.file(sha(first)).isPresent());
        assertTrue(cache.file(sha(second)).isEmpty());
        assertTrue(cache.file(sha(third)).isPresent());
        assertTrue(cache.size() <= 250);
    }

    @Test
    void files_outside_the_cache_directory_are_never_evicted() throws Exception {
        Path local = Files.createDirectories(directory.resolve("local"));
        Path entry = Files.write(local.resolve("keep.nbt"), bytes(500, 1));
        SchematicCache cache = new SchematicCache(directory.resolve("cache"), 100, clock, new ObjectMapper());

        for (int i = 0; i < 5; i++) {
            byte[] content = bytes(90, 10 + i);
            cache.putFile(sha(content), new ByteArrayInputStream(content));
        }

        assertTrue(Files.exists(entry));
    }

    @Test
    void thumbnails_are_cached_by_version_hash() {
        SchematicCache cache = cache(1024);

        cache.putThumbnail("abc", bytes(50, 4));

        assertTrue(cache.thumbnail("abc").isPresent());
        assertTrue(cache.thumbnail("other").isEmpty());
    }

    private SchematicCache cache(long maxBytes) {
        return new SchematicCache(directory.resolve("cache"), maxBytes, clock, new ObjectMapper());
    }

    private static byte[] bytes(int length, int seed) {
        byte[] content = new byte[length];
        for (int i = 0; i < length; i++) {
            content[i] = (byte) (seed * 31 + i);
        }
        return content;
    }

    static String sha(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-21T10:00:00Z");

        void advance() {
            now = now.plusSeconds(1);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
