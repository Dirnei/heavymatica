package io.github.dirnei.mc.heavymatica;

import java.net.URI;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config of Heavymatica, written to {@code config/heavymatica-client.toml}. */
public final class HeavymaticaConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.ConfigValue<String> CLOUD_URL = BUILDER
            .comment("Address of the Heavymatica Cloud.")
            .push("cloud")
            .define("cloudUrl", "http://localhost:5180", HeavymaticaConfig::isHttpUrl);

    private static final ModConfigSpec.ConfigValue<String> DEV_SESSION_JOIN_URL = BUILDER
            .comment("Development only: sign in through the cloud's DevMojang at this address instead of Mojang,",
                    "for example http://localhost:5180/dev/mojang. Leave empty to sign in with your Minecraft account.")
            .define("devSessionJoinUrl", "", value -> value instanceof String text && (text.isEmpty() || isHttpUrl(text)));

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
            .define("item", "minecraft:stick", HeavymaticaConfig::isItemId);

    private static final ModConfigSpec.DoubleValue GHOST_OPACITY = BUILDER
            .pop()
            .comment("Opacity of placement ghosts, from 0.05 (barely visible) to 1 (solid).")
            .push("ghost")
            .defineInRange("opacity", 0.45, 0.05, 1.0);

    private static final ModConfigSpec.IntValue GHOST_BLOCK_BUDGET = BUILDER
            .comment("Visible ghost blocks drawn at most, across all placements. Placements beyond it show as outlines only.")
            .defineInRange("blockBudget", 300_000, 1_000, 10_000_000);

    private static final ModConfigSpec.EnumValue<OverlaySide> OVERLAY_SIDE = BUILDER
            .pop()
            .comment("Screen side of the placement overlay shown while the remote is held.")
            .push("overlay")
            .defineEnum("side", OverlaySide.LEFT);

    private static final ModConfigSpec.BooleanValue OVERLAY_COMPACT = BUILDER
            .comment("Show only the placement's name and state in the overlay, without details and key hints.")
            .define("compact", false);

    private static final ModConfigSpec.IntValue LAYERS_FOLLOW_OFFSET = BUILDER
            .pop()
            .comment("In follow feet, how many layers above the player's feet still show.")
            .push("layers")
            .defineInRange("followOffset", 1, 0, 8);

    private static final ModConfigSpec.DoubleValue LAYERS_HOTBAR_OPACITY = BUILDER
            .comment("With the in-hand filter, the opacity of blocks matching other hotbar items, as a share of the ghost opacity.")
            .defineInRange("hotbarOpacity", 0.33, 0.05, 1.0);

    public static final ModConfigSpec SPEC;

    public enum OverlaySide {
        LEFT,
        RIGHT,
    }

    static {
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private HeavymaticaConfig() {
    }

    public static URI cloudUrl() {
        return URI.create(CLOUD_URL.get());
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

    public static int ghostBlockBudget() {
        return GHOST_BLOCK_BUDGET.get();
    }

    public static OverlaySide overlaySide() {
        return OVERLAY_SIDE.get();
    }

    public static boolean overlayCompact() {
        return OVERLAY_COMPACT.get();
    }

    public static int layersFollowOffset() {
        return LAYERS_FOLLOW_OFFSET.get();
    }

    public static float layersHotbarOpacity() {
        return LAYERS_HOTBAR_OPACITY.get().floatValue();
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
