package io.github.dirnei.mc.heavymatica;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.DevSessionJoin;
import io.github.dirnei.mc.heavymatica.account.GameSessionJoin;
import io.github.dirnei.mc.heavymatica.account.SessionJoin;
import io.github.dirnei.mc.heavymatica.account.SessionStore;
import io.github.dirnei.mc.heavymatica.cache.SchematicCache;
import io.github.dirnei.mc.heavymatica.cloud.client.ApiClient;
import io.github.dirnei.mc.heavymatica.library.Library;
import io.github.dirnei.mc.heavymatica.library.LocalLibrary;
import io.github.dirnei.mc.heavymatica.library.StoredListing;
import io.github.dirnei.mc.heavymatica.thumbnail.ThumbnailSpike;
import io.github.dirnei.mc.heavymatica.ui.DevScreens;
import io.github.dirnei.mc.heavymatica.ui.LibraryScreen;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Client entrypoint: config, the library key and the client services (account, library). */
@Mod(value = Heavymatica.MOD_ID, dist = Dist.CLIENT)
public final class HeavymaticaClient {

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "heavymatica"));

    /** Opens the library; H unless the player changes it. */
    public static final KeyMapping OPEN_LIBRARY = new KeyMapping("key.heavymatica.open_library", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    private static @Nullable CloudAccount account;
    private static @Nullable Library library;

    public HeavymaticaClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, HeavymaticaConfig.SPEC);
        modEventBus.addListener(HeavymaticaClient::registerKeys);
        modEventBus.addListener(HeavymaticaClient::setup);
        NeoForge.EVENT_BUS.addListener(HeavymaticaClient::tick);
        ThumbnailSpike.registerIfRequested();
        DevScreens.registerIfRequested();
    }

    /** The cloud account; available after client setup. */
    public static CloudAccount account() {
        if (account == null) {
            throw new IllegalStateException("Heavymatica's client services are not set up yet.");
        }
        return account;
    }

    /** The schematic library; available after client setup. */
    public static Library library() {
        if (library == null) {
            throw new IllegalStateException("Heavymatica's client services are not set up yet.");
        }
        return library;
    }

    /** Heavymatica's files in the game directory (sessions, library, local, cache, import). */
    public static Path dataDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(Heavymatica.MOD_ID);
    }

    public static LibraryScreen libraryScreen() {
        return new io.github.dirnei.mc.heavymatica.ui.ImportingLibraryScreen(account(), library());
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_LIBRARY);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            ObjectMapper json = ApiClient.createDefaultObjectMapper();
            Path data = dataDirectory();
            SessionJoin join = HeavymaticaConfig.devSessionJoinUrl().<SessionJoin>map(DevSessionJoin::new).orElseGet(GameSessionJoin::new);
            Library[] holder = new Library[1];
            account = new CloudAccount(HeavymaticaConfig.cloudUrl(), minecraft::execute, Util.backgroundExecutor(), HeavymaticaClient::player, join,
                    new SessionStore(data.resolve("sessions"), json), () -> {
                        if (holder[0] != null) {
                            holder[0].signedOut();
                        }
                    });
            library = new Library(account, HeavymaticaClient::playerId, new LocalLibrary(data.resolve("local"), json, Clock.systemUTC()),
                    new StoredListing(data.resolve("library"), json),
                    new SchematicCache(data.resolve("cache"), HeavymaticaConfig.cacheSizeBytes(), Clock.systemUTC(), json),
                    minecraft::execute, Util.backgroundExecutor());
            holder[0] = library;
            account.restore();
            library.loadStored();
        });
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_LIBRARY.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null && library != null) {
                minecraft.setScreen(libraryScreen());
            }
        }
    }

    private static CloudAccount.Player player() {
        User user = Minecraft.getInstance().getUser();
        return new CloudAccount.Player(user.getProfileId(), user.getName());
    }

    private static UUID playerId() {
        return Minecraft.getInstance().getUser().getProfileId();
    }
}
