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
        contents(ItemAccess.forPlayerSlot(player, slot), stack, 1, counts);
    }

    /** Adds what a stack holds to {@code into}; a handler that fails adds nothing, so its stack counts as itself only. */
    private static void contents(ItemAccess access, ItemStack stack, int depth, Map<Item, Integer> into) {
        ResourceHandler<ItemResource> handler = handler(access, stack);
        if (handler == null) {
            return;
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
                    contents(ItemAccess.forHandlerIndex(handler, index), resource.toStack(), depth + 1, inside);
                }
            }
        } catch (RuntimeException e) {
            Heavymatica.LOGGER.debug("Could not read the contents of {}", stack, e);
            return;
        }
        inside.forEach((item, count) -> into.merge(item, count, Integer::sum));
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
