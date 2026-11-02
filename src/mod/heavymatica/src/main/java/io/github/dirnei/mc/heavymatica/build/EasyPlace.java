package io.github.dirnei.mc.heavymatica.build;

import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.build.PlacementSearch.Click;
import io.github.dirnei.mc.heavymatica.build.PlacementSearch.Found;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Bounds;
import io.github.dirnei.mc.heavymatica.placement.TwoItemBlocks;
import io.github.dirnei.mc.heavymatica.placement.WorldComparison;
import io.github.dirnei.mc.heavymatica.remote.Remotes;
import io.github.dirnei.mc.heavymatica.targeting.GhostTargeting;
import io.github.dirnei.mc.heavymatica.targeting.GhostTargeting.GhostTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Easy Place: a main-hand right-click on a missing or not-enough-yet ghost within reach, holding one of its items,
 * places the planned block with its planned orientation, or uses the held content on the standing base of a two-item
 * block. Sends only what a vanilla client sends: a look direction, a
 * block click, and the real look direction again. Game thread only.
 */
public final class EasyPlace {

    public enum Outcome {
        /** Not an Easy Place click; vanilla and placement restriction decide. */
        NOT_OURS,
        /** The click was sent. */
        PLACED,
        /** The click was sent and the hand swings. */
        PLACED_SWING,
        /** No click gives the planned block; nothing was sent. */
        CANT_PLACE,
    }

    private final PlacementSession session;
    private final GhostTargeting targeting;

    public EasyPlace(PlacementSession session, GhostTargeting targeting) {
        this.session = session;
        this.targeting = targeting;
    }

    public Outcome click(Minecraft minecraft, LocalPlayer player) {
        if (!HeavymaticaConfig.easyPlace() || minecraft.gameMode == null || minecraft.getConnection() == null) {
            return Outcome.NOT_OURS;
        }
        GhostTarget target = targeting.fresh();
        WorldComparison comparison = target == null ? null : session.comparison(target.placement());
        ItemStack held = player.getMainHandItem();
        boolean candidate = comparison != null && target.plannedIndex() >= 0 && candidate(comparison, target.plannedIndex(), held);
        BuildRules.EasyPlaceClick click = new BuildRules.EasyPlaceClick(true, minecraft.screen != null, player.isShiftKeyDown(), player.isSpectator(),
                Remotes.isRemote(held), target == null ? null : target.state(), target != null && target.inReach(), candidate,
                target != null && target.found() != null && WorldComparison.waitsForOtherHalf(target.planned(), target.found()));
        if (!BuildRules.easyPlaces(click)) {
            return Outcome.NOT_OURS;
        }
        Bounds bounds = comparison.bounds();
        int x = target.pos().getX() - bounds.minX();
        int y = target.pos().getY() - bounds.minY();
        int z = target.pos().getZ() - bounds.minZ();
        Block filled = target.found() == null ? null : TwoItemBlocks.filled(target.found(), held.getItem());
        if (BuildRules.easyPlaceAddsContent(target.state(), filled == null ? null : comparison.judgePlaced(x, y, z, filled.defaultBlockState()))) {
            Vec3 eye = player.getEyePosition();
            Click face = PlacementSearch.visibleFace(eye.x - target.pos().getX(), eye.y - target.pos().getY(), eye.z - target.pos().getZ(),
                    player.getYRot(), player.getXRot());
            return outcome(send(minecraft, player, face, target));
        }
        Found<BlockState> found = VanillaPlacement.find(player, InteractionHand.MAIN_HAND, target.pos(),
                state -> BuildRules.easyPlaceAccepts(comparison.judgePlaced(x, y, z, state)));
        if (found == null) {
            player.sendOverlayMessage(Component.translatable("heavymatica.easy_place.cant_place", target.planned().getBlock().getName()));
            return Outcome.CANT_PLACE;
        }
        return outcome(send(minecraft, player, found.click(), target));
    }

    private static Outcome outcome(InteractionResult result) {
        return result instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SwingSource.CLIENT
                ? Outcome.PLACED_SWING : Outcome.PLACED;
    }

    private static boolean candidate(WorldComparison comparison, int plannedIndex, ItemStack held) {
        if (held.isEmpty()) {
            return false;
        }
        for (int index : comparison.accepts(plannedIndex)) {
            if (held.is(comparison.item(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rotation, click, real rotation. The client player has the click's rotation during the click so the client's own
     * prediction places the same state, and gets its own back before the next frame, so the camera never moves. A
     * sneaking click is wrapped the same way in a sneaking input and the real one.
     */
    private static InteractionResult send(Minecraft minecraft, LocalPlayer player, Click click, GhostTarget target) {
        ClientPacketListener connection = minecraft.getConnection();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        Input input = player.input.keyPresses;
        if (click.sneaking()) {
            player.input.keyPresses = VanillaPlacement.sneaking(input, true);
            connection.send(new ServerboundPlayerInputPacket(player.input.keyPresses));
        }
        if (click.turned()) {
            connection.send(new ServerboundMovePlayerPacket.Rot(click.yaw(), click.pitch(), player.onGround(), player.horizontalCollision));
            player.setYRot(click.yaw());
            player.setXRot(click.pitch());
        }
        try {
            return minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, VanillaPlacement.hit(target.pos(), click));
        } finally {
            if (click.turned()) {
                player.setYRot(yaw);
                player.setXRot(pitch);
                connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision));
            }
            if (click.sneaking()) {
                player.input.keyPresses = input;
                connection.send(new ServerboundPlayerInputPacket(player.getLastSentInput()));
            }
        }
    }
}
