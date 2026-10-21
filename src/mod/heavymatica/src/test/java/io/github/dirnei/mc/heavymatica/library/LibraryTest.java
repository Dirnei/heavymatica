package io.github.dirnei.mc.heavymatica.library;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.SessionStore;
import io.github.dirnei.mc.heavymatica.account.StubCloudAccess;
import io.github.dirnei.mc.heavymatica.cache.SchematicCache;
import io.github.dirnei.mc.heavymatica.cloud.client.ApiClient;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryTest {

    private static final UUID PLAYER = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    @TempDir
    Path directory;

    private StubCloudAccess cloud;

    @BeforeEach
    void start() throws Exception {
        cloud = new StubCloudAccess();
    }

    @AfterEach
    void stop() {
        cloud.close();
    }

    @Test
    void filters_combine_text_folder_and_tag() {
        LibraryEntry tower = entry("Watch Tower", "houses", List.of("spruce"));
        LibraryEntry otherTower = entry("Tower of Light", "towers", List.of("spruce"));
        LibraryEntry barn = entry("Barn", "houses", List.of("spruce"));

        assertTrue(tower.matches("tow", "houses", "spruce"));
        assertFalse(otherTower.matches("tow", "houses", "spruce"));
        assertFalse(barn.matches("tow", "houses", "spruce"));
        assertTrue(barn.matches("", null, null));
        assertTrue(tower.matches("  TOWER ", null, null));
    }

    @Test
    void cloud_and_local_entries_appear_in_one_list() throws Exception {
        Library library = signedInLibrary();
        library.local().add(fixture("house.nbt"));
        cloud.stub().answer(200, page(summary("s1", "Cloud One", "2026-10-21T10:00:00Z"), summary("s2", "Cloud Two", "2026-10-21T11:00:00Z")));

        library.refresh().get(10, TimeUnit.SECONDS);
        List<LibraryEntry> entries = library.entries();

        assertEquals(List.of("Cloud Two", "Cloud One", "house"), entries.stream().map(LibraryEntry::name).toList());
        assertEquals(List.of(true, true, false), entries.stream().map(LibraryEntry::cloud).toList());
        assertEquals(Library.CloudState.CURRENT, library.cloudState());
    }

    @Test
    void an_unreachable_cloud_shows_the_last_known_listing_as_offline() throws Exception {
        Library online = signedInLibrary();
        cloud.stub().answer(200, page(summary("s1", "Remembered", "2026-10-21T10:00:00Z")));
        online.refresh().get(10, TimeUnit.SECONDS);

        Library offline = library(URI.create("http://127.0.0.1:1"));
        offline.refresh().get(15, TimeUnit.SECONDS);

        assertEquals(Library.CloudState.OFFLINE, offline.cloudState());
        assertEquals(List.of("Remembered"), offline.entries().stream().map(LibraryEntry::name).toList());
    }

    @Test
    void the_stored_listing_belongs_to_one_minecraft_account() throws Exception {
        StoredListing listing = new StoredListing(directory.resolve("library"), ApiClient.createDefaultObjectMapper());
        listing.save(PLAYER, List.of(ApiClient.createDefaultObjectMapper().readValue(summary("s1", "Mine", "2026-10-21T10:00:00Z"),
                io.github.dirnei.mc.heavymatica.cloud.model.SchematicSummary.class)));

        assertEquals(1, listing.load(PLAYER).size());
        assertTrue(listing.load(UUID.randomUUID()).isEmpty());
    }

    @Test
    void signing_out_deletes_the_stored_listing() throws Exception {
        Library library = signedInLibrary();
        cloud.stub().answer(200, page(summary("s1", "Gone soon", "2026-10-21T10:00:00Z")));
        library.refresh().get(10, TimeUnit.SECONDS);

        library.signedOut();

        assertTrue(library.entries().isEmpty());
        assertTrue(new StoredListing(directory.resolve("library"), ApiClient.createDefaultObjectMapper()).load(PLAYER).isEmpty());
        assertEquals(Library.CloudState.NONE, library.cloudState());
    }

    @Test
    void a_signed_out_library_requests_nothing() throws Exception {
        CloudAccount account = cloud.account(directory, false);
        Library library = new Library(account, () -> PLAYER, local(), listing(), cache(), Runnable::run, Runnable::run);

        library.refresh().get(5, TimeUnit.SECONDS);

        assertTrue(cloud.stub().requests().isEmpty());
        assertEquals(Library.CloudState.NONE, library.cloudState());
    }

    @Test
    void local_entries_can_be_renamed_and_deleted_while_signed_out() throws Exception {
        CloudAccount account = cloud.account(directory, false);
        Library library = new Library(account, () -> PLAYER, local(), listing(), cache(), Runnable::run, Runnable::run);
        Path source = fixture("house.nbt");
        LocalLibrary.LocalEntry entry = library.local().add(source);
        Path copy = library.local().file(entry);

        library.renameLocal(entry.sha256(), "Starter Hut");
        assertEquals(List.of("Starter Hut"), library.entries().stream().map(LibraryEntry::name).toList());
        assertEquals("Starter Hut", local().find(entry.sha256()).orElseThrow().name());

        library.deleteLocal(entry.sha256());
        assertTrue(library.entries().isEmpty());
        assertFalse(Files.exists(copy));
        assertTrue(Files.exists(source));
        assertTrue(cloud.stub().requests().isEmpty());
    }

    @Test
    void version_files_download_once_into_the_cache() throws Exception {
        Library library = signedInLibrary();
        byte[] file = Files.readAllBytes(fixture("house.nbt"));
        String sha = LocalLibrary.sha256(file);
        cloud.stub().answer(200, "{\"fileUrl\":\"" + cloud.stub().uri() + "/blob\",\"thumbnailUrl\":null,\"expiresAt\":\"2026-10-21T10:05:00Z\"}");
        cloud.stub().answerBytes(200, file);

        Path first = library.file("s1", 1, sha).get(10, TimeUnit.SECONDS);
        int requests = cloud.stub().requests().size();
        Path second = library.file("s1", 1, sha).get(10, TimeUnit.SECONDS);

        assertArrayEquals(file, Files.readAllBytes(first));
        assertEquals(first, second);
        assertEquals(requests, cloud.stub().requests().size());
        assertTrue(library.cached(sha));
    }

    private Library signedInLibrary() throws Exception {
        return new Library(cloud.account(directory, true), () -> PLAYER, local(), listing(), cache(), Runnable::run, Runnable::run);
    }

    private Library library(URI uri) {
        CloudAccount account = new CloudAccount(uri, Runnable::run, Runnable::run, () -> new CloudAccount.Player(PLAYER, "Dirnei"),
                (profile, serverId) -> { }, new SessionStore(directory.resolve("sessions"), ApiClient.createDefaultObjectMapper()), () -> { });
        account.restore();
        return new Library(account, () -> PLAYER, local(), listing(), cache(), Runnable::run, Runnable::run);
    }

    private LocalLibrary local() {
        return new LocalLibrary(directory.resolve("local"), ApiClient.createDefaultObjectMapper(), Clock.systemUTC());
    }

    private StoredListing listing() {
        return new StoredListing(directory.resolve("library"), ApiClient.createDefaultObjectMapper());
    }

    private SchematicCache cache() {
        return new SchematicCache(directory.resolve("cache"), 64L * 1024 * 1024, Clock.systemUTC(), ApiClient.createDefaultObjectMapper());
    }

    static Path fixture(String name) {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("src").resolve("conformance").resolve("files").resolve(name);
            if (Files.exists(candidate)) {
                return candidate;
            }
            candidate = dir.resolve("conformance").resolve("files").resolve(name);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No fixture " + name);
    }

    private static LibraryEntry entry(String name, String folder, List<String> tags) {
        return new LibraryEntry(LibraryEntry.Kind.CLOUD, name, name, folder, tags, "sha", "sponge", 1, 1, 1, 1, false, 1, OffsetDateTime.now());
    }

    static String summary(String id, String name, String updatedAt) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"folder\":null,\"tags\":[],\"updatedAt\":\"" + updatedAt + "\",\"latest\":{"
                + "\"number\":1,\"sha256\":\"ab\",\"format\":\"sponge\",\"sizeBytes\":5,\"dimensions\":{\"width\":1,\"height\":2,\"length\":3},"
                + "\"dataVersion\":null,\"blockTotal\":4,\"blockCounts\":[],\"skippedEntities\":0,\"hasThumbnail\":false,\"createdAt\":\"" + updatedAt + "\"}}";
    }

    static String page(String... summaries) {
        return "{\"items\":[" + String.join(",", summaries) + "],\"nextCursor\":null}";
    }
}
