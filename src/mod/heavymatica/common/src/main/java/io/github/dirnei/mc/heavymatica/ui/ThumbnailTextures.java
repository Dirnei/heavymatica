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
 * get one rendered on this machine and cached the same way, one render at a time so a library of old entries does not
 * stutter. Missing thumbnails simply stay missing (placeholder); a local render that fails is not tried again until the
 * game restarts.
 */
final class ThumbnailTextures implements AutoCloseable {

    private final Library library;
    private static final Set<String> FAILED = new HashSet<>();
    private static boolean rendering;

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
        if (!requested.contains(entry.sha256()) && load(entry)) {
            requested.add(entry.sha256());
        }
        return Optional.empty();
    }

    /** Starts loading; false when it has to wait for another local render to finish first. */
    private boolean load(LibraryEntry entry) {
        if (entry.cloud()) {
            if (entry.hasThumbnail()) {
                library.thumbnail(entry.id(), entry.version(), entry.sha256())
                        .thenAccept(path -> path.ifPresent(file -> register(entry.sha256(), file)))
                        .exceptionally(error -> null);
            }
            return true;
        }
        Optional<Path> cached = library.cachedThumbnail(entry.sha256());
        if (cached.isPresent()) {
            register(entry.sha256(), cached.get());
            return true;
        }
        if (FAILED.contains(entry.sha256())) {
            return true;
        }
        if (rendering) {
            return false;
        }
        renderLocal(entry);
        return true;
    }

    private void renderLocal(LibraryEntry entry) {
        library.local().find(entry.sha256()).ifPresent(local -> {
            rendering = true;
            try {
                ThumbnailRenderer.renderPng(SchematicReader.read(library.local().file(local)))
                        .whenComplete((png, failure) -> Minecraft.getInstance().execute(() -> {
                            rendering = false;
                            if (failure != null) {
                                FAILED.add(entry.sha256());
                                Heavymatica.LOGGER.debug("No thumbnail for local entry {}", entry.name(), failure);
                            } else {
                                register(entry.sha256(), library.storeThumbnail(entry.sha256(), png));
                            }
                        }));
            } catch (RuntimeException e) {
                rendering = false;
                FAILED.add(entry.sha256());
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
