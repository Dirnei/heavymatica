package io.github.dirnei.mc.heavymatica.neoforge;

import io.github.dirnei.mc.heavymatica.platform.ItemContainers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/** Carried containers through NeoForge's item capability. */
final class NeoForgeItemContainers implements ItemContainers {

    @Override
    public @Nullable Container ofPlayerSlot(Player player, int slot) {
        return of(ItemAccess.forPlayerSlot(player, slot));
    }

    private static @Nullable Container of(ItemAccess access) {
        ResourceHandler<ItemResource> handler = access.getCapability(Capabilities.Item.ITEM);
        return handler == null ? null : new HandlerContainer(handler);
    }

    private record HandlerContainer(ResourceHandler<ItemResource> handler) implements Container {

        @Override
        public int size() {
            return handler.size();
        }

        @Override
        public Item item(int slot) {
            return handler.getResource(slot).getItem();
        }

        @Override
        public long amount(int slot) {
            return handler.getResource(slot).isEmpty() ? 0 : handler.getAmountAsLong(slot);
        }

        @Override
        public ItemStack stack(int slot) {
            return handler.getResource(slot).toStack();
        }

        @Override
        public @Nullable Container nested(int slot) {
            return of(ItemAccess.forHandlerIndex(handler, slot));
        }
    }
}
