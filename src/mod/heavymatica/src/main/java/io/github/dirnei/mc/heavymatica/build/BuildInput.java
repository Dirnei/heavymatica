package io.github.dirnei.mc.heavymatica.build;

import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.remote.Remotes;
import io.github.dirnei.mc.heavymatica.targeting.GhostTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The use key and the toggle keys of the hand-building helpers. A right-click is the remote's while one is held (its
 * listener cancels the event before this one sees it), otherwise Easy Place's when it is an Easy Place click, otherwise
 * placement restriction judges it, otherwise vanilla handles it. Vanilla repeats the use key while held, so Easy Place
 * repeats at vanilla's rate.
 */
public final class BuildInput {

    private final EasyPlace easyPlace;
    private final PlacementRestriction restriction;

    public BuildInput(PlacementSession session, GhostTargeting targeting) {
        this.easyPlace = new EasyPlace(session, targeting);
        this.restriction = new PlacementRestriction(session);
    }

    /** Register after the remote's input, so it keeps the use key while a remote is held. */
    public void register() {
        NeoForge.EVENT_BUS.addListener(this::interaction);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
    }

    private void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!event.isUseItem() || player == null || Remotes.held()) {
            return;
        }
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            EasyPlace.Outcome outcome = easyPlace.click(minecraft, player);
            if (outcome != EasyPlace.Outcome.NOT_OURS) {
                event.setCanceled(true);
                event.setSwingHand(outcome == EasyPlace.Outcome.PLACED_SWING);
                return;
            }
        }
        Component refusal = restriction.refusal(minecraft, player, event.getHand());
        if (refusal != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
            player.sendOverlayMessage(refusal);
        }
    }

    private static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        while (BuildKeys.TOGGLE_EASY_PLACE.consumeClick()) {
            if (player != null) {
                toggleEasyPlace(player);
            }
        }
        while (BuildKeys.TOGGLE_RESTRICTION.consumeClick()) {
            if (player != null) {
                toggleRestriction(player);
            }
        }
    }

    public static void toggleEasyPlace(LocalPlayer player) {
        HeavymaticaConfig.setEasyPlace(!HeavymaticaConfig.easyPlace());
        player.sendOverlayMessage(Component.translatable(HeavymaticaConfig.easyPlace() ? "heavymatica.easy_place.on" : "heavymatica.easy_place.off"));
    }

    public static void toggleRestriction(LocalPlayer player) {
        HeavymaticaConfig.setPlacementRestriction(!HeavymaticaConfig.placementRestriction());
        player.sendOverlayMessage(Component.translatable(HeavymaticaConfig.placementRestriction() ? "heavymatica.restriction.on" : "heavymatica.restriction.off"));
    }
}
