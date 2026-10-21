package io.github.dirnei.mc.heavymatica.library;

import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.CloudException;
import io.github.dirnei.mc.heavymatica.cache.SchematicCache;
import io.github.dirnei.mc.heavymatica.cloud.model.DownloadLinks;
import io.github.dirnei.mc.heavymatica.cloud.model.OrganizeRequest;
import io.github.dirnei.mc.heavymatica.cloud.model.SchematicDetails;
import io.github.dirnei.mc.heavymatica.cloud.model.SchematicPage;
import io.github.dirnei.mc.heavymatica.cloud.model.SchematicSummary;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * The player's library: their cloud schematics and local-only entries in one list (client/schematic-library). Keeps
 * the last known cloud listing for offline use, serves version files and thumbnails through the read-through cache,
 * and organizes cloud schematics. Every call completes on the completion executor and never blocks the caller.
 */
public final class Library {

    /** Whether the cloud part of the library is current. */
    public enum CloudState {
        /** Signed out: no cloud entries. */
        NONE,
        LOADING,
        CURRENT,
        /** The cloud could not be reached or the session expired: the last known listing is shown. */
        OFFLINE,
    }

    private static final int PAGE_SIZE = 100;

    private final CloudAccount account;
    private final Supplier<UUID> player;
    private final LocalLibrary local;
    private final StoredListing listing;
    private final SchematicCache cache;
    private final Executor completion;
    private final Executor background;
    private final HttpClient downloads = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final List<Consumer<Library>> listeners = new CopyOnWriteArrayList<>();

    private volatile List<SchematicSummary> cloud = List.of();
    private volatile CloudState state = CloudState.NONE;

    public Library(CloudAccount account, Supplier<UUID> player, LocalLibrary local, StoredListing listing, SchematicCache cache,
                   Executor completion, Executor background) {
        this.account = account;
        this.player = player;
        this.local = local;
        this.listing = listing;
        this.cache = cache;
        this.completion = completion;
        this.background = background;
    }

