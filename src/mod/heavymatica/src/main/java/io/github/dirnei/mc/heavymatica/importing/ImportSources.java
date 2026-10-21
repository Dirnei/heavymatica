package io.github.dirnei.mc.heavymatica.importing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Where players keep schematic files: the game directory's {@code schematics} folder (Litematica's) and
 * Heavymatica's own {@code heavymatica/import} folder, including subfolders.
 */
public final class ImportSources {

    /** Extensions offered in the import list. Content decides the format; the extension only filters the list. */
    private static final Set<String> EXTENSIONS = Set.of("litematic", "schem", "schematic", "nbt");

    /** A file found in one of the folders, with its path relative to that folder for display. */
    public record Candidate(Path file, String folder, String relativePath) {
    }

    private ImportSources() {
    }

    public static List<Path> folders(Path gameDirectory) {
        return List.of(gameDirectory.resolve("schematics"), gameDirectory.resolve("heavymatica").resolve("import"));
    }

    public static List<Candidate> scan(Path gameDirectory) {
        List<Candidate> found = new ArrayList<>();
        for (Path folder : folders(gameDirectory)) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(folder)) {
                files.filter(Files::isRegularFile).filter(ImportSources::supported).sorted(Comparator.naturalOrder())
                        .forEach(file -> found.add(new Candidate(file, gameDirectory.relativize(folder).toString().replace('\\', '/'),
                                folder.relativize(file).toString().replace('\\', '/'))));
            } catch (IOException e) {
                // An unreadable folder offers nothing.
            }
        }
        return found;
    }

    private static boolean supported(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
