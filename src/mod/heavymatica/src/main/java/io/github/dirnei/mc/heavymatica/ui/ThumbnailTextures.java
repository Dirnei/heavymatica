package io.github.dirnei.mc.heavymatica.ui;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.formats.SchematicReader;
import io.github.dirnei.mc.heavymatica.library.Library;
import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import io.github.dirnei.mc.heavymatica.thumbnail.ThumbnailRenderer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Thumbnails as GUI textures, keyed by version hash. Cloud thumbnails come through the library's cache; local entries
 * get one rendered on this machine and cached the same way. Missing thumbnails simply stay missing (placeholder).
 */
final class ThumbnailTextures implements AutoCloseable {

    private final Library library;
    private final Map<String, Identifier> loaded = new HashMap<>();
    private final Set<String> requested = new HashSet<>();

    ThumbnailTextures(Library library) {
        this.library = library;
    }

    /** The texture of the entry's thumbnail if it is ready; starts loading it otherwise. */
    Optional<Identifier> get(LibraryEntry entry) {
        Identifier texture = loaded.get(entry.sha256());
        if (texture != null) {
            return Optional.of(texture);
        }
        if (requested.add(entry.sha256())) {
            load(entry);
        }
        return Optional.empty();
    }

    private void load(LibraryEntry entry) {
        if (entry.cloud()) {
            if (entry.hasThumbnail()) {
                library.thumbnail(entry.id(), entry.version(), entry.sha256())
                        .thenAccept(path -> path.ifPresent(file -> register(entry.sha256(), file)))
                        .exceptionally(error -> null);
            }
            return;
        }
        library.cachedThumbnail(entry.sha256()).ifPresentOrElse(file -> register(entry.sha256(), file), () -> renderLocal(entry));
    }

    private void renderLocal(LibraryEntry entry) {
        library.local().find(entry.sha256()).ifPresent(local -> {
            try {
                ThumbnailRenderer.renderPng(SchematicReader.read(library.local().file(local)))
                        .thenAccept(png -> Minecraft.getInstance().execute(() -> register(entry.sha256(), library.storeThumbnail(entry.sha256(), png))))
                        .exceptionally(error -> null);
            } catch (RuntimeException e) {
                Heavymatica.LOGGER.debug("No thumbnail for local entry {}", entry.name(), e);
            }
        });
    }

    private void register(String sha256, Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "thumbnail/" + sha256);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Heavymatica thumbnail " + sha256, image));
            loaded.put(sha256, id);
        } catch (IOException | RuntimeException e) {
            Heavymatica.LOGGER.debug("Unreadable thumbnail {}", file, e);
        }
    }

    @Override
    public void close() {
        loaded.values().forEach(id -> Minecraft.getInstance().getTextureManager().release(id));
        loaded.clear();
        requested.clear();
    }
}
