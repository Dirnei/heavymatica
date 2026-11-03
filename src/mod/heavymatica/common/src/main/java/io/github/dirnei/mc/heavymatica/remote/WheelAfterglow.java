package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.platform.Platform;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The wheel's last frame growing and fading for a moment after the wheel closed, drawn on the HUD because the screen
 * is already gone. Nothing is drawn while another screen is open.
 */
public final class WheelAfterglow {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "wheel_afterglow");

    private static @Nullable WheelPainter last;
    private static long closedAt;

    private WheelAfterglow() {
    }

    public static void register() {
        Platform.get().hudLayers().top(ID, WheelAfterglow::render);
    }

    static void start(WheelPainter painter) {
        last = painter;
        closedAt = Util.getMillis();
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        WheelPainter painter = last;
        if (painter == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long elapsed = Util.getMillis() - closedAt;
        if (minecraft.screen != null || elapsed >= WheelAnimation.CLOSE) {
            last = null;
            return;
        }
        painter.paint(graphics, minecraft.font, WheelAnimation.closeAlpha(true, elapsed), WheelAnimation.closeScale(true, elapsed));
    }
}
