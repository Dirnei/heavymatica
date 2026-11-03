package io.github.dirnei.mc.heavymatica.targeting;

import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Bounds;
import io.github.dirnei.mc.heavymatica.placement.WorldComparison;
import io.github.dirnei.mc.heavymatica.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the mistake pointer's candidate: the nearest position of the selected locked placement that needs attention.
 * It is searched again every {@link #REFRESH_TICKS} ticks, and at once when the mode, the selected placement, its
 * comparison or its visible layers change; drawing only reads the result.
 */
public final class MistakePointer {

    public static final int REFRESH_TICKS = 10;

    /** A candidate in world coordinates, with its state as found by the last search. */
    public record Target(BlockPos pos, CellState state) {
    }

    /** The last search; a null target means nothing is left in the mode. */
    public record Result(PointerMode mode, @Nullable Target target) {
    }

    private record Key(PointerMode mode, Placement placement, WorldComparison comparison, Layers.Range visible) {
    }

    private final PlacementSession session;
    private @Nullable Key key;
    private @Nullable Result result;
    private int ticks;

    public MistakePointer(PlacementSession session) {
        this.session = session;
    }

    public void register() {
        Platform.get().clientEvents().onTickEnd(this::tick);
    }

    /** The last search, or null while the pointer is off or no locked placement with a comparison is selected. */
    public @Nullable Result current() {
        return result;
    }

    private void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        PointerMode mode = HeavymaticaConfig.mistakePointer();
        Placement selected = session.placements().selected().filter(Placement::locked).orElse(null);
        WorldComparison comparison = selected == null ? null : session.comparison(selected);
        if (mode == PointerMode.OFF || player == null || comparison == null) {
            key = null;
            result = null;
            return;
        }
        Key next = new Key(mode, selected, comparison, Layers.visible(selected, PlacementSession.feetLayer(selected)));
        if (next.equals(key) && ++ticks < REFRESH_TICKS) {
            return;
        }
        key = next;
        ticks = 0;
        result = new Result(mode, search(comparison, player.getEyePosition(), mode, next.visible()));
    }

    static @Nullable Target search(WorldComparison comparison, Vec3 eye, PointerMode mode, Layers.Range visible) {
        Bounds bounds = comparison.bounds();
        MistakeSearch.Candidate candidate = MistakeSearch.nearest(comparison.state(), eye.x - bounds.minX(), eye.y - bounds.minY(), eye.z - bounds.minZ(),
                mode, visible);
        return candidate == null ? null
                : new Target(new BlockPos(bounds.minX() + candidate.x(), bounds.minY() + candidate.y(), bounds.minZ() + candidate.z()), candidate.state());
    }
}
