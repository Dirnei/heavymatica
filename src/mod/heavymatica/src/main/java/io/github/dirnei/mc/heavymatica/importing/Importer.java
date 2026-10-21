package io.github.dirnei.mc.heavymatica.importing;

import io.github.dirnei.mc.heavymatica.formats.RejectionCode;
import io.github.dirnei.mc.heavymatica.formats.SchematicReader;
import io.github.dirnei.mc.heavymatica.formats.SchematicRejectedException;
import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import io.github.dirnei.mc.heavymatica.library.LocalLibrary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Imports schematic files into the library as local-only entries (client/schematic-import). Every file is validated
 * first; a rejected file changes nothing. A file identical to an entry already in the library is reported with that
 * entry instead of being imported, unless the player imports it anyway.
 */
public final class Importer {

    public enum Outcome {
        IMPORTED,
        REJECTED,
        DUPLICATE,
    }

    /**
     * The result for one file.
     *
     * @param match  the library entry a duplicate matches
     * @param reason why the file was rejected
     */
    public record Result(Path file, Outcome outcome, LocalLibrary.@Nullable LocalEntry entry, @Nullable LibraryEntry match,
                         @Nullable RejectionCode reason) {
    }

    private final LocalLibrary local;
    private final Supplier<List<LibraryEntry>> library;

    /** @param library the library's current entries, cloud and local, for duplicate detection */
    public Importer(LocalLibrary local, Supplier<List<LibraryEntry>> library) {
        this.local = local;
        this.library = library;
    }

    /** Imports each file separately; one bad file never stops the others. */
    public List<Result> importAll(List<Path> files) {
        return files.stream().map(file -> importFile(file, false)).toList();
    }

    /** Imports one file. With {@code anyway}, a duplicate is imported as a local entry all the same. */
    public Result importFile(Path file, boolean anyway) {
        byte[] content;
        try {
            content = Files.readAllBytes(file);
        } catch (IOException e) {
            return new Result(file, Outcome.REJECTED, null, null, RejectionCode.UNSUPPORTED_FORMAT);
        } catch (OutOfMemoryError | UncheckedIOException e) {
            return new Result(file, Outcome.REJECTED, null, null, RejectionCode.FILE_TOO_LARGE);
        }
        try {
            SchematicReader.read(content);
        } catch (SchematicRejectedException rejected) {
            return new Result(file, Outcome.REJECTED, null, null, rejected.code());
        }

        String sha256 = LocalLibrary.sha256(content);
        Optional<LibraryEntry> match = library.get().stream().filter(entry -> entry.sha256().equals(sha256)).findFirst();
        if (match.isPresent() && !anyway) {
            return new Result(file, Outcome.DUPLICATE, null, match.get(), null);
        }
        return new Result(file, Outcome.IMPORTED, local.add(file), match.orElse(null), null);
    }
}
