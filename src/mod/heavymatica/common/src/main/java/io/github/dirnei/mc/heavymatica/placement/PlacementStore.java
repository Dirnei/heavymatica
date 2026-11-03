package io.github.dirnei.mc.heavymatica.placement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dirnei.mc.heavymatica.placement.Placement.Flip;
import io.github.dirnei.mc.heavymatica.placement.Placement.State;
import io.github.dirnei.mc.heavymatica.placement.Placement.Turn;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Anchored and locked placements of one player in one world or on one server: {@code placements/<player>/<world key>.json}.
 * FOLLOWING placements are never written. Saving is debounced: a change is written at most once every
 * {@link #SAVE_DELAY_MILLIS}, and {@link #flush()} writes what is pending (on disconnect).
 */
public final class PlacementStore {

    public static final long SAVE_DELAY_MILLIS = 2_000;
    private static final int FORMAT_VERSION = 1;
    private static final System.Logger LOGGER = System.getLogger(PlacementStore.class.getName());

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SourceJson(String kind, @Nullable String schematicId, @Nullable Integer version, String sha256) {
    }

    record PlacementJson(UUID id, SourceJson source, String name, int width, int height, int length, int pivotX2, int y, int pivotZ2,
                         Turn rotation, Flip mirror, String dimension, @Nullable Boolean locked, @Nullable LayersJson layers,
                         @Nullable Boolean inHand, @Nullable Map<String, String> swaps) {
    }

    record LayersJson(Layers.@Nullable Mode mode, int bottom, int top, @Nullable Integer followOffset) {
    }

    record FileJson(int version, List<PlacementJson> placements) {
    }

    private final Path directory;
    private final ObjectMapper json;
    private @Nullable Path file;
    private @Nullable List<Placement> pending;
    private long dueAt;

    public PlacementStore(Path directory, ObjectMapper json) {
        this.directory = directory;
        this.json = json;
    }

    /** The world key of a singleplayer save folder. */
    public static String singleplayerKey(String saveFolder) {
        return "sp-" + sanitize(saveFolder);
    }

    /** The world key of a server address; a missing port means the default port, so both spellings match. */
    public static String multiplayerKey(String address) {
        String trimmed = address.trim();
        // A bracketed IPv6 address has colons of its own; the port follows the closing bracket.
        int hostEnd = trimmed.startsWith("[") && trimmed.contains("]") ? trimmed.indexOf(']') + 1 : 0;
        int colon = trimmed.indexOf(':', hostEnd);
        boolean hasPort = colon > 0 && (hostEnd > 0 || trimmed.lastIndexOf(':') == colon) && colon < trimmed.length() - 1
                && trimmed.substring(colon + 1).chars().allMatch(Character::isDigit);
        String hostPort = hasPort ? trimmed.substring(0, colon) + "_" + trimmed.substring(colon + 1) : trimmed + "_25565";
        return "mp-" + sanitize(hostPort);
    }

    /** The world key of a Realm, by its name: the address of a Realm can change. */
    public static String realmsKey(String realmName) {
        return "realms-" + sanitize(realmName);
    }

    static String sanitize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    /** Switches to a world (flushing the previous one) and returns its placements, ANCHORED or LOCKED. */
    public List<Placement> open(UUID player, String worldKey) {
        flush();
        file = directory.resolve(player.toString()).resolve(worldKey + ".json");
        return load(file);
    }

    /** Leaves the world: writes what is pending and forgets the file. */
    public void close() {
        flush();
        file = null;
    }

    /** Remembers the current placements; written on a later {@link #tick} or {@link #flush}. */
    public void changed(List<Placement> placements, long nowMillis) {
        if (file == null) {
            return;
        }
        if (pending == null) {
            dueAt = nowMillis + SAVE_DELAY_MILLIS;
        }
        pending = List.copyOf(placements);
    }

    public void tick(long nowMillis) {
        if (pending != null && nowMillis >= dueAt) {
            flush();
        }
    }

    public void flush() {
        if (pending == null || file == null) {
            pending = null;
            return;
        }
        List<Placement> placements = pending;
        pending = null;
        try {
            write(file, placements);
        } catch (UncheckedIOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not save placements to " + file, e);
        }
    }

    boolean hasPending() {
        return pending != null;
    }

    void write(Path target, List<Placement> placements) {
        List<PlacementJson> anchored = placements.stream().filter(placement -> !placement.following()).map(PlacementStore::toJson).toList();
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            json.writeValue(temporary.toFile(), new FileJson(FORMAT_VERSION, anchored));
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads a file; a missing file is empty, a corrupt one is kept aside as {@code .corrupt} and read as empty. */
    List<Placement> load(Path source) {
        if (!Files.isRegularFile(source)) {
            return List.of();
        }
        try {
            FileJson read = json.readValue(source.toFile(), FileJson.class);
            return read.placements() == null ? List.of() : read.placements().stream().map(PlacementStore::fromJson).toList();
        } catch (IOException | RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Placements in " + source + " are unreadable and were set aside: " + e.getMessage());
            try {
                Files.move(source, source.resolveSibling(source.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Leave it; it is overwritten on the next save.
            }
            return List.of();
        }
    }

    private static PlacementJson toJson(Placement placement) {
        SourceJson source = switch (placement.source()) {
            case PlacementSource.Cloud cloud -> new SourceJson("cloud", cloud.schematicId(), cloud.version(), cloud.sha256());
            case PlacementSource.Local local -> new SourceJson("local", null, null, local.sha256());
        };
        return new PlacementJson(placement.id(), source, placement.name(), placement.width(), placement.height(), placement.length(),
                placement.pivotX2(), placement.y(), placement.pivotZ2(), placement.rotation(), placement.mirror(), placement.dimension(),
                placement.locked(), new LayersJson(placement.layers().mode(), placement.layers().bottom(), placement.layers().top(),
                placement.layers().followOffset()), placement.inHand(), placement.swaps());
    }

    private static Placement fromJson(PlacementJson read) {
        PlacementSource source = switch (read.source().kind()) {
            case "cloud" -> new PlacementSource.Cloud(require(read.source().schematicId()), require(read.source().version()), read.source().sha256());
            case "local" -> new PlacementSource.Local(read.source().sha256());
            default -> throw new IllegalArgumentException("Unknown source kind " + read.source().kind());
        };
        if (read.width() <= 0 || read.height() <= 0 || read.length() <= 0) {
            throw new IllegalArgumentException("Placement " + read.id() + " has no size");
        }
        return new Placement(read.id(), source, read.name(), read.width(), read.height(), read.length(), read.pivotX2(), read.y(), read.pivotZ2(),
                read.rotation(), read.mirror(), read.dimension(), Boolean.TRUE.equals(read.locked()) ? State.LOCKED : State.ANCHORED, 0,
                layers(read.layers()), Boolean.TRUE.equals(read.inHand()), read.swaps() == null ? Map.of() : read.swaps());
    }

    private static Layers layers(@Nullable LayersJson read) {
        if (read == null || read.mode() == null) {
            return Layers.NONE;
        }
        int followOffset = read.followOffset() == null ? Layers.DEFAULT_FOLLOW_OFFSET : read.followOffset();
        return new Layers(read.mode(), read.bottom(), read.top(), 0).withFollowOffset(followOffset);
    }

    private static <T> T require(@Nullable T value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing value in a cloud source");
        }
        return value;
    }
}
