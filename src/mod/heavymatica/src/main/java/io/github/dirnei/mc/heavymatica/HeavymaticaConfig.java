package io.github.dirnei.mc.heavymatica;

import java.net.URI;
import java.util.Optional;
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

    public static final ModConfigSpec SPEC;

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
