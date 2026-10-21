package io.github.dirnei.mc.heavymatica.importing;

import io.github.dirnei.mc.heavymatica.account.CloudClient;
import io.github.dirnei.mc.heavymatica.cloud.model.UploadResult;
import io.github.dirnei.mc.heavymatica.library.LocalLibrary;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * Uploads local entries to the cloud as a new schematic or as a new version of one. Only a successful upload turns the
 * entry into a cloud schematic; a failed one keeps it local. A thumbnail is rendered and attached afterwards; when that
 * fails the upload still counts.
 */
public final class Uploader {

    private final CloudClient client;
    private final LocalLibrary local;
    private final Function<Path, CompletableFuture<byte[]>> thumbnails;

    /** @param thumbnails renders a schematic file's thumbnail as PNG */
    public Uploader(CloudClient client, LocalLibrary local, Function<Path, CompletableFuture<byte[]>> thumbnails) {
        this.client = client;
        this.local = local;
        this.thumbnails = thumbnails;
    }

    /**
     * @param schematicId the cloud schematic to add a version to, or null for a new schematic named after the entry
     */
    public CompletableFuture<UploadResult> upload(LocalLibrary.LocalEntry entry, @Nullable String schematicId) {
        Path file = local.file(entry);
        CompletableFuture<UploadResult> upload = schematicId == null
                ? client.uploadSchematic(entry.name(), file)
                : client.uploadVersion(schematicId, file);
        return upload.thenCompose(result -> attachThumbnail(file, result).thenApply(ignored -> {
            local.remove(entry.sha256());
            return result;
        }));
    }

    private CompletableFuture<Void> attachThumbnail(Path file, UploadResult result) {
        if (result.getVersion().getHasThumbnail()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<byte[]> png;
        try {
            png = thumbnails.apply(file);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(null);
        }
        return png.thenCompose(bytes -> client.attachThumbnail(result.getSchematicId(), result.getVersion().getNumber(), bytes))
                .exceptionally(ignored -> null);
    }
}
