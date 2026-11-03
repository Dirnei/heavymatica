package io.github.dirnei.mc.heavymatica.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes library files into a folder, by default the game's {@code schematics} folder, named after the entry. A file
 * is first written next to its target and then moved into place, so a failed export never leaves half a file.
 */
public final class Exporter {

    public enum Conflict {
        OVERWRITE,
        KEEP_BOTH,
    }

    private static final String FALLBACK = "schematic";

    private final Path folder;

    public Exporter(Path folder) {
        this.folder = folder;
    }

    public static Exporter schematicsFolder(Path gameDirectory) {
        return new Exporter(gameDirectory.resolve("schematics"));
    }

    /** The name with characters Windows, macOS or Linux reject in file names replaced by {@code _}. */
    public static String fileName(String name) {
        StringBuilder cleaned = new StringBuilder(name.length());
        name.codePoints().forEach(c -> cleaned.appendCodePoint(c < 0x20 || c == 0x7F || "<>:\"/\\|?*".indexOf(c) >= 0 ? '_' : c));
        int end = cleaned.length();
        while (end > 0 && (cleaned.charAt(end - 1) == '.' || cleaned.charAt(end - 1) == ' ')) {
            end--;
        }
        String result = cleaned.substring(0, end).strip();
        return result.isEmpty() || result.chars().allMatch(c -> c == '_') ? FALLBACK : result;
    }

    /** Where an entry with this name goes when nothing is in the way. */
    public Path target(String name, String extension) {
        return folder.resolve(fileName(name) + "." + extension);
    }

    public boolean exists(String name, String extension) {
        return Files.exists(target(name, extension));
    }

    /** Writes {@code source} under the entry's name; with KEEP_BOTH an existing file gets the first free {@code (n)} name. */
    public Path export(Path source, String name, String extension, Conflict conflict) throws IOException {
        Path target = target(name, extension);
        if (conflict == Conflict.KEEP_BOTH) {
            for (int n = 2; Files.exists(target); n++) {
                target = folder.resolve(fileName(name) + " (" + n + ")." + extension);
            }
        }
        Files.createDirectories(folder);
        Path temporary = Files.createTempFile(folder, ".heavymatica-export-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }
}
