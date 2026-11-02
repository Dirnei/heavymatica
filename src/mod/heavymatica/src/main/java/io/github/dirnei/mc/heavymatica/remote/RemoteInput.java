package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.build.BuildInput;
import io.github.dirnei.mc.heavymatica.capture.CaptureController;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.paste.CreativePaste;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementController;
import io.github.dirnei.mc.heavymatica.placement.PlacementController.Target;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Relative;
import io.github.dirnei.mc.heavymatica.placement.Placements;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Turns the remote's input into placement changes: takes right-click while a remote is held (cancelled before
 * vanilla sends anything to the server), reads the placement keys with key repeat for moves, and keeps a FOLLOWING
 * placement on the crosshair.
 */
public final class RemoteInput {

    /** Ticks a move key is held before it repeats, and ticks between repeats. */
    static final int REPEAT_DELAY = 6;
    static final int REPEAT_INTERVAL = 2;

    private final PlacementSession session;
    private final Runnable openLibrary;
    private final Runnable openMaterials;
    private final CaptureController capture;
    private final CreativePaste paste;
    private final Map<KeyMapping, Integer> heldTicks = new HashMap<>();
    private boolean useHandled;
    private static boolean shiftHeldForDevelopment;

    public RemoteInput(PlacementSession session, Runnable openLibrary, Runnable openMaterials, CaptureController capture, CreativePaste paste) {
        this.session = session;
        this.openLibrary = openLibrary;
        this.openMaterials = openMaterials;
        this.capture = capture;
        this.paste = paste;
    }

