package io.github.dirnei.mc.heavymatica;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.DevSessionJoin;
import io.github.dirnei.mc.heavymatica.account.GameSessionJoin;
import io.github.dirnei.mc.heavymatica.account.SessionJoin;
import io.github.dirnei.mc.heavymatica.account.SessionStore;
import io.github.dirnei.mc.heavymatica.cache.SchematicCache;
import io.github.dirnei.mc.heavymatica.capture.CaptureController;
import io.github.dirnei.mc.heavymatica.capture.CaptureRenderer;
import io.github.dirnei.mc.heavymatica.cloud.client.ApiClient;
import io.github.dirnei.mc.heavymatica.library.Library;
import io.github.dirnei.mc.heavymatica.library.LocalLibrary;
import io.github.dirnei.mc.heavymatica.library.StoredListing;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.GhostPipeline;
import io.github.dirnei.mc.heavymatica.placement.PlacementController;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementSource;
import io.github.dirnei.mc.heavymatica.placement.PlacementStore;
import io.github.dirnei.mc.heavymatica.remote.FinishFeedback;
import io.github.dirnei.mc.heavymatica.remote.PlacementOverlay;
import io.github.dirnei.mc.heavymatica.remote.RemoteInput;
import io.github.dirnei.mc.heavymatica.remote.RemoteKeys;
import io.github.dirnei.mc.heavymatica.remote.Remotes;
import io.github.dirnei.mc.heavymatica.targeting.GhostPick;
import io.github.dirnei.mc.heavymatica.targeting.GhostTargeting;
import io.github.dirnei.mc.heavymatica.targeting.TargetHud;
import io.github.dirnei.mc.heavymatica.thumbnail.ThumbnailSpike;
import io.github.dirnei.mc.heavymatica.ui.DevScreens;
import io.github.dirnei.mc.heavymatica.ui.LibraryScreen;
import io.github.dirnei.mc.heavymatica.ui.MaterialsScreen;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

@Mod(value = Heavymatica.MOD_ID, dist = Dist.CLIENT)
public final class HeavymaticaClient {

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "heavymatica"));

    public static final KeyMapping OPEN_LIBRARY = new KeyMapping("key.heavymatica.open_library", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    private static @Nullable CloudAccount account;
    private static @Nullable Library library;
    private static @Nullable PlacementSession placements;
    private static @Nullable GhostTargeting targeting;
    private static @Nullable CaptureController capture;

    public HeavymaticaClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, HeavymaticaConfig.SPEC);
        modEventBus.addListener(HeavymaticaClient::registerKeys);
        modEventBus.addListener(HeavymaticaClient::setup);
        modEventBus.addListener(GhostPipeline::register);
        modEventBus.addListener((RegisterGuiLayersEvent event) -> new PlacementOverlay(HeavymaticaClient::placements).register(event));
        modEventBus.addListener((RegisterGuiLayersEvent event) -> new TargetHud(HeavymaticaClient::placements, HeavymaticaClient::targeting).register(event));
        NeoForge.EVENT_BUS.addListener(HeavymaticaClient::tick);
        ThumbnailSpike.registerIfRequested();
        DevScreens.registerIfRequested();
    }

    /** Available after client setup. */
    public static CloudAccount account() {
        if (account == null) {
            throw new IllegalStateException("Heavymatica's client services are not set up yet.");
        }
        return account;
    }

    /** Available after client setup. */
    public static Library library() {
        if (library == null) {
            throw new IllegalStateException("Heavymatica's client services are not set up yet.");
        }
        return library;
    }

    /** Null before client setup. */
    public static @Nullable PlacementSession placements() {
        return placements;
    }

    /** Capture mode; null before client setup. */
    public static @Nullable CaptureController capture() {
        return capture;
    }

    /** The ghost under the crosshair; null before client setup. */
    public static @Nullable GhostTargeting targeting() {
        return targeting;
    }

    /** Heavymatica's files in the game directory (sessions, library, local, cache, import, placements). */
    public static Path dataDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(Heavymatica.MOD_ID);
    }

    public static LibraryScreen libraryScreen() {
        return new io.github.dirnei.mc.heavymatica.ui.ImportingLibraryScreen(account(), library(), HeavymaticaClient::place);
    }

    /** Library Place: a new FOLLOWING placement at the crosshair (or the player's feet), and a hint when no remote is held. */
    private static void place(PlacementSource source, String name, SchematicModel model) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (placements == null || player == null) {
            return;
        }
        PlacementController.Target at = RemoteInput.crosshairTarget();
        if (at == null) {
            BlockPos feet = player.blockPosition();
            at = new PlacementController.Target(feet.getX(), feet.getY(), feet.getZ());
        }
        placements.start(source, name, model, RemoteInput.dimension(), at);
        if (!Remotes.held()) {
            player.sendOverlayMessage(Component.translatable("heavymatica.place.hold_remote", new ItemStack(Remotes.configuredItem()).getHoverName()));
        }
    }

    private static void openLibrary() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.player != null && library != null) {
            minecraft.setScreen(libraryScreen());
        }
    }

    private static void openMaterials() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && placements != null && placements.placements().selected().isPresent()) {
            minecraft.setScreen(new MaterialsScreen(placements));
        }
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_LIBRARY);
        RemoteKeys.register(event);
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
            placements = new PlacementSession(new PlacementStore(data.resolve("placements"), json), library, Remotes::held);
            placements.register();
            placements.onFinish(FinishFeedback::finished);
            targeting = new GhostTargeting(placements);
            targeting.register();
            new GhostPick(placements, targeting).register();
            capture = new CaptureController(placements.capture(), HeavymaticaClient::library);
            new RemoteInput(placements, HeavymaticaClient::openLibrary, HeavymaticaClient::openMaterials, capture).register();
            new CaptureRenderer(placements.capture()).register();
            account.restore();
            library.loadStored();
        });
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_LIBRARY.consumeClick()) {
            openLibrary();
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
