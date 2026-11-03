package io.github.dirnei.mc.heavymatica.neoforge;

import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.account.CloudAccess;
import io.github.dirnei.mc.heavymatica.placement.OpacitySteps;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu;
import io.github.dirnei.mc.heavymatica.targeting.PointerMode;
import java.net.URI;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client config spec exactly as it was written by hand before the settings schema existed. Frozen: the schema
 * must keep producing the same file, so this copy is never edited.
 */
@SuppressWarnings("unused")
final class LegacyClientSpec {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.ConfigValue<String> CLOUD_URL = BUILDER
            .comment("Address of the Heavymatica Cloud.")
            .push("cloud")
            .define("cloudUrl", "https://api.heavymatica.com", LegacyClientSpec::isHttpUrl);

    private static final ModConfigSpec.ConfigValue<String> DEV_SESSION_JOIN_URL = BUILDER
            .comment("Development only: sign in through the cloud's DevMojang at this address instead of Mojang,",
                    "for example http://localhost:5180/dev/mojang. Leave empty to sign in with your Minecraft account.")
            .define("devSessionJoinUrl", "", value -> value instanceof String text && (text.isEmpty() || isHttpUrl(text)));

    private static final ModConfigSpec.EnumValue<CloudAccess> CLOUD_ACCESS = BUILDER
            .comment("Heavymatica Cloud: AUTO asks the cloud whether it is open when you open the library, ON always shows it,",
                    "OFF never contacts it.")
            .defineEnum("access", CloudAccess.AUTO);

    private static final ModConfigSpec.IntValue CACHE_SIZE_MIB = BUILDER
            .pop()
            .comment("Size of the local cache of downloaded schematics and thumbnails, in MiB.")
            .push("cache")
            .defineInRange("cacheSizeMiB", 1024, 64, 65_536);

    private static final ModConfigSpec.ConfigValue<String> REMOTE_ITEM = BUILDER
            .pop()
            .comment("The item that works as the placement remote while held in the main hand, for example minecraft:blaze_rod.",
                    "Items in the item tag heavymatica:remotes always work too.")
            .push("remote")
            .define("item", "minecraft:stick", LegacyClientSpec::isItemId);

    private static final ModConfigSpec.DoubleValue GHOST_OPACITY = BUILDER
            .pop()
            .comment("Opacity of placement ghosts, from 0.05 (barely visible) to 1 (solid).")
            .push("ghost")
            .defineInRange("opacity", 0.4, OpacitySteps.MIN, OpacitySteps.MAX);

    private static final ModConfigSpec.ConfigValue<List<? extends Number>> GHOST_OPACITY_STEPS = BUILDER
            .comment("Opacities offered on the placement wheel, 2 to 8 values from 0.05 to 1.",
                    "Values out of range and duplicates are dropped; with fewer than 2 or more than 8 left, the default steps are used.")
            .defineList("opacitySteps", OpacitySteps.DEFAULT, () -> 0.5, value -> value instanceof Number);

    private static final ModConfigSpec.BooleanValue GHOST_SHOW_UNKNOWN_BLOCKS = BUILDER
            .comment("Show blocks the game does not know (from mods that are not installed or newer versions) as missing-texture cubes.")
            .define("showUnknownBlocks", true);

    private static final ModConfigSpec.IntValue GHOST_BLOCK_BUDGET = BUILDER
            .comment("Visible ghost blocks drawn at most, across all placements. Placements beyond it show as outlines only.")
            .defineInRange("blockBudget", 300_000, 1_000, 10_000_000);

    private static final ModConfigSpec.EnumValue<HeavymaticaConfig.OverlaySide> OVERLAY_SIDE = BUILDER
            .pop()
            .comment("Screen side of the placement overlay shown while the remote is held.")
            .push("overlay")
            .defineEnum("side", HeavymaticaConfig.OverlaySide.LEFT);

    private static final ModConfigSpec.BooleanValue OVERLAY_COMPACT = BUILDER
            .comment("Replaced by keyHints. When true at start, key hints are turned off and this goes back to false.")
            .define("compact", false);

