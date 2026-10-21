package io.github.dirnei.mc.heavymatica.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Cloud sessions on disk, one file per Minecraft UUID ({@code sessions/<uuid>.json}, D4), so a session is only ever
 * used by the Minecraft account that signed in.
 */
public final class SessionStore {

    /** What is kept between game starts. The Minecraft access token is never part of it. */
    public record StoredSession(UUID uuid, String name, String accessToken, OffsetDateTime accessTokenExpiresAt, String refreshToken) {
    }

    private final Path directory;
    private final ObjectMapper json;

    public SessionStore(Path directory, ObjectMapper json) {
        this.directory = directory;
        this.json = json;
    }

    public Optional<StoredSession> load(UUID uuid) {
        Path file = file(uuid);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            StoredSession session = json.readValue(file.toFile(), StoredSession.class);
            return session.uuid().equals(uuid) ? Optional.of(session) : Optional.empty();
        } catch (IOException e) {
            // A damaged file is a lost session, not a crash.
            return Optional.empty();
        }
    }

    public void save(StoredSession session) {
        try {
            Files.createDirectories(directory);
            Path temporary = directory.resolve(session.uuid() + ".json.tmp");
            json.writeValue(temporary.toFile(), session);
            Files.move(temporary, file(session.uuid()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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
        return directory.resolve(uuid + ".json");
    }
}
