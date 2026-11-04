package io.github.dirnei.mc.heavymatica;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.CloudAvailability;
import io.github.dirnei.mc.heavymatica.account.DevSessionJoin;
import io.github.dirnei.mc.heavymatica.account.GameSessionJoin;
import io.github.dirnei.mc.heavymatica.account.SessionJoin;
import io.github.dirnei.mc.heavymatica.account.SessionStore;
import io.github.dirnei.mc.heavymatica.build.BuildOptionsHud;
import io.github.dirnei.mc.heavymatica.build.BuildInput;
import io.github.dirnei.mc.heavymatica.build.BuildKeys;
import io.github.dirnei.mc.heavymatica.cache.SchematicCache;
import io.github.dirnei.mc.heavymatica.capture.CaptureController;
import io.github.dirnei.mc.heavymatica.capture.CaptureRenderer;
import io.github.dirnei.mc.heavymatica.cloud.client.ApiClient;
import io.github.dirnei.mc.heavymatica.library.Library;
import io.github.dirnei.mc.heavymatica.library.LocalLibrary;
import io.github.dirnei.mc.heavymatica.library.StoredListing;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.paste.CreativePaste;
import io.github.dirnei.mc.heavymatica.placement.GhostPipeline;
import io.github.dirnei.mc.heavymatica.platform.KeyContext;
import io.github.dirnei.mc.heavymatica.platform.KeyRegistry;
import io.github.dirnei.mc.heavymatica.platform.Platform;
import io.github.dirnei.mc.heavymatica.placement.PlacementController;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementSource;
import io.github.dirnei.mc.heavymatica.placement.PlacementStore;
import io.github.dirnei.mc.heavymatica.remote.FinishFeedback;
import io.github.dirnei.mc.heavymatica.remote.KeyHints;
import io.github.dirnei.mc.heavymatica.remote.PlacementOverlay;
import io.github.dirnei.mc.heavymatica.remote.RemoteInput;
import io.github.dirnei.mc.heavymatica.remote.RemoteKeys;
import io.github.dirnei.mc.heavymatica.remote.Remotes;
import io.github.dirnei.mc.heavymatica.remote.WheelAfterglow;
import io.github.dirnei.mc.heavymatica.targeting.GhostPick;
import io.github.dirnei.mc.heavymatica.targeting.GhostTargeting;
import io.github.dirnei.mc.heavymatica.targeting.MistakePointer;
import io.github.dirnei.mc.heavymatica.targeting.PointerHud;
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
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Heavymatica's client: its services, keys, HUD layers and events. The loader's client entry point drives it. */
public final class HeavymaticaClient {

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "heavymatica"));

    public static final KeyMapping OPEN_LIBRARY = Platform.get().keys().create("key.heavymatica.open_library", KeyContext.IN_GAME,
            GLFW.GLFW_KEY_H, CATEGORY);

    private static @Nullable CloudAccount account;
    private static @Nullable CloudAvailability availability;
    private static String modVersion = "0";
    private static @Nullable Library library;
    private static @Nullable PlacementSession placements;
    private static @Nullable GhostTargeting targeting;
    private static @Nullable MistakePointer mistakePointer;
    private static @Nullable CaptureController capture;
    private static @Nullable CreativePaste paste;

    private HeavymaticaClient() {
    }

    /** Registers the key category and every key mapping. */
    public static void registerKeys(KeyRegistry keys) {
        keys.registerCategory(CATEGORY);
        keys.register(OPEN_LIBRARY);
        RemoteKeys.register(keys);
        BuildKeys.register(keys);
    }

    /** Registers keys, HUD layers, render pipelines and events. The loader calls this while it constructs the mod. */
    public static void construct(String version) {
        modVersion = version;
        registerKeys(Platform.get().keys());
        GhostPipeline.register();
        new PlacementOverlay(HeavymaticaClient::placements).register();
        new KeyHints(HeavymaticaClient::placements).register();
        new TargetHud(HeavymaticaClient::placements, HeavymaticaClient::targeting).register();
        new PointerHud(HeavymaticaClient::mistakePointer).register();
        new BuildOptionsHud(HeavymaticaClient::placements).register();
        WheelAfterglow.register();
        Platform.get().clientEvents().onTickEnd(HeavymaticaClient::tick);
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
    public static CloudAvailability availability() {
        if (availability == null) {
            throw new IllegalStateException("Heavymatica's client services are not set up yet.");
        }
        return availability;
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

    /** Creative paste; null before client setup. */
    public static @Nullable CreativePaste paste() {
        return paste;
    }

    /** The ghost under the crosshair; null before client setup. */
    public static @Nullable GhostTargeting targeting() {
        return targeting;
    }

    /** The mistake pointer's candidate; null before client setup. */
    public static @Nullable MistakePointer mistakePointer() {
        return mistakePointer;
    }

    /** Heavymatica's files in the game directory (sessions, library, local, cache, import, placements). */
    public static Path dataDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(Heavymatica.MOD_ID);
    }

    public static LibraryScreen libraryScreen() {
        return new io.github.dirnei.mc.heavymatica.ui.ImportingLibraryScreen(account(), availability(), library(), HeavymaticaClient::place);
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

    /** Starts the client services. The loader calls this on the game thread once the game is set up. */
    public static void setup() {
        HeavymaticaConfig.migrateCompactOverlay();
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
        CloudAvailability cloud = new CloudAvailability(account::fetchStatus, modVersion, HeavymaticaConfig::cloudAccess, minecraft::execute,
                CloudAvailability.TIMEOUT);
        availability = cloud;
        account.contactWhen(() -> cloud.mayContact() && !cloud.tooOld());
        library = new Library(account, cloud, HeavymaticaClient::playerId, new LocalLibrary(data.resolve("local"), json, Clock.systemUTC()),
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
        mistakePointer = new MistakePointer(placements);
        mistakePointer.register();
        capture = new CaptureController(placements.capture(), HeavymaticaClient::library);
        paste = new CreativePaste(placements);
        paste.register();
        new RemoteInput(placements, HeavymaticaClient::openLibrary, HeavymaticaClient::openMaterials, capture, paste).register();
        new BuildInput(placements, targeting).register();
        new CaptureRenderer(placements.capture()).register();
        account.restore();
        library.loadStored();
    }

    private static void tick() {
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
