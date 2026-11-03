package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.platform.ItemContainers;
import io.github.dirnei.mc.heavymatica.platform.Platform;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The items a player carries: the main inventory, the hotbar and the offhand, plus everything inside carried items
 * that expose their contents through the loader's item container API (shulker boxes, bundles, backpacks that keep their
 * items on the stack). The containers are only read, never inserted into or extracted from. Game thread only.
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
        Map<Item, Integer> inside = contents(() -> Platform.get().itemContainers().ofPlayerSlot(player, slot), stack, 1);
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
            Map<Item, Integer> inside = contents(() -> Platform.get().itemContainers().ofPlayerSlot(player, index), stack, 1);
            if (inside != null && inside.containsKey(item)) {
                return stack;
            }
        }
        return null;
    }

    /** What a stack holds, nested contents included; null when it holds nothing or its container fails. */
    private static @Nullable Map<Item, Integer> contents(Supplier<ItemContainers.@Nullable Container> access, ItemStack stack, int depth) {
        ItemContainers.Container container = container(access, stack);
        if (container == null) {
            return null;
        }
        Map<Item, Integer> inside = new IdentityHashMap<>();
        try {
            for (int index = 0; index < container.size(); index++) {
                long amount = container.amount(index);
                if (amount <= 0) {
                    continue;
                }
                Item item = container.item(index);
                if (item == Items.AIR) {
                    continue;
                }
                inside.merge(item, (int) Math.min(Integer.MAX_VALUE, amount), Integer::sum);
                if (depth < MAX_DEPTH) {
                    int slot = index;
                    Map<Item, Integer> nested = contents(() -> container.nested(slot), container.stack(index), depth + 1);
                    if (nested != null) {
                        nested.forEach((nestedItem, count) -> inside.merge(nestedItem, count, Integer::sum));
                    }
                }
            }
        } catch (RuntimeException e) {
            Heavymatica.LOGGER.debug("Could not read the contents of {}", stack, e);
            return null;
        }
        return inside;
    }

    private static ItemContainers.@Nullable Container container(Supplier<ItemContainers.@Nullable Container> access, ItemStack stack) {
        try {
            return access.get();
        } catch (RuntimeException e) {
            Heavymatica.LOGGER.debug("Could not look into {}", stack, e);
            return null;
        }
    }
}
