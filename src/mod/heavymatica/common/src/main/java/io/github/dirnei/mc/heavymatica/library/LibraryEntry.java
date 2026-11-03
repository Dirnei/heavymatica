package io.github.dirnei.mc.heavymatica.library;

import io.github.dirnei.mc.heavymatica.cloud.model.SchematicSummary;
import io.github.dirnei.mc.heavymatica.cloud.model.Version;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * One card in the library: a cloud schematic (its latest version) or a local-only entry.
 *
 * @param id cloud schematic ID, or the content hash of a local entry
 */
public record LibraryEntry(Kind kind, String id, String name, @Nullable String folder, List<String> tags, String sha256, String format,
                           int width, int height, int length, long blockTotal, boolean hasThumbnail, int version,
                           @Nullable OffsetDateTime updatedAt) {

    public enum Kind {
        CLOUD,
        LOCAL,
    }

    public boolean cloud() {
        return kind == Kind.CLOUD;
    }

    public String dimensions() {
        return width + " x " + height + " x " + length;
    }

    /** Text, folder and tag filters combine; empty filters match everything. A folder filter includes its subfolders. */
    public boolean matches(String text, @Nullable String folderFilter, @Nullable String tagFilter) {
        if (!text.isBlank() && !name.toLowerCase(Locale.ROOT).contains(text.trim().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (folderFilter != null && !inFolder(folder, folderFilter)) {
            return false;
        }
        return tagFilter == null || tags.contains(tagFilter);
    }

    /** True when {@code path} is {@code folder} or below it: {@code houses} holds {@code houses/oak} but not {@code housesmall}. */
    public static boolean inFolder(@Nullable String path, String folder) {
        return path != null && (path.equals(folder) || path.startsWith(folder + "/"));
    }

    static LibraryEntry of(SchematicSummary summary) {
        Version latest = summary.getLatest();
        return new LibraryEntry(Kind.CLOUD, summary.getId(), summary.getName(), summary.getFolder(), List.copyOf(summary.getTags()),
                latest.getSha256(), latest.getFormat().getValue(), latest.getDimensions().getWidth(), latest.getDimensions().getHeight(),
                latest.getDimensions().getLength(), latest.getBlockTotal(), latest.getHasThumbnail(), latest.getNumber(), summary.getUpdatedAt());
    }

    static LibraryEntry of(LocalLibrary.LocalEntry local) {
        return new LibraryEntry(Kind.LOCAL, local.sha256(), local.name(), null, List.of(), local.sha256(), local.format(), local.width(),
                local.height(), local.length(), local.blockTotal(), false, 0, local.importedAt());
    }
}