    private static final ModConfigSpec.BooleanValue OVERLAY_KEY_HINTS = BUILDER
            .comment("Show the key hints in the bottom-right corner while the remote is held.")
            .define("keyHints", true);

    private static final ModConfigSpec.BooleanValue OVERLAY_PINNED = BUILDER
            .comment("Keep the placement overlay on screen without the remote while a placement is selected.")
            .define("pinned", false);

    private static final ModConfigSpec.EnumValue<RadialMenu.Mode> WHEEL_MODE = BUILDER
            .pop()
            .comment("How the placement wheel works. HOLD: hold the wheel key, point and let go. TOGGLE: tap the key to open, click to pick.")
            .push("wheel")
            .defineEnum("mode", RadialMenu.Mode.HOLD);

    private static final ModConfigSpec.BooleanValue WHEEL_ANIMATIONS = BUILDER
            .comment("Animate the placement wheel as it opens, changes page, highlights and closes. Off shows each state at once.")
            .define("animations", true);

    private static final ModConfigSpec.BooleanValue WHEEL_SOUNDS = BUILDER
            .comment("Play a quiet tick when the wheel highlights another segment, and a click when a segment is picked.")
            .define("sounds", true);

    private static final ModConfigSpec.DoubleValue LAYERS_HOTBAR_OPACITY = BUILDER
            .pop()
            .push("layers")
            .comment("With the in-hand filter, the opacity of blocks matching other hotbar items, as a share of the ghost opacity.")
            .defineInRange("hotbarOpacity", 0.33, 0.05, 1.0);

    private static final ModConfigSpec.IntValue COMPARISON_CELLS_PER_TICK = BUILDER
            .pop()
            .push("comparison")
            .comment("Positions of locked placements compared with the world per tick, besides those within reach of the player.",
                    "Higher values notice other players' changes sooner and cost more time per tick.")
            .defineInRange("cellsPerTick", 20_000, 1_000, 1_000_000);

    private static final ModConfigSpec.IntValue TARGETING_HUD_RANGE = BUILDER
            .pop()
            .push("targeting")
            .comment("How far away, in blocks, the ghost under the crosshair is named in a small HUD below the crosshair. 0 turns the HUD off.")
            .defineInRange("hudRange", 32, 0, 128);

    private static final ModConfigSpec.BooleanValue TARGETING_PICK_BLOCK = BUILDER
            .comment("Pick block (middle click) on a ghost within reach picks the item for the planned block from the inventory.",
                    "Sneak while picking, or turn this off, to pick the block behind the ghost as usual.")
            .define("pickBlock", true);

    private static final ModConfigSpec.EnumValue<PointerMode> TARGETING_MISTAKE_POINTER = BUILDER
            .comment("Mistake pointer: an arrow below the crosshair toward the nearest position of the selected locked placement that needs attention.",
                    "OFF, MISTAKES (boxed positions only) or MISTAKES_AND_MISSING (also positions still missing their block).")
            .defineEnum("mistakePointer", PointerMode.OFF);

    private static final ModConfigSpec.BooleanValue BUILD_EASY_PLACE = BUILDER
            .pop()
            .push("build")
            .comment("Easy Place: right-clicking a ghost within reach with its item places the planned block with the planned orientation.")
            .define("easyPlace", false);

    private static final ModConfigSpec.BooleanValue BUILD_PLACEMENT_RESTRICTION = BUILDER
            .comment("Placement restriction: refuse block placements that would put a wrong block into a locked placement's visible layers.")
            .define("placementRestriction", false);

    static final ModConfigSpec SPEC;

    static {
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private LegacyClientSpec() {
    }

    private static boolean isItemId(Object value) {
        return value instanceof String text && Identifier.tryParse(text) != null;
    }

    private static boolean isHttpUrl(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        try {
            String scheme = URI.create(text).getScheme();
            return "http".equals(scheme) || "https".equals(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
