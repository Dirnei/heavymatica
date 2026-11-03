package io.github.dirnei.mc.heavymatica.ui.glass;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A Heavymatica screen: the world stays visible behind a dim instead of the game's menu blur, and the game keeps running. */
public abstract class GlassScreen extends Screen {

    protected GlassScreen(Component title) {
        super(title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (minecraft != null && minecraft.level != null) {
            Glass.dim(graphics, width, height, GlassStyle.SCREEN_DIM);
        } else {
            super.extractBackground(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
