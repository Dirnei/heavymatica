package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.formats.SchematicReader;
import io.github.dirnei.mc.heavymatica.library.Library;
import io.github.dirnei.mc.heavymatica.library.LocalLibrary;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * The placements of the world or server the player is in: loaded on join, saved while playing and on leaving,
 * with each pinned version loaded through the library. Versions that cannot be loaded stay as outlines and are tried
 * again when the library changes, for example when the cloud comes back. Game thread only.
 */
public final class PlacementSession {

    private final Placements placements = new Placements();
    private final PlacementController controller = new PlacementController(placements);
    private final PlacementStore store;
    private final Library library;
    private final GhostMeshes meshes = new GhostMeshes();
    private final GhostRenderer renderer;
    private final Map<String, SchematicModel> models = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private final Set<String> unavailable = new HashSet<>();
    private boolean inWorld;

    public PlacementSession(PlacementStore store, Library library, BooleanSupplier remoteHeld) {
        this.store = store;
        this.library = library;
        this.renderer = new GhostRenderer(this, meshes, remoteHeld);
        placements.onChange(changed -> {
            store.changed(changed.all(), System.currentTimeMillis());
            changed.all().forEach(this::load);
        });
        library.onChange(changed -> retryUnavailable());
    }

    public void register() {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> joined());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> left());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentBlocks event) -> renderer.render(event));
    }

    public Placements placements() {
        return placements;
    }

    public PlacementController controller() {
        return controller;
    }

    /** The placement's schematic once loaded; null while loading or unavailable. */
    public @Nullable SchematicModel model(Placement placement) {
        return models.get(placement.source().sha256());
    }

    /** True when the placement's version could not be loaded (offline and not cached, or deleted). */
    public boolean unavailable(Placement placement) {
        return unavailable.contains(placement.source().sha256());
    }

    /** Starts a placement of an already loaded version (Place in the library). */
    public Placement start(PlacementSource source, String name, SchematicModel model, String dimension, PlacementController.Target at) {
        models.put(source.sha256(), model);
        unavailable.remove(source.sha256());
        return controller.start(source, name, model.width(), model.height(), model.length(), dimension, at);
    }

    /** The player's feet y minus the placement's bottom y; 0 outside a world. */
    public static int feetLayer(Placement placement) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0 : Mth.floor(player.getY()) - placement.y();
    }

    /** The block an item places, any state; null for items that place no block. */
    public static @Nullable Block placedBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item ? item.getBlock() : null;
    }

    /** Mesh count, for development checks. */
    public int meshCount() {
        return meshes.size();
    }

    /** What the last frame drew, for development checks. */
    public String lastFrame() {
        return renderer.lastFrame();
    }

    private void joined() {
        Minecraft minecraft = Minecraft.getInstance();
        String key = worldKey(minecraft);
        List<Placement> loaded = store.open(minecraft.getUser().getProfileId(), key);
        inWorld = true;
        Heavymatica.LOGGER.info("Loaded {} placements for {}", loaded.size(), key);
        placements.replaceAll(loaded);
    }

    private void left() {
        if (!inWorld) {
            return;
        }
        inWorld = false;
        store.close();
        placements.replaceAll(List.of());
        models.clear();
        loading.clear();
        unavailable.clear();
        meshes.close();
    }

    private void tick() {
        store.tick(System.currentTimeMillis());
        meshes.sweep(placements.all(), this::model);
    }

    static String worldKey(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return PlacementStore.singleplayerKey(root.getFileName().toString());
        }
        ServerData data = minecraft.getCurrentServer();
        if (data == null) {
            return PlacementStore.multiplayerKey("unknown");
        }
        return data.isRealm() ? PlacementStore.realmsKey(data.name) : PlacementStore.multiplayerKey(data.ip);
    }

    private void load(Placement placement) {
        String sha256 = placement.source().sha256();
        if (models.containsKey(sha256) || loading.contains(sha256) || unavailable.contains(sha256)) {
            return;
        }
        loading.add(sha256);
        file(placement.source())
                .thenApplyAsync(SchematicReader::read, Util.backgroundExecutor())
                .whenCompleteAsync((model, failure) -> {
                    if (!loading.remove(sha256)) {
                        return;
                    }
                    if (failure == null) {
                        models.put(sha256, model);
                    } else {
                        Heavymatica.LOGGER.info("Placement version {} is unavailable: {}", sha256, failure.getMessage());
                        unavailable.add(sha256);
                    }
                }, Minecraft.getInstance());
    }

    private CompletableFuture<Path> file(PlacementSource source) {
        return switch (source) {
            case PlacementSource.Cloud cloud -> library.file(cloud.schematicId(), cloud.version(), cloud.sha256());
            case PlacementSource.Local local -> {
                LocalLibrary locals = library.local();
                Optional<Path> file = locals.find(local.sha256()).map(locals::file);
                yield file.map(CompletableFuture::completedFuture)
                        .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("The local entry was deleted")));
            }
        };
    }

    private void retryUnavailable() {
        if (unavailable.isEmpty() || library.cloudState() == Library.CloudState.LOADING) {
            return;
        }
        unavailable.clear();
        placements.all().forEach(this::load);
    }
}
