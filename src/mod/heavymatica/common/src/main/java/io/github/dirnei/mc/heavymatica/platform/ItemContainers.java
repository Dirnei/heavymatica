package io.github.dirnei.mc.heavymatica.platform;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Read access to what carried items hold, through the loader's item container API. Game thread only. */
public interface ItemContainers {

    /** What the stack in a player inventory slot holds; null when it holds nothing the loader can see. */
    @Nullable Container ofPlayerSlot(Player player, int slot);

    /** A container's slots. */
    interface Container {

        int size();

        /** The item in the slot; an empty slot has amount 0. */
        Item item(int slot);

        long amount(int slot);

        ItemStack stack(int slot);

        /** What the item in the slot holds itself; null when nothing. */
        @Nullable Container nested(int slot);
    }
}
