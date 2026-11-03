package io.github.dirnei.mc.heavymatica;

import io.github.dirnei.mc.heavymatica.account.CloudAccess;
import io.github.dirnei.mc.heavymatica.account.CloudAddress;
import io.github.dirnei.mc.heavymatica.placement.OpacitySteps;
import io.github.dirnei.mc.heavymatica.remote.KeyHintRules;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu;
import io.github.dirnei.mc.heavymatica.settings.Setting;
import io.github.dirnei.mc.heavymatica.settings.SettingsSchema;
import io.github.dirnei.mc.heavymatica.targeting.PointerMode;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/** Client config of Heavymatica, written to {@code config/heavymatica-client.toml}. */
public final class HeavymaticaConfig {

    /** Every client setting; the loader binds it to its config file. */
    public static final SettingsSchema SCHEMA = new SettingsSchema();

    private static final Setting<String> CLOUD_URL = SCHEMA
            .comment("Address of the Heavymatica Cloud.")
            .push("cloud")
            .define("cloudUrl", "https://api.heavymatica.com", HeavymaticaConfig::isHttpUrl);

    private static final Setting<String> DEV_SESSION_JOIN_URL = SCHEMA
            .comment("Development only: sign in through the cloud's DevMojang at this address instead of Mojang,",
                    "for example http://localhost:5180/dev/mojang. Leave empty to sign in with your Minecraft account.")
            .define("devSessionJoinUrl", "", value -> value instanceof String text && (text.isEmpty() || isHttpUrl(text)));

    private static final Setting<CloudAccess> CLOUD_ACCESS = SCHEMA
            .comment("Heavymatica Cloud: AUTO asks the cloud whether it is open when you open the library, ON always shows it,",
                    "OFF never contacts it.")
            .defineEnum("access", CloudAccess.AUTO);

    private static final Setting<Integer> CACHE_SIZE_MIB = SCHEMA
            .pop()
            .comment("Size of the local cache of downloaded schematics and thumbnails, in MiB.")
            .push("cache")
            .defineInRange("cacheSizeMiB", 1024, 64, 65_536);

    private static final Setting<String> REMOTE_ITEM = SCHEMA
            .pop()
            .comment("The item that works as the placement remote while held in the main hand, for example minecraft:blaze_rod.",
                    "Items in the item tag heavymatica:remotes always work too.")
            .push("remote")
            .define("item", "minecraft:stick", HeavymaticaConfig::isItemId);

    private static final Setting<Double> GHOST_OPACITY = SCHEMA
            .pop()
            .comment("Opacity of placement ghosts, from 0.05 (barely visible) to 1 (solid).")
            .push("ghost")
            .defineInRange("opacity", 0.4, OpacitySteps.MIN, OpacitySteps.MAX);

    private static final Setting<List<? extends Number>> GHOST_OPACITY_STEPS = SCHEMA
            .comment("Opacities offered on the placement wheel, 2 to 8 values from 0.05 to 1.",
                    "Values out of range and duplicates are dropped; with fewer than 2 or more than 8 left, the default steps are used.")
            .defineList("opacitySteps", OpacitySteps.DEFAULT, () -> 0.5, value -> value instanceof Number);

    private static final Setting<Boolean> GHOST_SHOW_UNKNOWN_BLOCKS = SCHEMA
            .comment("Show blocks the game does not know (from mods that are not installed or newer versions) as missing-texture cubes.")
            .define("showUnknownBlocks", true);

    private static final Setting<Integer> GHOST_BLOCK_BUDGET = SCHEMA
            .comment("Visible ghost blocks drawn at most, across all placements. Placements beyond it show as outlines only.")
            .defineInRange("blockBudget", 300_000, 1_000, 10_000_000);

    private static final Setting<OverlaySide> OVERLAY_SIDE = SCHEMA
            .pop()
            .comment("Screen side of the placement overlay shown while the remote is held.")
            .push("overlay")
            .defineEnum("side", OverlaySide.LEFT);

    private static final Setting<Boolean> OVERLAY_COMPACT = SCHEMA
            .comment("Replaced by keyHints. When true at start, key hints are turned off and this goes back to false.")
            .define("compact", false);

