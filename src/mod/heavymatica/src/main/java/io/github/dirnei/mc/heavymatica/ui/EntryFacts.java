package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.blocks.BlockResolver;
import io.github.dirnei.mc.heavymatica.cloud.model.BlockCount;
import io.github.dirnei.mc.heavymatica.cloud.model.Version;
import io.github.dirnei.mc.heavymatica.formats.BlockStates;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.formats.SchematicReader;
import io.github.dirnei.mc.heavymatica.library.Library;
import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import io.github.dirnei.mc.heavymatica.library.LocalLibrary;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The block counts and the data version of library versions, by content hash. A cloud version's come from its details
 * when they are loaded; a local entry's, or a cached cloud version's, are read from the file in the background.
 */
final class EntryFacts {

    /** @param dataVersion the data version the file records, or null when it records none */
    record Facts(DetailBlockCounts counts, @Nullable Integer dataVersion) {
    }

    private final Library library;
    private final Map<String, Facts> read = new HashMap<>();
    private final Set<String> reading = new HashSet<>();
    private final Runnable changed;

    /** @param changed called on the game thread when facts read in the background arrive */
    EntryFacts(Library library, Runnable changed) {
        this.library = library;
        this.changed = changed;
    }

    /**
     * The facts of a version, or empty while they are not known yet. Starts reading the file when it is on this
     * machine.
     *
     * @param version the cloud version's details, when loaded
     */
    Optional<Facts> get(LibraryEntry entry, String sha256, @Nullable Version version) {
        if (version != null && version.getBlockCounts() != null && !version.getBlockCounts().isEmpty()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (BlockCount count : version.getBlockCounts()) {
                counts.merge(count.getBlock(), count.getCount(), Long::sum);
            }
            return Optional.of(new Facts(counts(counts, version.getDataVersion()), version.getDataVersion()));
        }
        Facts known = read.get(sha256);
        if (known != null) {
            return Optional.of(known);
        }
        if (reading.add(sha256)) {
            file(entry, sha256).ifPresent(file -> CompletableFuture.supplyAsync(() -> SchematicReader.read(file), Util.backgroundExecutor())
                    .whenComplete((model, failure) -> Minecraft.getInstance().execute(() -> {
                        if (failure != null) {
                            Heavymatica.LOGGER.debug("Reading {} for its details failed", entry.name(), failure);
                            return;
                        }
                        read.put(sha256, of(model));
                        changed.run();
                    })));
        }
        return Optional.empty();
    }

    static Facts of(SchematicModel model) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (SchematicModel.BlockCount count : model.blockCounts()) {
            counts.merge(count.block(), count.count(), Long::sum);
        }
        return new Facts(counts(counts, model.dataVersion()), model.dataVersion());
    }

    /** The file on this machine: a local entry's copy, or a cloud version in the cache. Never downloads. */
    private Optional<Path> file(LibraryEntry entry, String sha256) {
        if (!entry.cloud()) {
            LocalLibrary locals = library.local();
            return locals.find(sha256).map(locals::file);
        }
        return library.cached(sha256) ? library.cachedFile(sha256) : Optional.empty();
    }

    /** Counts under the names the game uses today, upgraded from the data version. */
    private static DetailBlockCounts counts(Map<String, Long> counts, @Nullable Integer dataVersion) {
        return DetailBlockCounts.of(counts, block -> BlockStates.id(BlockResolver.resolution().upgraded(block, dataVersion)),
                block -> MaterialsScreen.block(block) != null);
    }
}
