package io.github.dirnei.mc.heavymatica.targeting;

import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.placement.CarriedItems;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.WorldComparison;
import io.github.dirnei.mc.heavymatica.targeting.GhostTargeting.GhostTarget;
import io.github.dirnei.mc.heavymatica.targeting.PickDecision.Action;
import io.github.dirnei.mc.heavymatica.targeting.PickDecision.Candidate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Pick block on a ghost within reach: selects or swaps in the item for the planned block, or adds it in creative, using
 * only what a vanilla client sends. The pick key's clicks are taken before vanilla reads them, since vanilla's pick
 * only fires on a real block and asks the server, which only sees the real world.
 */
public final class GhostPick {

    private final PlacementSession session;
    private final GhostTargeting targeting;

    public GhostPick(PlacementSession session, GhostTargeting targeting) {
        this.session = session;
        this.targeting = targeting;
    }

    public void register() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> tick());
    }

    private void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        if (player == null || gameMode == null || minecraft.screen != null || minecraft.getOverlay() != null || !HeavymaticaConfig.targetingPickBlock()
                || player.isShiftKeyDown() || player.isSpectator()) {
            return;
        }
        GhostTarget target = targeting.fresh();
        if (target == null || !target.inReach() || target.plannedIndex() < 0) {
            return;
        }
        WorldComparison comparison = session.comparison(target.placement());
        if (comparison == null) {
            return;
        }
        Action<Item> action = decide(player, comparison, target.plannedIndex(), false);
        if (action instanceof PickDecision.Vanilla) {
            return;
        }
        boolean clicked = false;
        while (minecraft.options.keyPickItem.consumeClick()) {
            clicked = true;
        }
        if (!clicked) {
            return;
        }
        if (action instanceof PickDecision.NotCarried) {
            action = decide(player, comparison, target.plannedIndex(), true);
        }
        perform(player, gameMode, action);
    }

    /** The pick for the player's items; containers are only looked into when asked, as that reads item handlers. */
    static Action<Item> decide(LocalPlayer player, WorldComparison comparison, int plannedIndex, boolean lookIntoContainers) {
        Inventory inventory = player.getInventory();
        List<Candidate<Item>> candidates = new ArrayList<>();
        for (int index : comparison.accepts(plannedIndex)) {
            Item item = comparison.item(index);
            List<Integer> hotbar = new ArrayList<>();
            List<Integer> main = new ArrayList<>();
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                if (inventory.getItem(slot).is(item)) {
                    (Inventory.isHotbarSlot(slot) ? hotbar : main).add(slot);
                }
            }
            Item container = null;
            if (lookIntoContainers && hotbar.isEmpty() && main.isEmpty()) {
                ItemStack holding = CarriedItems.containerHolding(player, item);
                container = holding == null ? null : holding.getItem();
            }
            candidates.add(new Candidate<>(item, hotbar, main, container));
        }
        return PickDecision.decide(candidates, player.hasInfiniteMaterials(), inventory.getSelectedSlot(), inventory.getSuitableHotbarSlot());
    }

    private static void perform(LocalPlayer player, MultiPlayerGameMode gameMode, Action<Item> action) {
        Inventory inventory = player.getInventory();
        switch (action) {
            case PickDecision.Vanilla<Item> ignored -> {
            }
            case PickDecision.Select<Item> select -> inventory.setSelectedSlot(select.slot());
            case PickDecision.Swap<Item> swap -> {
                if (player.containerMenu != player.inventoryMenu) {
                    return;
                }
                // Main inventory slots keep their index in the player's inventory menu; the button is the hotbar slot.
                gameMode.handleContainerInput(player.inventoryMenu.containerId, swap.inventorySlot(), swap.hotbarSlot(), ContainerInput.SWAP, player);
                inventory.setSelectedSlot(swap.hotbarSlot());
            }
            case PickDecision.CreativeAdd<Item> add -> creativeAdd(inventory, gameMode, add.item(), add.hotbarSlot());
            case PickDecision.InContainer<Item> inContainer -> player.sendOverlayMessage(Component.translatable("heavymatica.pick.in_container",
                    name(inContainer.item()), name(inContainer.container())));
            case PickDecision.NotCarried<Item> notCarried -> player.sendOverlayMessage(Component.translatable("heavymatica.pick.not_carried",
                    name(notCarried.item())));
        }
    }

    /** Puts the item into the hotbar slot as vanilla's creative pick did, moving what was there to a free slot. */
    private static void creativeAdd(Inventory inventory, MultiPlayerGameMode gameMode, Item item, int slot) {
        ItemStack old = inventory.getItem(slot);
        if (!old.isEmpty()) {
            int free = inventory.getFreeSlot();
            if (free >= 0) {
                inventory.setItem(free, old);
                gameMode.handleCreativeModeItemAdd(old, menuSlot(free));
            }
        }
        ItemStack stack = new ItemStack(item);
        inventory.setItem(slot, stack);
        gameMode.handleCreativeModeItemAdd(stack, menuSlot(slot));
        inventory.setSelectedSlot(slot);
    }

    private static int menuSlot(int inventorySlot) {
        return Inventory.isHotbarSlot(inventorySlot) ? InventoryMenu.USE_ROW_SLOT_START + inventorySlot : inventorySlot;
    }

    private static Component name(@Nullable Item item) {
        return item == null ? Component.empty() : item.getName(item.getDefaultInstance());
    }
}
