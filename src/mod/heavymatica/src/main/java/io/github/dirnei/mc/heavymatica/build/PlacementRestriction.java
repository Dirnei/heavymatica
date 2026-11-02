package io.github.dirnei.mc.heavymatica.build;

import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.build.BuildRules.Covering;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.placement.CellState.Expected.Kind;
import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Bounds;
import io.github.dirnei.mc.heavymatica.placement.TwoItemBlocks;
import io.github.dirnei.mc.heavymatica.placement.WorldComparison;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Placement restriction: a right-click with a block item, or with the content of a clicked two-item base, that would put
 * a block the comparison judges wrong into a locked placement's visible layers is refused before vanilla sends anything.
 * The click is predicted as vanilla would place it, from vanilla's own hit result and the player's real rotation. Game
 * thread only.
 */
public final class PlacementRestriction {

    public static final ActsOnUse ACTS_ON_USE = new ActsOnUse(BlockBehaviour.class, Set.of("useWithoutItem", "useItemOn"));

    /** The comparison and the local position a restricted world position has in it. */
    private record Local(WorldComparison comparison, int x, int y, int z) {
    }

    private final PlacementSession session;

    public PlacementRestriction(PlacementSession session) {
        this.session = session;
    }

    /** The message for a refused right-click with the hand's item, or null when the click is left to vanilla. */
    public @Nullable Component refusal(Minecraft minecraft, LocalPlayer player, InteractionHand hand) {
        ClientLevel level = minecraft.level;
        if (!HeavymaticaConfig.placementRestriction() || level == null || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        VanillaPlacement.Placed placed = predict(level, player, hand, hit);
        if (placed == null) {
            return null;
        }
        Covering<Local> deciding = BuildRules.deciding(coverings(level.dimension().identifier().toString(), placed.pos()),
                session.placements().selected().map(Placement::id).orElse(null));
        if (deciding == null) {
            return null;
        }
        Local local = deciding.value();
        WorldComparison comparison = local.comparison();
        CellState judged = comparison.judge(local.x(), local.y(), local.z(), placed.state());
        if (!BuildRules.refused(judged)) {
            return null;
        }
        return comparison.kindAt(local.x(), local.y(), local.z()) == Kind.AIR ? Component.translatable("heavymatica.restriction.refused_air")
                : Component.translatable("heavymatica.restriction.refused", comparison.plannedAt(local.x(), local.y(), local.z()).getBlock().getName());
    }

    /**
     * What the click puts where, as restriction judges it: the two-item block a content makes of its clicked base, or
     * the block the held block item places; null when the click is left to vanilla.
     */
    private static VanillaPlacement.@Nullable Placed predict(ClientLevel level, LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        BlockState clicked = level.getBlockState(hit.getBlockPos());
        Block filled = TwoItemBlocks.filled(clicked, stack.getItem());
        if (BuildRules.judgesAsFilled(filled != null, player.isSecondaryUseActive())) {
            return new VanillaPlacement.Placed(hit.getBlockPos(), filled.defaultBlockState());
        }
        boolean blockItem = stack.getItem() instanceof BlockItem;
        VanillaPlacement.Placed placed = blockItem ? VanillaPlacement.predict(player, hand, hit) : null;
        if (placed == null) {
            return null;
        }
        boolean acts = ACTS_ON_USE.acts(clicked.getBlock().getClass());
        return BuildRules.skipsJudging(true, acts, player.isSecondaryUseActive(), placed.pos().equals(hit.getBlockPos())) ? null : placed;
    }

    /** The locked placements of the dimension that hold the position, with whether their layers show it. */
    private List<Covering<Local>> coverings(String dimension, BlockPos pos) {
        List<Covering<Local>> coverings = new ArrayList<>();
        for (Placement placement : session.placements().inDimension(dimension)) {
            WorldComparison comparison = placement.locked() ? session.comparison(placement) : null;
            if (comparison == null) {
                continue;
            }
            Bounds bounds = comparison.bounds();
            int x = pos.getX() - bounds.minX();
            int y = pos.getY() - bounds.minY();
            int z = pos.getZ() - bounds.minZ();
            if (comparison.contains(x, y, z)) {
                UUID id = placement.id();
                coverings.add(new Covering<>(id, Layers.visible(placement, PlacementSession.feetLayer(placement)).contains(y), new Local(comparison, x, y, z)));
            }
        }
        return coverings;
    }
}
