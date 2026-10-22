package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * The items a player carries: the main inventory, the hotbar and the offhand, plus everything inside carried items
 * that expose NeoForge's item capability (shulker boxes, bundles, backpacks that keep their items on the stack). The
 * handlers are only read, never inserted into or extracted from. Game thread only.
 */
public final class CarriedItems {

    private static final int MAX_DEPTH = 3;

    private final Map<Item, Integer> counts = new IdentityHashMap<>();

    public int count(Item item) {
        return counts.getOrDefault(item, 0);
    }

    /** Counts the player's items again. */
    public void refresh(Player player) {
        counts.clear();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            slot(player, inventory, slot);
        }
        slot(player, inventory, Inventory.SLOT_OFFHAND);
    }

    private void slot(Player player, Inventory inventory, int slot) {
        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty()) {
            return;
        }
        counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        Map<Item, Integer> inside = contents(ItemAccess.forPlayerSlot(player, slot), stack, 1);
        if (inside != null) {
            inside.forEach((item, count) -> counts.merge(item, count, Integer::sum));
        }
    }

    /**
     * The first carried stack, in inventory slot order and then the offhand, that holds the item somewhere inside it;
     * null when none does.
     */
    public static @Nullable ItemStack containerHolding(Player player, Item item) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot <= Inventory.INVENTORY_SIZE; slot++) {
            int index = slot == Inventory.INVENTORY_SIZE ? Inventory.SLOT_OFFHAND : slot;
            ItemStack stack = inventory.getItem(index);
            if (stack.isEmpty()) {
                continue;
            }
            Map<Item, Integer> inside = contents(ItemAccess.forPlayerSlot(player, index), stack, 1);
            if (inside != null && inside.containsKey(item)) {
                return stack;
            }
        }
        return null;
    }

    /** What a stack holds, nested contents included; null when it holds nothing or its handler fails. */
    private static @Nullable Map<Item, Integer> contents(ItemAccess access, ItemStack stack, int depth) {
        ResourceHandler<ItemResource> handler = handler(access, stack);
        if (handler == null) {
            return null;
        }
        Map<Item, Integer> inside = new IdentityHashMap<>();
        try {
            for (int index = 0; index < handler.size(); index++) {
                ItemResource resource = handler.getResource(index);
                long amount = handler.getAmountAsLong(index);
                if (resource.isEmpty() || amount <= 0) {
                    continue;
                }
                inside.merge(resource.getItem(), (int) Math.min(Integer.MAX_VALUE, amount), Integer::sum);
                if (depth < MAX_DEPTH) {
                    Map<Item, Integer> nested = contents(ItemAccess.forHandlerIndex(handler, index), resource.toStack(), depth + 1);
                    if (nested != null) {
                        nested.forEach((item, count) -> inside.merge(item, count, Integer::sum));
                    }
                }
            }
        } catch (RuntimeException e) {
            Heavymatica.LOGGER.debug("Could not read the contents of {}", stack, e);
            return null;
        }
        return inside;
    }

    private static @Nullable ResourceHandler<ItemResource> handler(ItemAccess access, ItemStack stack) {
        try {
            return access.getCapability(Capabilities.Item.ITEM);
        } catch (RuntimeException e) {
            Heavymatica.LOGGER.debug("Could not look into {}", stack, e);
            return null;
        }
    }
}
