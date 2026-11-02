package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class UploadChoiceTest {

    @Test
    void a_new_schematic_names_the_entry() {
        assertEquals(LinkCodeText.Text.of("heavymatica.upload.summary.new", "Starter Hut"), UploadChoice.summary("Starter Hut", false, "Tower", 2));
    }

    @Test
    void a_new_version_names_the_target_and_its_next_number() {
        LinkCodeText.Text text = UploadChoice.summary("Starter Hut", true, "Tower", 2);
        assertEquals("heavymatica.upload.summary.version", text.key());
        assertEquals(List.of("Tower", "3"), text.args());
    }

    @Test
    void a_new_version_without_a_target_asks_for_one() {
        assertEquals("heavymatica.upload.summary.pick", UploadChoice.summary("Starter Hut", true, null, 0).key());
    }

    @Test
    void only_the_selected_row_shows_the_next_version() {
        assertEquals("v2", UploadChoice.versionLabel(2, false));
        assertEquals("v2 → v3", UploadChoice.versionLabel(2, true));
    }
}