    /** Subscribes to changes; run the returned handle to unsubscribe. */
    public Runnable onChange(Consumer<Library> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public CloudState cloudState() {
        return state;
    }

    public LocalLibrary local() {
        return local;
    }

    /** Shows the stored listing of the current account at once; call {@link #refresh()} to bring it up to date. */
    public void loadStored() {
        cloud = account.status() == CloudAccount.Status.SIGNED_OUT ? List.of() : listing.load(player.get());
        state = cloud.isEmpty() && account.status() == CloudAccount.Status.SIGNED_OUT ? CloudState.NONE : CloudState.OFFLINE;
        changed();
    }

    /** Fetches the whole cloud listing. Signed out, nothing is requested. */
    public CompletableFuture<Void> refresh() {
        if (!account.signedIn()) {
            loadStored();
            return CompletableFuture.completedFuture(null);
        }
        state = CloudState.LOADING;
        changed();
        UUID owner = player.get();
        return fetchAll(null, new ArrayList<>()).handle((all, failure) -> {
            if (failure == null) {
                cloud = List.copyOf(all);
                listing.save(owner, cloud);
                state = CloudState.CURRENT;
            } else {
                cloud = listing.load(owner);
                state = account.status() == CloudAccount.Status.SIGNED_OUT ? CloudState.NONE : CloudState.OFFLINE;
            }
            changed();
            return null;
        });
    }

    /** Deletes the stored listing; part of signing out. */
    public void signedOut() {
        listing.delete(player.get());
        cloud = List.of();
        state = CloudState.NONE;
        changed();
    }

    /** Cloud entries first, newest change first, then local entries, newest import first. */
    public List<LibraryEntry> entries() {
        List<LibraryEntry> all = new ArrayList<>();
        cloud.stream().map(LibraryEntry::of).sorted(Comparator.comparing(LibraryEntry::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))).forEach(all::add);
        local.entries().stream().map(LibraryEntry::of).sorted(Comparator.comparing(LibraryEntry::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))).forEach(all::add);
        return all;
    }

    public List<LibraryEntry> filtered(String text, @Nullable String folder, @Nullable String tag) {
        return entries().stream().filter(entry -> entry.matches(text, folder, tag)).toList();
    }

    public List<String> folders() {
        TreeSet<String> folders = new TreeSet<>();
        cloud.forEach(entry -> {
            if (entry.getFolder() != null) {
                folders.add(entry.getFolder());
            }
        });
        return List.copyOf(folders);
    }

    public List<String> tags() {
        TreeSet<String> tags = new TreeSet<>();
        cloud.forEach(entry -> tags.addAll(entry.getTags()));
        return List.copyOf(tags);
    }

    /** True when the version file is in the cache, so it can be used offline. */
    public boolean cached(String sha256) {
        return cache.file(sha256).isPresent();
    }

    public CompletableFuture<SchematicDetails> details(String schematicId) {
        return account.client().call(apis -> apis.schematics().getSchematic(schematicId));
    }

    public CompletableFuture<SchematicDetails> organize(String schematicId, OrganizeRequest request) {
        return account.client().call(apis -> apis.schematics().organizeSchematic(schematicId, request)).thenCompose(details -> refresh().thenApply(ignored -> details));
    }

    public CompletableFuture<Void> deleteSchematic(String schematicId) {
        return account.client().call(apis -> apis.schematics().deleteSchematic(schematicId)).thenCompose(ignored -> refresh());
    }

    public CompletableFuture<Void> deleteVersion(String schematicId, int version) {
        return account.client().call(apis -> apis.schematics().deleteVersion(schematicId, version)).thenCompose(ignored -> refresh());
    }

    /** Renames a local-only entry; works signed out. */
    public void renameLocal(String sha256, String name) {
        local.rename(sha256, name);
        changed();
    }

    /** Deletes a local-only entry and its copy of the file; works signed out. */
    public void deleteLocal(String sha256) {
        local.remove(sha256);
        changed();
    }

    /** The version file from the cache, or downloaded into it through a short-lived link. */
    public CompletableFuture<Path> file(String schematicId, int version, String sha256) {
        Optional<Path> cached = cache.file(sha256);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }
        return links(schematicId, version).thenCompose(links -> download(links.getFileUrl().toString()))
                .thenApplyAsync(stream -> cache.putFile(sha256, stream), background)
                .thenApplyAsync(path -> path, completion);
    }

    /** The version's thumbnail from the cache, or downloaded into it; empty when the version has none. */
    public CompletableFuture<Optional<Path>> thumbnail(String schematicId, int version, String sha256) {
        Optional<Path> cached = cache.thumbnail(sha256);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        if (!account.signedIn()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return links(schematicId, version).thenCompose(links -> links.getThumbnailUrl() == null
                ? CompletableFuture.completedFuture(Optional.<Path>empty())
                : download(links.getThumbnailUrl().toString())
                        .thenApplyAsync(stream -> Optional.of(cache.putThumbnail(sha256, readAll(stream))), background)
                        .thenApplyAsync(path -> path, completion));
    }

    /** A thumbnail already in the cache, for example one rendered for a local entry. */
    public Optional<Path> cachedThumbnail(String sha256) {
        return cache.thumbnail(sha256);
    }

    /** Caches a thumbnail rendered on this machine. */
    public Path storeThumbnail(String sha256, byte[] png) {
        return cache.putThumbnail(sha256, png);
    }

    private CompletableFuture<DownloadLinks> links(String schematicId, int version) {
        return account.client().call(apis -> apis.schematics().createLinks(schematicId, version));
    }

    private CompletableFuture<InputStream> download(String url) {
        HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build();
        return downloads.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            if (response.statusCode() / 100 != 2) {
                throw new CloudException(response.statusCode(), null, "Download failed with " + response.statusCode(), null);
            }
            return response.body();
        });
    }

    private CompletableFuture<List<SchematicSummary>> fetchAll(@Nullable String cursor, List<SchematicSummary> collected) {
        return account.client().call(apis -> apis.schematics().listSchematics(null, null, null, cursor, PAGE_SIZE)).thenCompose((SchematicPage page) -> {
            collected.addAll(page.getItems());
            return page.getNextCursor() == null ? CompletableFuture.completedFuture(collected) : fetchAll(page.getNextCursor(), collected);
        });
    }

    private static byte[] readAll(InputStream stream) {
        try (stream) {
            return stream.readAllBytes();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private void changed() {
        completion.execute(() -> listeners.forEach(listener -> listener.accept(this)));
    }
}
