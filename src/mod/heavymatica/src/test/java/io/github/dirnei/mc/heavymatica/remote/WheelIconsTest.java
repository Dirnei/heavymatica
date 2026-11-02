package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class WheelIconsTest {

    private static final String ROOT = "assets/heavymatica/textures/gui/sprites/";

    @Test
    void unknownBlocksHasAShownAndAHiddenSprite() {
        assertEquals("wheel/unknown_blocks_shown", WheelIcons.sprite(Entry.UNKNOWN_BLOCKS, true));
        assertEquals("wheel/unknown_blocks_hidden", WheelIcons.sprite(Entry.UNKNOWN_BLOCKS, false));
        assertEquals("wheel/lock", WheelIcons.sprite(Entry.LOCK, true));
    }

    @Test
    void everyEntryHasASixteenPixelSprite() throws IOException {
        for (String sprite : used()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(ROOT + sprite + ".png")) {
                assertNotNull(in, "missing sprite " + sprite);
                BufferedImage image = ImageIO.read(in);
                assertEquals(16, image.getWidth(), sprite);
                assertEquals(16, image.getHeight(), sprite);
            }
        }
    }

    @Test
    void noSpriteGoesUnused() throws IOException, URISyntaxException {
        URL folder = getClass().getClassLoader().getResource(ROOT + WheelIcons.FOLDER);
        assertNotNull(folder);
        Set<String> files;
        try (Stream<Path> list = Files.list(Path.of(folder.toURI()))) {
            files = list.map(path -> WheelIcons.FOLDER + path.getFileName().toString().replace(".png", ""))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals(used(), files);
    }

    private static Set<String> used() {
        Set<String> sprites = new TreeSet<>();
        for (Entry entry : Entry.values()) {
            sprites.add(WheelIcons.sprite(entry, true));
            sprites.add(WheelIcons.sprite(entry, false));
        }
        return sprites;
    }
}
