package io.github.dirnei.mc.heavymatica.fabric;

import io.github.dirnei.mc.heavymatica.platform.ItemContainers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Carried containers through Fabric's item storage (shulker boxes, bundles and modded containers). */
final class FabricItemContainers implements ItemContainers {

    @Override
    public @Nullable Container ofPlayerSlot(Player player, int slot) {
        SingleSlotStorage<ItemVariant> storage = PlayerInventoryStorage.of(player).getSlot(slot);
        return of(ContainerItemContext.ofPlayerSlot(player, storage));
    }

    private static @Nullable Container of(ContainerItemContext context) {
        Storage<ItemVariant> storage = context.find(ItemStorage.ITEM);
        if (storage == null) {
            return null;
        }
        List<StorageView<ItemVariant>> views = new ArrayList<>();
        storage.iterator().forEachRemaining(views::add);
        return new StorageContainer(views);
    }

    private record StorageContainer(List<StorageView<ItemVariant>> views) implements Container {

        @Override
        public int size() {
            return views.size();
        }

        @Override
        public Item item(int slot) {
            return views.get(slot).getResource().getItem();
        }

        @Override
        public long amount(int slot) {
            StorageView<ItemVariant> view = views.get(slot);
            return view.isResourceBlank() ? 0 : view.getAmount();
        }

        @Override
        public ItemStack stack(int slot) {
            StorageView<ItemVariant> view = views.get(slot);
            return view.getResource().toStack((int) Math.min(Integer.MAX_VALUE, view.getAmount()));
        }

        @Override
        @SuppressWarnings("unchecked")
        public @Nullable Container nested(int slot) {
            return views.get(slot) instanceof SingleSlotStorage<?> single
                    ? of(ContainerItemContext.ofSingleSlot((SingleSlotStorage<ItemVariant>) single))
                    : null;
        }
    }
}
