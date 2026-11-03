package io.github.dirnei.mc.heavymatica.ui;

import java.util.List;
import java.util.Set;

/** Which client setting shows in which section of the settings screen, and with which control. */
final class SettingsTable {

    enum Section {
        REMOTE,
        GHOSTS,
        BUILDING,
        HUD,
        LIBRARY,
        PERFORMANCE;

        String key() {
            return "heavymatica.settings.section." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    enum Control {
        TOGGLE,
        CHOICE,
        /** A share from 0 to 1, shown in percent. */
        PERCENT,
        /** A whole number on a slider. */
        SLIDER,
        /** A whole number typed in. */
        NUMBER,
        ITEM,
        STEPS,
    }

    /**
     * @param path the config path, such as {@code ghost.opacity}
     * @param min  the smallest value a number or slider takes
     * @param max  the largest value
     */
    record Setting(Section section, String path, Control control, double min, double max) {

        String nameKey() {
            return "heavymatica.settings." + path + ".name";
        }

        String hintKey() {
            return "heavymatica.settings." + path + ".hint";
        }
    }

    /** Config values that stay in the config file only. */
    static final Set<String> FILE_ONLY = Set.of("cloud.cloudUrl", "cloud.devSessionJoinUrl");

    static final List<Setting> ALL = List.of(
            new Setting(Section.REMOTE, "remote.item", Control.ITEM, 0, 0),
            new Setting(Section.REMOTE, "wheel.mode", Control.CHOICE, 0, 0),
            new Setting(Section.REMOTE, "wheel.animations", Control.TOGGLE, 0, 0),
            new Setting(Section.REMOTE, "wheel.sounds", Control.TOGGLE, 0, 0),
            new Setting(Section.GHOSTS, "ghost.opacity", Control.PERCENT, 0.05, 1),
            new Setting(Section.GHOSTS, "ghost.opacitySteps", Control.STEPS, 0.05, 1),
            new Setting(Section.GHOSTS, "layers.hotbarOpacity", Control.PERCENT, 0.05, 1),
            new Setting(Section.GHOSTS, "ghost.showUnknownBlocks", Control.TOGGLE, 0, 0),
            new Setting(Section.BUILDING, "build.easyPlace", Control.TOGGLE, 0, 0),
            new Setting(Section.BUILDING, "build.placementRestriction", Control.TOGGLE, 0, 0),
            new Setting(Section.BUILDING, "targeting.pickBlock", Control.TOGGLE, 0, 0),
            new Setting(Section.BUILDING, "targeting.mistakePointer", Control.CHOICE, 0, 0),
            new Setting(Section.HUD, "overlay.side", Control.CHOICE, 0, 0),
            new Setting(Section.HUD, "overlay.compact", Control.TOGGLE, 0, 0),
            new Setting(Section.HUD, "overlay.pinned", Control.TOGGLE, 0, 0),
            new Setting(Section.HUD, "targeting.hudRange", Control.SLIDER, 0, 128),
            new Setting(Section.LIBRARY, "cloud.access", Control.CHOICE, 0, 0),
            new Setting(Section.LIBRARY, "cache.cacheSizeMiB", Control.NUMBER, 64, 65_536),
            new Setting(Section.PERFORMANCE, "ghost.blockBudget", Control.NUMBER, 1_000, 10_000_000),
            new Setting(Section.PERFORMANCE, "comparison.cellsPerTick", Control.NUMBER, 1_000, 1_000_000));

    private SettingsTable() {
    }

    static List<Setting> of(Section section) {
        return ALL.stream().filter(setting -> setting.section() == section).toList();
    }

    /** Whether a typed whole number may be set: inside the setting's range. */
    static boolean accepts(Setting setting, long value) {
        return value >= setting.min() && value <= setting.max();
    }
}
