package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.placement.PlacementController;
import io.github.dirnei.mc.heavymatica.placement.PlacementController.Target;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Relative;
import io.github.dirnei.mc.heavymatica.placement.Placements;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
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
 * Turns the remote's input into placement changes (D4): takes right-click while a remote is held (cancelled before
 * vanilla sends anything to the server), reads the placement keys with key repeat for moves, and keeps a FOLLOWING
 * placement on the crosshair.
 */
public final class RemoteInput {

    /** Ticks a move key is held before it repeats, and ticks between repeats. */
    static final int REPEAT_DELAY = 6;
    static final int REPEAT_INTERVAL = 2;

    private final PlacementSession session;
    private final Runnable openLibrary;
    private final Map<KeyMapping, Integer> heldTicks = new HashMap<>();
    private boolean useHandled;

    public RemoteInput(PlacementSession session, Runnable openLibrary) {
        this.session = session;
        this.openLibrary = openLibrary;
    }

    public void register() {
        NeoForge.EVENT_BUS.addListener(this::interaction);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
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
        LocalPlayer player = minecraft.player;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        PlacementController.Use result = session.controller().use(player.isShiftKeyDown(), dimension(),
                new Placements.Ray(eye.x, eye.y, eye.z, look.x, look.y, look.z));
        if (result == PlacementController.Use.OPEN_LIBRARY) {
            openLibrary.run();
        }
    }

    private void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyUse.isDown()) {
            useHandled = false;
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
        PlacementController controller = session.controller();
        controller.follow(crosshairTarget(), dimension());
        double yaw = minecraft.player.getYRot();
        boolean shift = minecraft.hasShiftDown();
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
