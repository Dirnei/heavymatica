package io.github.dirnei.mc.heavymatica.capture;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.formats.LitematicaWriter;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.formats.SchematicRejectedException;
import io.github.dirnei.mc.heavymatica.library.Library;
import io.github.dirnei.mc.heavymatica.library.LocalLibrary;
import io.github.dirnei.mc.heavymatica.placement.Placements;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Relative;
import io.github.dirnei.mc.heavymatica.ui.TextPromptScreen;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Runs capture mode in the game: the remote's input on the box, the checks and name prompt, and the capture itself. */
public final class CaptureController {

    /** Cells a capture reads per tick. */
    static final long BUDGET = 262_144;
    private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CaptureSession session;
    private final Supplier<Library> library;
    private @Nullable CaptureSnapshot snapshot;
    private @Nullable String name;
    private int generation;

    public CaptureController(CaptureSession session, Supplier<Library> library) {
        this.session = session;
        this.library = library;
    }

    public CaptureSession session() {
        return session;
    }

    public void start() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            cancelRunning();
            session.start(level.dimension().identifier().toString(), level.getMinY(), level.getMaxY());
        }
    }

    public void cancel() {
        cancelRunning();
        session.end();
    }

    public void reset() {
        if (!session.capturing()) {
            session.box().ifPresent(CaptureBox::reset);
        }
    }

    public void swap() {
        box().ifPresent(CaptureBox::swap);
    }

    public void move(Relative direction, double yaw, boolean shift) {
        box().ifPresent(box -> box.move(direction, yaw, shift));
    }

    public void vertical(int direction, boolean shift) {
        box().ifPresent(box -> box.vertical(direction, shift));
    }

    /** A right-click: the block under the crosshair within the remote's reach. */
    public void click() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        CaptureBox box = box().orElse(null);
        if (player == null || minecraft.level == null || box == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(Placements.REACH));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            box.click(hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ());
        }
    }

    /** Capture on the wheel: checks the box, then asks for a name and starts reading the world. */
    public void capture() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        CaptureBox box = box().orElse(null);
        if (player == null || minecraft.level == null || box == null || box.bounds().isEmpty()) {
            return;
        }
        CaptureBox.Bounds bounds = box.bounds().get();
        if (!bounds.withinLimits()) {
            player.sendOverlayMessage(Component.translatable("heavymatica.capture.too_large"));
            return;
        }
        if (!CaptureSnapshot.loaded(bounds, new ClientCaptureWorld(minecraft.level, null))) {
            player.sendOverlayMessage(Component.translatable("heavymatica.capture.not_loaded"));
            return;
        }
        Component warning = minecraft.getSingleplayerServer() == null ? Component.translatable("heavymatica.capture.server_warning") : null;
        minecraft.setScreen(new TextPromptScreen(null, Component.translatable("heavymatica.capture.name.title"),
                Component.translatable("heavymatica.capture.name.hint"), "Capture " + NAME_TIME.format(LocalDateTime.now()), warning,
                chosen -> begin(chosen.isBlank() ? "Capture " + NAME_TIME.format(LocalDateTime.now()) : chosen.trim()), () -> { }));
    }

    private void begin(String chosen) {
        Minecraft minecraft = Minecraft.getInstance();
        CaptureBox.Bounds bounds = box().flatMap(CaptureBox::bounds).orElse(null);
        if (minecraft.level == null || bounds == null) {
            return;
        }
        name = chosen;
        snapshot = new CaptureSnapshot(bounds, new ClientCaptureWorld(minecraft.level, minecraft.getSingleplayerServer()),
                SharedConstants.getCurrentVersion().dataVersion().version());
        session.setProgress(new CaptureSession.Progress(0, false));
    }

    /** Called every client tick: reads the next part of a running capture. */
    public void tick() {
        CaptureSnapshot running = snapshot;
        if (running == null) {
            return;
        }
        if (!session.on()) {
            snapshot = null;
            return;
        }
        CaptureSnapshot.Outcome outcome = running.step(BUDGET);
        switch (outcome) {
            case RUNNING -> session.setProgress(new CaptureSession.Progress(running.progress(), false));
            case UNLOADED, FAILED -> fail(outcome == CaptureSnapshot.Outcome.UNLOADED ? "heavymatica.capture.unloaded" : "heavymatica.capture.failed");
            case DONE -> save(running.model());
        }
    }

    private void save(SchematicModel model) {
        snapshot = null;
        session.setProgress(new CaptureSession.Progress(1, true));
        String entryName = name == null ? "Capture" : name;
        int token = generation;
        Minecraft minecraft = Minecraft.getInstance();
        String author = minecraft.getUser().getName();
        CompletableFuture.supplyAsync(() -> library.get().addLocal(LitematicaWriter.write(model,
                new LitematicaWriter.Metadata(entryName, author, System.currentTimeMillis())), entryName, "litematic"), Util.backgroundExecutor())
                .whenComplete((entry, failure) -> minecraft.execute(() -> saved(token, entry, failure)));
    }

    private void saved(int token, LocalLibrary.@Nullable LocalEntry entry, @Nullable Throwable failure) {
        if (token != generation || !session.on()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (failure != null) {
            Heavymatica.LOGGER.warn("Saving the capture failed", failure);
            session.setProgress(null);
            if (player != null) {
                player.sendOverlayMessage(Component.translatable(failure.getCause() instanceof SchematicRejectedException
                        ? "heavymatica.capture.too_large" : "heavymatica.capture.failed"));
            }
            return;
        }
        session.end();
        if (player != null && entry != null) {
            player.sendOverlayMessage(Component.translatable("heavymatica.capture.saved", entry.name()));
        }
    }

    private void fail(String key) {
        snapshot = null;
        session.setProgress(null);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(key));
        }
    }

    private void cancelRunning() {
        snapshot = null;
        generation++;
        session.setProgress(null);
    }

    /** The box while it may change: capture mode on and no capture running. */
    private Optional<CaptureBox> box() {
        return session.capturing() ? Optional.empty() : session.box();
    }
}
