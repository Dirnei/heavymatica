package io.github.dirnei.mc.heavymatica.placement;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The material list of the selected locked placement, rebuilt from its counts over the visible layers and the
 * player's items. The player's items are counted at most every {@value #REFRESH_TICKS} ticks; the list is rebuilt
 * as often, or at once when the comparison or the visible layers change. Game thread only.
 */
public final class Materials {

    public static final int REFRESH_TICKS = 10;

    private static final Comparator<Item> ITEM_ORDER = Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString());
    private static final Comparator<Block> BLOCK_ORDER = Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString());

    private final CarriedItems carried = new CarriedItems();
    private int ticks;
    private int carriedAt = -REFRESH_TICKS;
    private int builtAt = -REFRESH_TICKS;
    private @Nullable WorldComparison builtFor;
    private Layers.@Nullable Range builtRange;
    private @Nullable MaterialList<Item, Block> list;

    void tick() {
        ticks++;
    }

    /** Makes the next list count the player's items again, for example when the materials screen opens. */
    public void refreshNow() {
        carriedAt = ticks - REFRESH_TICKS;
        builtAt = ticks - REFRESH_TICKS;
    }

    /** How many of an item the player had when their items were last counted. */
    int carried(Item item) {
        return carried.count(item);
    }

    MaterialList<Item, Block> list(WorldComparison comparison, Layers.Range range) {
        if (list != null && comparison == builtFor && range.equals(builtRange) && ticks - builtAt < REFRESH_TICKS) {
            return list;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ticks - carriedAt >= REFRESH_TICKS) {
            carried.refresh(player);
            carriedAt = ticks;
        }
        MaterialCounts.Totals totals = comparison.materials().sum(range.from(), range.to());
        Map<Item, Integer> needed = new IdentityHashMap<>();
        for (int item = 0; item < totals.needed().length; item++) {
            needed.merge(comparison.item(item), totals.needed()[item], Integer::sum);
        }
        Map<Block, Integer> cantCarry = new IdentityHashMap<>();
        for (int block = 0; block < totals.cantCarry().length; block++) {
            cantCarry.merge(comparison.cantCarryBlock(block), totals.cantCarry()[block], Integer::sum);
        }
        list = MaterialList.of(needed, cantCarry, carried::count, ITEM_ORDER, BLOCK_ORDER);
        builtFor = comparison;
        builtRange = range;
        builtAt = ticks;
        return list;
    }
}
