package io.github.dirnei.mc.heavymatica.build;

import io.github.dirnei.mc.heavymatica.build.PlacementSearch.Click;
import io.github.dirnei.mc.heavymatica.build.PlacementSearch.Found;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The game's own placement logic as the client sees it: which state a held block item places from a click, with the
 * client player turned to the click's rotation for the moment of asking. Game thread only.
 */
public final class VanillaPlacement {

    private VanillaPlacement() {
    }

    /** The first click at the target that makes the held block item place an accepted state, or null. */
    public static @Nullable Found<BlockState> find(LocalPlayer player, InteractionHand hand, BlockPos target, Predicate<BlockState> accept) {
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        try {
            return PlacementSearch.search(PlacementSearch.candidates(yaw, pitch), click -> {
                player.setYRot(click.yaw());
                player.setXRot(click.pitch());
                return placed(player, hand, hit(target, click), target);
            }, accept);
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    /** The click as the game's hit result. */
    public static BlockHitResult hit(BlockPos target, Click click) {
        Vec3 location = new Vec3(target.getX() + click.hitX(), target.getY() + click.hitY(), target.getZ() + click.hitZ());
        return new BlockHitResult(location, Direction.from3DDataValue(click.face().ordinal()), target, false);
    }

    /**
     * The state the held item would place from this hit with the player's current rotation, as vanilla's own placement
     * decides it, and where it lands; null when the hand holds no block item or the click places nothing.
     */
    public static @Nullable Placed predict(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem item)) {
            return null;
        }
        BlockPlaceContext context = item.updatePlacementContext(new BlockPlaceContext(player, hand, stack, hit));
        if (context == null || !context.canPlace()) {
            return null;
        }
        BlockState state = item.getPlacementState(context);
        return state == null ? null : new Placed(context.getClickedPos(), state);
    }

    /** Where a predicted placement lands and what it places there. */
    public record Placed(BlockPos pos, BlockState state) {
    }

    private static @Nullable BlockState placed(LocalPlayer player, InteractionHand hand, BlockHitResult hit, BlockPos target) {
        Placed placed = predict(player, hand, hit);
        return placed == null || !placed.pos().equals(target) ? null : placed.state();
    }
}
