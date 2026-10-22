package io.github.dirnei.mc.heavymatica.targeting;

import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.placement.BlockEquivalents;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.placement.InHandFilter;
import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Bounds;
import io.github.dirnei.mc.heavymatica.placement.WorldComparison;
import io.github.dirnei.mc.heavymatica.targeting.GhostRay.Answer;
import io.github.dirnei.mc.heavymatica.targeting.TargetRules.Candidate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * The ghost under the crosshair: the nearest position along the line of sight that a locked placement draws, unless a
 * real block outside such positions is hit first. Computed at most once per frame. Game and render thread, which are
 * the same on the client.
 */
public final class GhostTargeting {

    /**
     * @param planned      the block the placement shows there, after swaps, rotation and mirror
     * @param found        the world's block there, or null for air
     * @param finish       the step that finishes the planned block, or null
     * @param plannedIndex the comparison's planned block index, or -1 when no item places the block
     * @param distance     how far along the line of sight the position starts
     * @param inReach      within the player's block interaction range, where pick block works
     */
    public record GhostTarget(Placement placement, BlockPos pos, BlockState planned, CellState state, @Nullable BlockState found,
                              BlockEquivalents.@Nullable Finish finish, int plannedIndex, double distance, boolean inReach) {

        public boolean plannedWaterlogged() {
            return planned.hasProperty(BlockStateProperties.WATERLOGGED) && planned.getValue(BlockStateProperties.WATERLOGGED);
        }
    }

    /** A locked placement the line of sight passes, with what decides which of its positions are drawn. */
    private record Drawn(Placement placement, WorldComparison comparison, Bounds bounds, Layers.Range layers) {
    }

    /** A drawn position of one placement. */
    private record Local(Drawn drawn, int x, int y, int z) {
    }

    private final PlacementSession session;
    private boolean computed;
    private @Nullable GhostTarget current;

    public GhostTargeting(PlacementSession session) {
        this.session = session;
    }

    public void register() {
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> computed = false);
    }

    /** This frame's target; null when there is none. */
    public @Nullable GhostTarget current() {
        if (!computed) {
            current = compute();
            computed = true;
        }
        return current;
    }

    /** The target recomputed now. */
    public @Nullable GhostTarget fresh() {
        computed = false;
        return current();
    }

    private @Nullable GhostTarget compute() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return null;
        }
        double reach = player.blockInteractionRange();
        double length = Math.max(HeavymaticaConfig.targetingHudRange(), reach);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(length));
        List<Drawn> drawn = drawnAlong(level.dimension().identifier().toString(), eye, end);
        if (drawn.isEmpty()) {
            return null;
        }
        UUID selected = session.placements().selected().map(Placement::id).orElse(null);
        InHandFilter inHand = session.inHandFilter();
        AABB body = player.getBoundingBox();
        CollisionContext context = CollisionContext.of(player);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<Candidate<Local>> candidates = new ArrayList<>();
        Local[] chosen = new Local[1];
        GhostRay.Hit hit = GhostRay.cast(eye.x, eye.y, eye.z, look.x, look.y, look.z, length, (x, y, z, entry) -> {
            if (body.intersects(x, y, z, x + 1, y + 1, z + 1)) {
                return Answer.PASS;
            }
            candidates.clear();
            for (Drawn placement : drawn) {
                Local local = new Local(placement, x - placement.bounds().minX(), y - placement.bounds().minY(), z - placement.bounds().minZ());
                if (drawnAt(local, inHand)) {
                    candidates.add(new Candidate<>(placement.placement().id(), local));
                }
            }
            Candidate<Local> candidate = TargetRules.choose(candidates, selected);
            if (candidate != null) {
                chosen[0] = candidate.value();
                return Answer.TARGET;
            }
            BlockState world = level.getBlockState(pos.set(x, y, z));
            if (world.isAir()) {
                return Answer.PASS;
            }
            return world.getShape(level, pos, context).clip(eye, end, pos) != null ? Answer.BLOCKED : Answer.PASS;
        });
        if (hit == null) {
            return null;
        }
        Local local = chosen[0];
        WorldComparison comparison = local.drawn().comparison();
        BlockPos at = new BlockPos(hit.x(), hit.y(), hit.z());
        BlockState world = level.getBlockState(at);
        return new GhostTarget(local.drawn().placement(), at, comparison.plannedAt(local.x(), local.y(), local.z()),
                comparison.stateAt(local.x(), local.y(), local.z()), world.isAir() ? null : world, comparison.finishAt(local.x(), local.y(), local.z()),
                comparison.plannedIndexAt(local.x(), local.y(), local.z()), hit.distance(), hit.distance() <= reach);
    }

    /** The locked placements of the dimension drawn cell by cell in the last frame whose box the line of sight passes. */
    private List<Drawn> drawnAlong(String dimension, Vec3 eye, Vec3 end) {
        List<Drawn> drawn = new ArrayList<>();
        for (Placement placement : session.placements().inDimension(dimension)) {
            WorldComparison comparison = placement.locked() && session.drawnThroughCells(placement) ? session.comparison(placement) : null;
            if (comparison == null) {
                continue;
            }
            Bounds bounds = comparison.bounds();
            AABB box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
            if (box.contains(eye) || box.clip(eye, end).isPresent()) {
                drawn.add(new Drawn(placement, comparison, bounds, Layers.visible(placement, PlacementSession.feetLayer(placement))));
            }
        }
        return drawn;
    }

    private static boolean drawnAt(Local local, InHandFilter inHand) {
        WorldComparison comparison = local.drawn().comparison();
        int x = local.x();
        int y = local.y();
        int z = local.z();
        if (!comparison.contains(x, y, z)) {
            return false;
        }
        CellState state = comparison.stateAt(x, y, z);
        boolean inHandShows = !local.drawn().placement().inHand() || !state.showsGhost()
                || inHand.shows(comparison, comparison.plannedAt(x, y, z).getBlock());
        return TargetRules.drawn(comparison.kindAt(x, y, z), state, local.drawn().layers().contains(y), inHandShows);
    }
}
