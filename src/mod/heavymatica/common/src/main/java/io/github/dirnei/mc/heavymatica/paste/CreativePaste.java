package io.github.dirnei.mc.heavymatica.paste;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.GhostView;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform;
import io.github.dirnei.mc.heavymatica.platform.Platform;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Pastes placements into the player's own single-player world while in creative mode, one job at a time, and keeps
 * what the last paste changed until it is undone or the player leaves the world. Plans are built on the client
 * thread; jobs run on the integrated server's tick, which fires in this process. Undoing a paste also puts back the
 * placement the paste finished.
 */
public final class CreativePaste {

    private static final long BUDGET_NANOS = 10_000_000L;

    /** A running job's progress for the overlay. */
    public record Progress(boolean undo, double share) {
    }

    private final PlacementSession session;
    private final AtomicReference<@Nullable PasteJob> running = new AtomicReference<>();
    private volatile @Nullable PasteRecord last;
    /** The record a running paste replaced, back in place if the server refuses the paste. */
    private volatile @Nullable PasteRecord replaced;

    public CreativePaste(PlacementSession session) {
        this.session = session;
    }

    public void register() {
        session.onFinish((placement, cantCheck) -> {
            PasteRecord record = last;
            if (record != null) {
                record.finish().finished(placement, System.currentTimeMillis());
            }
        });
        Platform.get().serverEvents().onTickEnd(this::serverTick);
        Platform.get().serverEvents().onStopping(this::stop);
        Platform.get().clientEvents().onLogout(this::stop);
        Platform.get().clientEvents().onTickEnd(this::clientTick);
    }

    /** True while the player is in creative mode in a world whose server runs in this game, also when opened to LAN. */
    public static boolean available() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.hasSingleplayerServer() && minecraft.player != null && minecraft.player.isCreative();
    }

    /** True while a paste or undo runs. */
    public boolean running() {
        return running.get() != null;
    }

    /** True while the last paste can be undone, or is still running. */
    public boolean undoable() {
        return last != null;
    }

    public Optional<Progress> progress() {
        PasteJob job = running.get();
        return job == null ? Optional.empty() : Optional.of(new Progress(job.kind() == PasteJob.Kind.UNDO, job.progress()));
    }

    /** Starts pasting an anchored or locked placement; does nothing when paste is not available or a job runs. */
    public void paste(Placement placement, SchematicModel model, PasteMode mode) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        LocalPlayer player = Minecraft.getInstance().player;
        if (!available() || server == null || player == null || placement.following() || running()) {
            return;
        }
        PlacementTransform.Bounds bounds = PlacementTransform.bounds(placement);
        GhostView view = GhostView.of(model, placement);
        PastePlan<BlockState> plan = PastePlan.build(new PastePlan.View<>() {
            @Override
            public int sizeX() {
                return view.sizeX();
            }

            @Override
            public int sizeY() {
                return view.sizeY();
            }

            @Override
            public int sizeZ() {
                return view.sizeZ();
            }

            @Override
            public int paletteIndex(int x, int y, int z, int[] scratch) {
                return view.paletteIndex(x, y, z, scratch);
            }

            @Override
            public String entry(int paletteIndex) {
                return view.entry(paletteIndex);
            }

            @Override
            public @Nullable BlockState placed(int paletteIndex) {
                BlockState placed = view.placed(paletteIndex);
                return PastePlan.unknown(view.entry(paletteIndex), placed.isAir()) ? null : placed;
            }
        }, model, placement.rotation(), placement.mirror(), bounds.minX(), bounds.minY(), bounds.minZ());
        PasteRecord record = new PasteRecord(ResourceKey.create(Registries.DIMENSION, Identifier.parse(placement.dimension())),
                new PasteFinish(placement.id()));
        if (running.compareAndSet(null, PasteJob.paste(server, player.getUUID(), plan, mode, record))) {
            replaced = last;
            last = record;
            Heavymatica.LOGGER.info("Pasting {} ({} positions, {})", placement.name(), plan.total(), mode);
        }
    }

    /** Starts undoing the last paste; does nothing when there is none, paste is not available or a job runs. */
    public void undo() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        LocalPlayer player = Minecraft.getInstance().player;
        PasteRecord record = last;
        if (!available() || server == null || player == null || record == null) {
            return;
        }
        running.compareAndSet(null, PasteJob.undo(server, player.getUUID(), record));
    }

    private void serverTick(MinecraftServer server) {
        PasteJob job = running.get();
        if (job != null && job.server() == server) {
            job.tick(BUDGET_NANOS);
        }
    }

    private void clientTick() {
        PasteJob job = running.get();
        if (job == null || job.outcome() == PasteJob.Outcome.RUNNING || !running.compareAndSet(job, null)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        PasteRecord before = replaced;
        replaced = null;
        switch (job.outcome()) {
            case DONE -> {
                if (job.kind() == PasteJob.Kind.UNDO) {
                    if (last == job.record()) {
                        last = null;
                    }
                    job.record().finish().finished().ifPresent(placement -> {
                        if (session.restoreFinished(placement)) {
                            Heavymatica.LOGGER.info("Undo paste put back placement {}", placement.name());
                        }
                    });
                    message(player, Component.translatable("heavymatica.paste.undone"));
                } else {
                    job.record().finish().pasteDone(System.currentTimeMillis());
                    message(player, Component.translatable("heavymatica.paste.done", job.record().size()));
                }
            }
            case REFUSED -> {
                if (job.kind() == PasteJob.Kind.PASTE && last == job.record()) {
                    last = before;
                }
                message(player, Component.translatable("heavymatica.paste.unavailable"));
            }
            case CANCELLED, RUNNING -> {
            }
        }
        Heavymatica.LOGGER.info("{} {}: {} positions changed", job.kind(), job.outcome(), job.record().size());
    }

    /** Leaving the world: a running job stops after its current section and the last paste is forgotten. */
    private void stop() {
        PasteJob job = running.getAndSet(null);
        if (job != null) {
            job.cancel();
        }
        last = null;
        replaced = null;
    }

    private static void message(@Nullable LocalPlayer player, Component text) {
        if (player != null) {
            player.sendOverlayMessage(text);
        }
    }
}