    public void register() {
        RemoteKeys.lockedSelected(this::lockedSelected);
        NeoForge.EVENT_BUS.addListener(this::interaction);
        NeoForge.EVENT_BUS.addListener(this::scroll);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> claimSharedKeys());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
    }

    private boolean lockedSelected() {
        return session.placements().selected().map(Placement::locked).orElse(false);
    }

    private boolean layerKeysWithoutRemote(Minecraft minecraft) {
        return minecraft.player != null
                && LayerKeyRules.withoutRemote(minecraft.screen != null, Remotes.held(), lockedSelected(), capture.session().on());
    }

    /** The current dimension's ID, or an empty string outside a world. */
    public static String dimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
    }

    /**
     * The block a following ghost sits on: in front of the block face under the crosshair, up to the remote's reach;
     * a replaceable block (grass, snow layer) is taken itself. Null when the crosshair hits nothing.
     */
    public static @Nullable Target crosshairTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(Placements.REACH));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos target = minecraft.level.getBlockState(hit.getBlockPos()).canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        return new Target(target.getX(), target.getY(), target.getZ());
    }

    private void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isUseItem() || minecraft.player == null || !Remotes.held()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        // Vanilla repeats use while the button is held; only a fresh press counts.
        if (event.getHand() != InteractionHand.MAIN_HAND || useHandled) {
            return;
        }
        useHandled = true;
        if (capture.session().on()) {
            capture.click();
            return;
        }
        LocalPlayer player = minecraft.player;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        PlacementController.Use result = session.controller().use(player.isShiftKeyDown(), dimension(),
                new Placements.Ray(eye.x, eye.y, eye.z, look.x, look.y, look.z));
        if (result == PlacementController.Use.OPEN_LIBRARY) {
            openLibrary.run();
        }
    }

    /**
     * While the remote is held its keys win: presses of other bindings on the same key (L is vanilla's advancements key)
     * are dropped before vanilla handles them. Without the remote on a locked placement, only the layer keys win.
     */
    private void claimSharedKeys() {
        Minecraft minecraft = Minecraft.getInstance();
        List<KeyMapping> claimed;
        if (minecraft.player != null && minecraft.screen == null && Remotes.held()) {
            claimed = RemoteKeys.ALL;
        } else if (layerKeysWithoutRemote(minecraft)) {
            claimed = RemoteKeys.LAYER;
        } else {
            return;
        }
        for (KeyMapping other : minecraft.options.keyMappings) {
            if (RemoteKeys.ALL.contains(other) || other.isUnbound()) {
                continue;
            }
            for (KeyMapping mine : claimed) {
                if (mine.getKey().equals(other.getKey())) {
                    while (other.consumeClick()) {
                        // Dropped: the remote's binding handles this key.
                    }
                }
            }
        }
    }

    /** Development aid: scripts can't hold physical keys, so this makes Shift count as held. */
    public static void holdShiftForDevelopment(boolean held) {
        shiftHeldForDevelopment = held;
    }

    private static boolean shift(Minecraft minecraft) {
        return minecraft.hasShiftDown() || shiftHeldForDevelopment;
    }

    /** Shift + scroll swaps the active corner in capture mode, instead of changing the hotbar slot. */
    private void scroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!capture.session().on() || minecraft.player == null || minecraft.screen != null || !Remotes.held() || !shift(minecraft)) {
            return;
        }
        event.setCanceled(true);
        if (event.getScrollDeltaY() != 0) {
            capture.swap();
        }
    }

    private void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        capture.tick();
        if (!minecraft.options.keyUse.isDown()) {
            useHandled = false;
        }
        if (layerKeysWithoutRemote(minecraft)) {
            layerKeysWithoutRemote(minecraft.player);
            return;
        }
        if (minecraft.player == null || minecraft.screen != null || !Remotes.held()) {
            RemoteKeys.ALL.forEach(key -> {
                while (key.consumeClick()) {
                    // Drop presses made while the keys were inactive.
                }
            });
            heldTicks.clear();
            return;
        }
        if (capture.session().on()) {
            captureKeys(minecraft);
            return;
        }
        while (RemoteKeys.SWAP_CORNER.consumeClick()) {
            // Only capture mode has corners.
        }
        PlacementController controller = session.controller();
        controller.follow(crosshairTarget(), dimension());
        while (RemoteKeys.LOCK.consumeClick()) {
            controller.lockOrUnlock(System.currentTimeMillis());
        }
        Placement selected = session.placements().selected().orElse(null);
        while (RemoteKeys.MATERIALS.consumeClick()) {
            if (selected != null) {
                openMaterials.run();
            } else {
                minecraft.player.sendOverlayMessage(Component.translatable("heavymatica.materials.select_first"));
            }
        }
        boolean wheel = false;
        while (RemoteKeys.WHEEL.consumeClick()) {
            wheel = true;
        }
        if (wheel && minecraft.screen == null) {
            openWheel(minecraft.player, selected);
        }
        if (minecraft.screen != null) {
            return;
        }
        boolean shift = minecraft.hasShiftDown();
        if (selected != null && selected.locked()) {
            layerKeys(controller, PlacementSession.feetLayer(selected), shift);
            return;
        }
        for (KeyMapping lockedOnly : new KeyMapping[] {RemoteKeys.IN_HAND, RemoteKeys.CYCLE_LAYERS}) {
            while (lockedOnly.consumeClick()) {
                // Only a locked placement has layers and an in-hand filter.
            }
        }
        double yaw = minecraft.player.getYRot();
        repeating(RemoteKeys.MOVE_FORWARD, () -> controller.move(Relative.FORWARD, yaw, shift));
        repeating(RemoteKeys.MOVE_BACK, () -> controller.move(Relative.BACK, yaw, shift));
        repeating(RemoteKeys.MOVE_LEFT, () -> controller.move(Relative.LEFT, yaw, shift));
        repeating(RemoteKeys.MOVE_RIGHT, () -> controller.move(Relative.RIGHT, yaw, shift));
        repeating(RemoteKeys.MOVE_UP, () -> controller.vertical(1, shift));
        repeating(RemoteKeys.MOVE_DOWN, () -> controller.vertical(-1, shift));
        while (RemoteKeys.ROTATE.consumeClick()) {
            controller.rotate(shift);
        }
        while (RemoteKeys.MIRROR.consumeClick()) {
            controller.mirror();
        }
        while (RemoteKeys.REMOVE.consumeClick()) {
            controller.remove(System.currentTimeMillis());
        }
    }

    /** In capture mode the arrows and numpad + / - move the active corner; the other placement keys do nothing. */
    private void captureKeys(Minecraft minecraft) {
        boolean wheel = false;
        while (RemoteKeys.WHEEL.consumeClick()) {
            wheel = true;
        }
        if (wheel) {
            openWheel(minecraft.player, session.placements().selected().orElse(null));
            return;
        }
        boolean shift = shift(minecraft);
        double yaw = minecraft.player.getYRot();
        repeating(RemoteKeys.MOVE_FORWARD, () -> capture.move(Relative.FORWARD, yaw, shift));
        repeating(RemoteKeys.MOVE_BACK, () -> capture.move(Relative.BACK, yaw, shift));
        repeating(RemoteKeys.MOVE_LEFT, () -> capture.move(Relative.LEFT, yaw, shift));
        repeating(RemoteKeys.MOVE_RIGHT, () -> capture.move(Relative.RIGHT, yaw, shift));
        repeating(RemoteKeys.MOVE_UP, () -> capture.vertical(1, shift));
        repeating(RemoteKeys.MOVE_DOWN, () -> capture.vertical(-1, shift));
        while (RemoteKeys.SWAP_CORNER.consumeClick()) {
            capture.swap();
        }
        for (KeyMapping ignored : new KeyMapping[] {RemoteKeys.ROTATE, RemoteKeys.MIRROR, RemoteKeys.REMOVE, RemoteKeys.LOCK, RemoteKeys.IN_HAND,
                RemoteKeys.MATERIALS, RemoteKeys.CYCLE_LAYERS}) {
            while (ignored.consumeClick()) {
                // Drop presses; no placement changes while capturing.
            }
        }
    }

    /**
     * Without the remote on a locked placement only the layer keys act, and the action bar shows a change since the
     * overlay is hidden; every other remote key is dropped.
     */
    private void layerKeysWithoutRemote(LocalPlayer player) {
        for (KeyMapping key : RemoteKeys.ALL) {
            if (!RemoteKeys.LAYER.contains(key)) {
                while (key.consumeClick()) {
                    // Only the layer keys act without the remote.
                }
            }
        }
        Placement selected = session.placements().selected().orElse(null);
        if (selected == null) {
            return;
        }
        layerChanges(session.controller(), PlacementSession.feetLayer(selected), shift(Minecraft.getInstance()));
        session.placements().selected().filter(placement -> !placement.layers().equals(selected.layers()))
                .ifPresent(placement -> player.sendOverlayMessage(PlacementOverlay.layersText(placement)));
    }

    /** Up / down move the from-to window, numpad + / - resize it or change the follow offset, and the cycle key steps the setting. */
    private void layerChanges(PlacementController controller, int feetLayer, boolean shift) {
        repeating(RemoteKeys.MOVE_FORWARD, () -> controller.moveWindow(1));
        repeating(RemoteKeys.MOVE_BACK, () -> controller.moveWindow(-1));
        repeating(RemoteKeys.MOVE_UP, () -> controller.resizeWindow(1));
        repeating(RemoteKeys.MOVE_DOWN, () -> controller.resizeWindow(-1));
        while (RemoteKeys.CYCLE_LAYERS.consumeClick()) {
            controller.cycleLayers(shift ? -1 : 1, feetLayer);
        }
    }

    /**
     * On a locked placement up / down and numpad + / - work on its layers; left / right, rotate, mirror and remove do
     * nothing.
     */
    private void layerKeys(PlacementController controller, int feetLayer, boolean shift) {
        layerChanges(controller, feetLayer, shift);
        while (RemoteKeys.IN_HAND.consumeClick()) {
            controller.toggleInHand();
        }
        for (KeyMapping ignored : new KeyMapping[] {RemoteKeys.MOVE_LEFT, RemoteKeys.MOVE_RIGHT, RemoteKeys.ROTATE, RemoteKeys.MIRROR, RemoteKeys.REMOVE}) {
            while (ignored.consumeClick()) {
                // Drop presses; a locked placement does not move, rotate, mirror or get removed.
            }
        }
    }

    private void openWheel(LocalPlayer player, @Nullable Placement selected) {
        RadialMenu.Selection selection = selected == null ? RadialMenu.Selection.NONE
                : new RadialMenu.Selection(switch (selected.state()) {
                    case FOLLOWING -> RadialMenu.State.FOLLOWING;
                    case ANCHORED -> RadialMenu.State.ANCHORED;
                    case LOCKED -> RadialMenu.State.LOCKED;
                }, selected.name(), selected.layers().mode(), selected.inHand(), session.unknownBlocks(selected).any(),
                        HeavymaticaConfig.ghostShowUnknownBlocks());
        selection = selection.with(new RadialMenu.Paste(CreativePaste.available(), paste.running(), paste.undoable()))
                .withBuild(HeavymaticaConfig.easyPlace(), HeavymaticaConfig.placementRestriction()).with(HeavymaticaConfig.mistakePointer());
        RadialMenu.Capture captureState = capture.session().box()
                .map(box -> new RadialMenu.Capture(true, capture.session().capturing() ? 0 : box.corners())).orElse(RadialMenu.Capture.OFF);
        Minecraft.getInstance().setScreen(new RadialMenuScreen(new RadialMenu(HeavymaticaConfig.wheelMode(), selection,
                new RadialMenu.Opacity(HeavymaticaConfig.ghostOpacitySteps(), HeavymaticaConfig.ghostOpacity()), session.canBringBack(), captureState),
                this::run, player.input.keyPresses));
    }

    /** Runs a wheel command; the wheel has closed already. */
    private void run(RadialMenu.Outcome.Run run) {
        RadialMenu.Command command = run.command();
        PlacementController controller = session.controller();
        switch (command) {
            case BRING_BACK -> session.bringBack();
            case OPEN_LIBRARY -> openLibrary.run();
            case OPEN_MATERIALS -> openMaterials.run();
            case CANCEL, REMOVE -> controller.removeNow();
            case LOCK -> controller.lock();
            case UNLOCK -> controller.unlock();
            case TOGGLE_IN_HAND -> controller.toggleInHand();
            case TOGGLE_EASY_PLACE -> BuildInput.toggleEasyPlace(Minecraft.getInstance().player);
            case TOGGLE_RESTRICTION -> BuildInput.toggleRestriction(Minecraft.getInstance().player);
            case SET_MISTAKE_POINTER -> HeavymaticaConfig.setMistakePointer(run.pointer());
            case LAYERS_OFF, LAYERS_FROM_TO, LAYERS_FOLLOW -> session.placements().selected()
                    .ifPresent(placement -> controller.setLayerMode(command.layerMode(), PlacementSession.feetLayer(placement)));
            case SET_OPACITY -> HeavymaticaConfig.setGhostOpacity(run.value());
            case TOGGLE_UNKNOWN_BLOCKS -> HeavymaticaConfig.setGhostShowUnknownBlocks(!HeavymaticaConfig.ghostShowUnknownBlocks());
            case START_CAPTURE -> capture.start();
            case CAPTURE -> capture.capture();
            case SWAP_CORNER -> capture.swap();
            case RESET_CORNERS -> capture.reset();
            case CANCEL_CAPTURE -> capture.cancel();
            case PASTE -> session.placements().selected().ifPresent(placement -> {
                SchematicModel model = session.model(placement);
                if (model == null) {
                    Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("heavymatica.paste.not_loaded"));
                } else {
                    paste.paste(placement, model, run.paste());
                }
            });
            case UNDO_PASTE -> paste.undo();
        }
    }

    /** Acts once per press, then every {@link #REPEAT_INTERVAL} ticks once held for {@link #REPEAT_DELAY} ticks. */
    private void repeating(KeyMapping key, Runnable action) {
        int presses = 0;
        while (key.consumeClick()) {
            presses++;
        }
        for (int i = 0; i < presses; i++) {
            action.run();
        }
        if (!key.isDown()) {
            heldTicks.remove(key);
            return;
        }
        int ticks = heldTicks.merge(key, 1, Integer::sum);
        if (presses == 0 && ticks > REPEAT_DELAY && (ticks - REPEAT_DELAY - 1) % REPEAT_INTERVAL == 0) {
            action.run();
        }
    }
}
