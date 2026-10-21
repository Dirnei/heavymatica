package io.github.dirnei.mc.heavymatica.importing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.CloudException;
import io.github.dirnei.mc.heavymatica.account.StubCloudAccess;
import io.github.dirnei.mc.heavymatica.cloud.client.ApiClient;
import io.github.dirnei.mc.heavymatica.cloud.model.ProblemCode;
import io.github.dirnei.mc.heavymatica.cloud.model.UploadResult;
import io.github.dirnei.mc.heavymatica.formats.RejectionCode;
import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import io.github.dirnei.mc.heavymatica.library.LocalLibrary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** client/schematic-import: import folders, validation, local copies, duplicates and uploads. */
class ImportingTest {

    @TempDir
    Path directory;

    private StubCloudAccess cloud;
    private LocalLibrary local;
    private final List<LibraryEntry> libraryEntries = new ArrayList<>();

    @BeforeEach
    void start() throws Exception {
        cloud = new StubCloudAccess();
        local = new LocalLibrary(directory.resolve("local"), ApiClient.createDefaultObjectMapper(), Clock.systemUTC());
    }

    @AfterEach
    void stop() {
        cloud.close();
    }

    @Test
    void the_import_list_shows_schematic_files_from_both_folders_including_subfolders() throws Exception {
        Path game = Files.createDirectories(directory.resolve("game"));
        copy("house.litematica-v6.litematic", Files.createDirectories(game.resolve("schematics/towns")).resolve("house.litematic"));
        copy("house.v2.schem", Files.createDirectories(game.resolve("heavymatica/import")).resolve("barn.schem"));
        Files.writeString(game.resolve("schematics/readme.txt"), "not listed");

        List<ImportSources.Candidate> found = ImportSources.scan(game);

        assertEquals(List.of("schematics/towns/house.litematic", "heavymatica/import/barn.schem"),
                found.stream().map(candidate -> candidate.folder() + "/" + candidate.relativePath()).toList());
    }

    @Test
    void each_file_is_imported_separately_and_rejections_carry_their_reason() throws Exception {
        Path good = copy("house.nbt", directory.resolve("house.nbt"));
        Path alsoGood = copy("house.v3.schem", directory.resolve("house.v3.schem"));
        Path bad = copy("truncated.litematic", directory.resolve("broken.litematic"));

        List<Importer.Result> results = importer().importAll(List.of(good, bad, alsoGood));

        assertEquals(List.of(Importer.Outcome.IMPORTED, Importer.Outcome.REJECTED, Importer.Outcome.IMPORTED), results.stream().map(Importer.Result::outcome).toList());
        assertEquals(RejectionCode.MALFORMED_NBT, results.get(1).reason());
        assertEquals(2, local.entries().size());
    }

    @Test
    void a_rejected_file_leaves_the_library_and_the_source_unchanged() throws Exception {
        Path bad = copy("truncated.litematic", directory.resolve("broken.litematic"));
        byte[] before = Files.readAllBytes(bad);

        importer().importAll(List.of(bad));

        assertTrue(local.entries().isEmpty());
        assertArrayEquals(before, Files.readAllBytes(bad));
    }

    @Test
    void a_local_entry_keeps_working_after_its_source_is_deleted() throws Exception {
        Path source = copy("house.nbt", directory.resolve("house.nbt"));
        LocalLibrary.LocalEntry entry = importer().importAll(List.of(source)).getFirst().entry();

        Files.delete(source);

        assertTrue(Files.exists(local.file(entry)));
        assertEquals(1, new LocalLibrary(directory.resolve("local"), ApiClient.createDefaultObjectMapper(), Clock.systemUTC()).entries().size());
    }

    @Test
    void a_file_matching_a_cloud_version_is_reported_with_that_entry() throws Exception {
        Path source = copy("house.nbt", directory.resolve("barn.nbt"));
        String sha = LocalLibrary.sha256(Files.readAllBytes(source));
        libraryEntries.add(new LibraryEntry(LibraryEntry.Kind.CLOUD, "s1", "Barn", null, List.of(), sha, "structure", 5, 4, 6, 77, false, 2, OffsetDateTime.now()));

        Importer.Result result = importer().importAll(List.of(source)).getFirst();

        assertEquals(Importer.Outcome.DUPLICATE, result.outcome());
        assertEquals("Barn", result.match().name());
        assertEquals(2, result.match().version());
        assertTrue(local.entries().isEmpty());
    }

    @Test
    void a_duplicate_can_be_imported_anyway() throws Exception {
        Path source = copy("house.nbt", directory.resolve("barn.nbt"));
        libraryEntries.add(new LibraryEntry(LibraryEntry.Kind.CLOUD, "s1", "Barn", null, List.of(),
                LocalLibrary.sha256(Files.readAllBytes(source)), "structure", 5, 4, 6, 77, false, 2, OffsetDateTime.now()));

        Importer.Result result = importer().importFile(source, true);

        assertEquals(Importer.Outcome.IMPORTED, result.outcome());
        assertEquals(1, local.entries().size());
    }

