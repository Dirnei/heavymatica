package io.github.dirnei.mc.heavymatica.build;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;
import io.github.dirnei.mc.heavymatica.remote.RemoteInput;
import io.github.dirnei.mc.heavymatica.remote.WheelIcons;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

/** A small row above the hotbar with the icon of each build option that is on, near a locked placement. */
public final class BuildOptionsHud {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "build_options");

    private static final int CELL = 20;
    private static final int SPACING = 3;
    /** Clears the hotbar, the experience bar, the hearts and food, and the held item's name. */
    private static final int ABOVE_BOTTOM = 64;

    private final Supplier<@Nullable PlacementSession> sessions;

    /** Looked up per frame: GUI layers are registered before the client services exist. */
    public BuildOptionsHud(Supplier<@Nullable PlacementSession> sessions) {
        this.sessions = sessions;
    }

    public void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ID, this::render);
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        PlacementSession session = sessions.get();
        if (session == null || minecraft.player == null) {
            return;
        }
        boolean locked = session.placements().inDimension(RemoteInput.dimension()).stream().anyMatch(Placement::locked);
        List<Entry> entries = BuildOptionsRules.shown(HeavymaticaConfig.easyPlace(), HeavymaticaConfig.placementRestriction(), locked,
                minecraft.screen != null, minecraft.options.hideGui);
        if (entries.isEmpty()) {
            return;
        }
        int width = entries.size() * CELL + (entries.size() - 1) * SPACING;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - ABOVE_BOTTOM - CELL;
        for (Entry entry : entries) {
            Glass.panel(graphics, x, y, CELL, CELL, 4, 0xD61C232C, 0xD10E1218, 0x1AFFFFFF);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, WheelIcons.sprite(entry, false)),
                    x + 2, y + 2, 16, 16);
            x += CELL + SPACING;
        }
    }
}
