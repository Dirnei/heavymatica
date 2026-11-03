package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The in-hand filter of one frame: the blocks the held block places or counts toward show at full opacity, those of
 * other hotbar blocks dimmed, everything else is hidden.
 */
public final class InHandFilter {

    private final @Nullable Block held;
    private final List<Block> hotbar;
    private final float opacity;
    private final float dimmed;
    private final Map<WorldComparison, ToDoubleFunction<Block>> alphas = new IdentityHashMap<>();

    private InHandFilter(@Nullable Block held, List<Block> hotbar, float opacity, float dimmed) {
        this.held = held;
        this.hotbar = hotbar;
        this.opacity = opacity;
        this.dimmed = dimmed;
    }

    /** The filter for the player's held and hotbar items now; hides everything outside a world. */
    static InHandFilter current() {
        float opacity = HeavymaticaConfig.ghostOpacity();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return new InHandFilter(null, List.of(), opacity, 0.0F);
        }
        List<Block> hotbar = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            Block block = PlacementSession.placedBlock(inventory.getItem(slot));
            if (block != null) {
                hotbar.add(block);
            }
        }
        return new InHandFilter(PlacementSession.placedBlock(player.getMainHandItem()), hotbar, opacity, opacity * HeavymaticaConfig.layersHotbarOpacity());
    }

    /** A planned block's opacity in a placement with the filter on: the ghost opacity, dimmed, or 0 when hidden. */
    ToDoubleFunction<Block> alpha(WorldComparison comparison) {
        return alphas.computeIfAbsent(comparison, this::build);
    }

    /** True when a placement with the filter on shows a planned block, dimmed or not. */
    public boolean shows(WorldComparison comparison, Block planned) {
        return alpha(comparison).applyAsDouble(planned) > 0.0;
    }

    private ToDoubleFunction<Block> build(WorldComparison comparison) {
        Set<Block> lit = held == null ? Set.of() : comparison.highlights(held);
        Set<Block> others = new HashSet<>();
        for (Block block : hotbar) {
            others.addAll(comparison.highlights(block));
        }
        return block -> lit.contains(block) ? opacity : others.contains(block) ? dimmed : 0.0;
    }
}
