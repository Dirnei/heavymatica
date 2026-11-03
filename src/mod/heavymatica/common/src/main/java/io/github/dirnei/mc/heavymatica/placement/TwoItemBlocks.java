package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.platform.Platform;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Planned blocks that are built from two items: a base block placed first and a content added to it. Potted plants
 * and candle cakes come from the game, so pots and candle cakes that mods register count too. Game thread only.
 */
public final class TwoItemBlocks {

    /**
     * What a two-item block is built from.
     *
     * @param content     the block the content stands for, numbered like any planned block
     * @param contentItem the item that adds the content
     */
    public record Parts(Block base, Block content, Item contentItem) {
    }

    private static final Map<Block, Optional<Parts>> CACHE = new IdentityHashMap<>();
    private static @Nullable Map<Block, Block> candlesByCake;
    private static @Nullable Map<Block, Map<Item, Block>> filledByBase;

    private TwoItemBlocks() {
    }

    /** The parts of a two-item block; null for any other block, and when the content has no item. */
    public static @Nullable Parts of(Block block) {
        return CACHE.computeIfAbsent(block, key -> Optional.ofNullable(lookup(key))).orElse(null);
    }

    /**
     * The two-item block that using the item on a standing block makes: the block must be a base as it is first placed
     * (an uneaten cake, an empty cauldron) and the item that base's content item. Null for any other pair.
     */
    public static @Nullable Block filled(BlockState standing, Item item) {
        Block base = standing.getBlock();
        if (standing != base.defaultBlockState()) {
            return null;
        }
        Map<Item, Block> filled = filledByBase().get(base);
        return filled == null ? null : filled.get(item);
    }

    private static Map<Block, Map<Item, Block>> filledByBase() {
        if (filledByBase == null) {
            Map<Block, Map<Item, Block>> map = new IdentityHashMap<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                Parts parts = of(block);
                if (parts != null) {
                    map.computeIfAbsent(parts.base(), ignored -> new IdentityHashMap<>()).putIfAbsent(parts.contentItem(), block);
                }
            }
            filledByBase = map;
        }
        return filledByBase;
    }

    private static @Nullable Parts lookup(Block block) {
        if (block instanceof FlowerPotBlock pot && pot.getPotted() != Blocks.AIR) {
            return parts(Platform.get().emptyFlowerPot(pot), pot.getPotted(), pot.getPotted().asItem());
        }
        if (block instanceof CandleCakeBlock) {
            Block candle = candlesByCake().get(block);
            return candle == null ? null : parts(Blocks.CAKE, candle, candle.asItem());
        }
        String[] cauldron = BlockEquivalents.FILLED_CAULDRONS.get(BuiltInRegistries.BLOCK.getKey(block).toString());
        if (cauldron != null) {
            return parts(Blocks.CAULDRON, BuiltInRegistries.BLOCK.getValue(Identifier.parse(cauldron[0])),
                    BuiltInRegistries.ITEM.getValue(Identifier.parse(cauldron[1])));
        }
        return null;
    }

    private static @Nullable Parts parts(Block base, Block content, Item contentItem) {
        return contentItem == Items.AIR || base.asItem() == Items.AIR ? null : new Parts(base, content, contentItem);
    }

    private static Map<Block, Block> candlesByCake() {
        if (candlesByCake == null) {
            Map<Block, Block> map = new IdentityHashMap<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block instanceof CandleBlock candle) {
                    map.put(CandleCakeBlock.byCandle(candle).getBlock(), candle);
                }
            }
            candlesByCake = map;
        }
        return candlesByCake;
    }
}
