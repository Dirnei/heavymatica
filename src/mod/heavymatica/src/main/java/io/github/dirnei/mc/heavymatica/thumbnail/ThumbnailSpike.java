package io.github.dirnei.mc.heavymatica.thumbnail;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.formats.SchematicReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development aid: when the {@code heavymatica.thumbnail.spike} system property (set from {@code HEAVYMATICA_THUMBNAIL_SPIKE} by the dev runs) names a schematic file, renders its thumbnail once the
 * game has finished loading and writes it next to the file as {@code <file>.png}. Used to check thumbnail rendering on a
 * real GPU without any UI.
 */
public final class ThumbnailSpike {

    private static boolean done;

    private ThumbnailSpike() {
    }

    public static void registerIfRequested() {
        String file = System.getProperty("heavymatica.thumbnail.spike");
        if (file != null && !file.isBlank()) {
            Heavymatica.LOGGER.info("Thumbnail spike armed for {}", file);
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Path.of(file)));
        }
    }

    private static void tick(Path file) {
        Minecraft minecraft = Minecraft.getInstance();
        // Any screen once loading is over: dev runs may open another screen than the title screen first.
        if (done || minecraft.getOverlay() != null || minecraft.screen == null) {
            return;
        }
        done = true;
        Heavymatica.LOGGER.info("Thumbnail spike rendering on {}", minecraft.screen.getClass().getSimpleName());
        Path output = file.resolveSibling(file.getFileName() + ".png");
        long started = System.nanoTime();
        ThumbnailRenderer.renderPng(SchematicReader.read(file)).whenComplete((png, failure) -> {
            if (failure != null) {
                Heavymatica.LOGGER.error("Thumbnail spike failed for {}", file, failure);
                return;
            }
            try {
                Files.write(output, png);
                Heavymatica.LOGGER.info("Thumbnail spike wrote {} ({} bytes) in {} ms", output, png.length, (System.nanoTime() - started) / 1_000_000);
            } catch (IOException e) {
                Heavymatica.LOGGER.error("Thumbnail spike could not write {}", output, e);
            }
        });
    }
}