    @Test
    void a_successful_upload_turns_the_entry_into_a_cloud_schematic_with_a_thumbnail() throws Exception {
        LocalLibrary.LocalEntry entry = local.add(copy("house.nbt", directory.resolve("house.nbt")));
        cloud.stub().answer(201, uploadResult("s9", 1)).answer(204, "");
        List<String> rendered = new ArrayList<>();

        UploadResult result = uploader(file -> {
            rendered.add(file.getFileName().toString());
            return CompletableFuture.completedFuture(new byte[] {1, 2, 3});
        }).upload(entry, null).get(10, TimeUnit.SECONDS);

        assertEquals("s9", result.getSchematicId());
        assertTrue(local.entries().isEmpty());
        assertEquals(1, rendered.size());
        assertEquals(List.of("POST /v1/schematics?name=house", "PUT /v1/schematics/s9/versions/1/thumbnail"), cloud.stub().requests());
    }

    @Test
    void an_upload_can_add_a_version_to_an_existing_schematic() throws Exception {
        LocalLibrary.LocalEntry entry = local.add(copy("house.v2.schem", directory.resolve("tower.schem")));
        cloud.stub().answer(201, uploadResult("tower", 4)).answer(204, "");

        UploadResult result = uploader(file -> CompletableFuture.completedFuture(new byte[] {1})).upload(entry, "tower").get(10, TimeUnit.SECONDS);

        assertEquals(4, result.getVersion().getNumber());
        assertEquals("POST /v1/schematics/tower/versions", cloud.stub().requests().getFirst());
    }

    @Test
    void a_failed_upload_keeps_the_local_entry() throws Exception {
        LocalLibrary.LocalEntry entry = local.add(copy("house.nbt", directory.resolve("house.nbt")));
        cloud.stub().problem(422, "over_quota");

        CompletableFuture<UploadResult> upload = uploader(file -> CompletableFuture.completedFuture(new byte[0])).upload(entry, null);

        ExecutionException failure = assertThrows(ExecutionException.class, () -> upload.get(10, TimeUnit.SECONDS));
        assertEquals(Optional.of(ProblemCode.OVER_QUOTA), assertInstanceOf(CloudException.class, failure.getCause()).code());
        assertEquals(1, local.entries().size());
    }

    @Test
    void a_thumbnail_that_cannot_be_rendered_does_not_fail_the_upload() throws Exception {
        LocalLibrary.LocalEntry entry = local.add(copy("house.nbt", directory.resolve("house.nbt")));
        cloud.stub().answer(201, uploadResult("s9", 1));

        UploadResult result = uploader(file -> CompletableFuture.failedFuture(new IllegalStateException("no GPU"))).upload(entry, null).get(10, TimeUnit.SECONDS);

        assertEquals("s9", result.getSchematicId());
        assertTrue(local.entries().isEmpty());
        assertEquals(1, cloud.stub().requests().size());
    }

    private Importer importer() {
        return new Importer(local, () -> {
            List<LibraryEntry> all = new ArrayList<>(libraryEntries);
            local.entries().forEach(entry -> all.add(new LibraryEntry(LibraryEntry.Kind.LOCAL, entry.sha256(), entry.name(), null, List.of(),
                    entry.sha256(), entry.format(), 1, 1, 1, 1, false, 0, entry.importedAt())));
            return all;
        });
    }

    private Uploader uploader(java.util.function.Function<Path, CompletableFuture<byte[]>> thumbnails) {
        CloudAccount account = cloud.account(directory, true);
        return new Uploader(account.client(), local, thumbnails);
    }

    private static Path copy(String fixture, Path target) throws Exception {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("src/conformance/files").resolve(fixture);
            if (!Files.exists(candidate)) {
                candidate = dir.resolve("conformance/files").resolve(fixture);
            }
            if (Files.exists(candidate)) {
                Files.createDirectories(target.getParent());
                return Files.copy(candidate, target);
            }
        }
        throw new IllegalStateException("No fixture " + fixture);
    }

    private static String uploadResult(String schematicId, int version) {
        return "{\"schematicId\":\"" + schematicId + "\",\"version\":{\"number\":" + version + ",\"sha256\":\"ab\",\"format\":\"structure\","
                + "\"sizeBytes\":5,\"dimensions\":{\"width\":1,\"height\":1,\"length\":1},\"dataVersion\":null,\"blockTotal\":1,"
                + "\"blockCounts\":[],\"skippedEntities\":0,\"hasThumbnail\":false,\"createdAt\":\"2026-10-21T10:00:00Z\"}}";
    }
}