    private static final Setting<Boolean> OVERLAY_KEY_HINTS = SCHEMA
            .comment("Show the key hints in the bottom-right corner while the remote is held.")
            .define("keyHints", true);

    private static final Setting<Boolean> OVERLAY_PINNED = SCHEMA
            .comment("Keep the placement overlay on screen without the remote while a placement is selected.")
            .define("pinned", false);

    private static final Setting<RadialMenu.Mode> WHEEL_MODE = SCHEMA
            .pop()
            .comment("How the placement wheel works. HOLD: hold the wheel key, point and let go. TOGGLE: tap the key to open, click to pick.")
            .push("wheel")
            .defineEnum("mode", RadialMenu.Mode.HOLD);

    private static final Setting<Boolean> WHEEL_ANIMATIONS = SCHEMA
            .comment("Animate the placement wheel as it opens, changes page, highlights and closes. Off shows each state at once.")
            .define("animations", true);

    private static final Setting<Boolean> WHEEL_SOUNDS = SCHEMA
            .comment("Play a quiet tick when the wheel highlights another segment, and a click when a segment is picked.")
            .define("sounds", true);

    private static final Setting<Double> LAYERS_HOTBAR_OPACITY = SCHEMA
            .pop()
            .push("layers")
            .comment("With the in-hand filter, the opacity of blocks matching other hotbar items, as a share of the ghost opacity.")
            .defineInRange("hotbarOpacity", 0.33, 0.05, 1.0);

    private static final Setting<Integer> COMPARISON_CELLS_PER_TICK = SCHEMA
            .pop()
            .push("comparison")
            .comment("Positions of locked placements compared with the world per tick, besides those within reach of the player.",
                    "Higher values notice other players' changes sooner and cost more time per tick.")
            .defineInRange("cellsPerTick", 20_000, 1_000, 1_000_000);

    private static final Setting<Integer> TARGETING_HUD_RANGE = SCHEMA
            .pop()
            .push("targeting")
            .comment("How far away, in blocks, the ghost under the crosshair is named in a small HUD below the crosshair. 0 turns the HUD off.")
            .defineInRange("hudRange", 32, 0, 128);

    private static final Setting<Boolean> TARGETING_PICK_BLOCK = SCHEMA
            .comment("Pick block (middle click) on a ghost within reach picks the item for the planned block from the inventory.",
                    "Sneak while picking, or turn this off, to pick the block behind the ghost as usual.")
            .define("pickBlock", true);

    private static final Setting<PointerMode> TARGETING_MISTAKE_POINTER = SCHEMA
            .comment("Mistake pointer: an arrow below the crosshair toward the nearest position of the selected locked placement that needs attention.",
                    "OFF, MISTAKES (boxed positions only) or MISTAKES_AND_MISSING (also positions still missing their block).")
            .defineEnum("mistakePointer", PointerMode.OFF);

    private static final Setting<Boolean> BUILD_EASY_PLACE = SCHEMA
            .pop()
            .push("build")
            .comment("Easy Place: right-clicking a ghost within reach with its item places the planned block with the planned orientation.")
            .define("easyPlace", false);

    private static final Setting<Boolean> BUILD_PLACEMENT_RESTRICTION = SCHEMA
            .comment("Placement restriction: refuse block placements that would put a wrong block into a locked placement's visible layers.")
            .define("placementRestriction", false);

    public enum OverlaySide {
        LEFT,
        RIGHT,
    }

    static {
        SCHEMA.pop();
    }

    private HeavymaticaConfig() {
    }

    /** Dev runs set a system property that wins over the config, so they never reach the production cloud by accident. */
    public static URI cloudUrl() {
        return CloudAddress.choose(System.getProperty(CloudAddress.PROPERTY), CLOUD_URL.get());
    }

    public static CloudAccess cloudAccess() {
        return CLOUD_ACCESS.get();
    }

    public static void setCloudAccess(CloudAccess access) {
        CLOUD_ACCESS.set(access);
        CLOUD_ACCESS.save();
    }

