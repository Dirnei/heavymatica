package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A large choice: a title with a short explanation under it, marked in the accent while it is the chosen one. */
final class ChoiceCard extends GlassButton {

    private final Component description;
    private boolean chosen;

    ChoiceCard(int x, int y, int width, int height, Component title, Component description, Consumer<GlassButton> onPress) {
        super(x, y, width, height, title, Kind.SECONDARY, onPress);
        this.description = description;
    }

    void choose(boolean chosen) {
        this.chosen = chosen;
    }

    boolean chosen() {
        return chosen;
    }

    /** The height a card of this width needs for its title and explanation. */
    static int heightFor(Font font, Component description, int width) {
        return 26 + Glass.wrap(font, description, width - 16).size() * Glass.LINE;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Font font = Minecraft.getInstance().font;
        boolean hot = active && (isHovered() || isFocused());
        int fill = chosen ? (hot ? 0x337FD8FF : 0x1F7FD8FF) : (hot ? 0x1AFFFFFF : 0x0AFFFFFF);
        int rim = chosen ? 0x997FD8FF : (hot ? 0x47FFFFFF : 0x24FFFFFF);
        Glass.box(graphics, getX(), getY(), width, height, 8, fill, rim);
        if (chosen) {
            Glass.rim(graphics, getX() - 1, getY() - 1, width + 2, height + 2, 9, 1, 0x337FD8FF);
        }
        int textColor = active ? GlassStyle.TEXT : GlassStyle.SUBTITLE;
        Glass.text(graphics, font, getMessage(), getX() + 8, getY() + 8, chosen ? GlassStyle.ACCENT : textColor, width - 16);
        Glass.paragraph(graphics, font, description, getX() + 8, getY() + 21, width - 16, active ? GlassStyle.LABEL : GlassStyle.SUBTITLE);
    }
}