    public static Optional<URI> devSessionJoinUrl() {
        String value = DEV_SESSION_JOIN_URL.get();
        return value.isEmpty() ? Optional.empty() : Optional.of(URI.create(value));
    }

    public static long cacheSizeBytes() {
        return CACHE_SIZE_MIB.get() * 1024L * 1024L;
    }

    /** Whether such an item exists is checked when it is used. */
    public static String remoteItem() {
        return REMOTE_ITEM.get();
    }

    public static float ghostOpacity() {
        return GHOST_OPACITY.get().floatValue();
    }

    /** Sets the ghost opacity and writes the config file. */
    public static void setGhostOpacity(double opacity) {
        GHOST_OPACITY.set(Math.clamp(opacity, OpacitySteps.MIN, OpacitySteps.MAX));
        GHOST_OPACITY.save();
    }

    public static boolean ghostShowUnknownBlocks() {
        return GHOST_SHOW_UNKNOWN_BLOCKS.get();
    }

    /** Sets whether unknown blocks show and writes the config file. */
    public static void setGhostShowUnknownBlocks(boolean show) {
        GHOST_SHOW_UNKNOWN_BLOCKS.set(show);
        GHOST_SHOW_UNKNOWN_BLOCKS.save();
    }

    public static List<Double> ghostOpacitySteps() {
        return OpacitySteps.sanitize(GHOST_OPACITY_STEPS.get());
    }

    public static int ghostBlockBudget() {
        return GHOST_BLOCK_BUDGET.get();
    }

    public static OverlaySide overlaySide() {
        return OVERLAY_SIDE.get();
    }

    public static boolean overlayKeyHints() {
        return OVERLAY_KEY_HINTS.get();
    }

    /** Carries the former compact overlay over into the key hints setting, once. */
    public static void migrateCompactOverlay() {
        if (!OVERLAY_COMPACT.get()) {
            return;
        }
        OVERLAY_KEY_HINTS.set(KeyHintRules.keyHintsAfterMigration(true, OVERLAY_KEY_HINTS.get()));
        OVERLAY_COMPACT.set(false);
        OVERLAY_COMPACT.save();
    }

    public static boolean overlayPinned() {
        return OVERLAY_PINNED.get();
    }

    /** Pins or unpins the placement overlay and writes the config file. */
    public static void setOverlayPinned(boolean pinned) {
        OVERLAY_PINNED.set(pinned);
        OVERLAY_PINNED.save();
    }

    public static RadialMenu.Mode wheelMode() {
        return WHEEL_MODE.get();
    }

    public static boolean wheelAnimations() {
        return WHEEL_ANIMATIONS.get();
    }

    public static boolean wheelSounds() {
        return WHEEL_SOUNDS.get();
    }

    public static float layersHotbarOpacity() {
        return LAYERS_HOTBAR_OPACITY.get().floatValue();
    }

    public static int comparisonCellsPerTick() {
        return COMPARISON_CELLS_PER_TICK.get();
    }

    public static int targetingHudRange() {
        return TARGETING_HUD_RANGE.get();
    }

    public static boolean targetingPickBlock() {
        return TARGETING_PICK_BLOCK.get();
    }

    public static PointerMode mistakePointer() {
        return TARGETING_MISTAKE_POINTER.get();
    }

    /** Sets the mistake pointer mode and writes the config file. */
    public static void setMistakePointer(PointerMode mode) {
        TARGETING_MISTAKE_POINTER.set(mode);
        TARGETING_MISTAKE_POINTER.save();
    }

    public static boolean easyPlace() {
        return BUILD_EASY_PLACE.get();
    }

    /** Turns Easy Place on or off and writes the config file. */
    public static void setEasyPlace(boolean on) {
        BUILD_EASY_PLACE.set(on);
        BUILD_EASY_PLACE.save();
    }

    public static boolean placementRestriction() {
        return BUILD_PLACEMENT_RESTRICTION.get();
    }

    /** Turns placement restriction on or off and writes the config file. */
    public static void setPlacementRestriction(boolean on) {
        BUILD_PLACEMENT_RESTRICTION.set(on);
        BUILD_PLACEMENT_RESTRICTION.save();
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
