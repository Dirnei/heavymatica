package io.github.dirnei.mc.heavymatica.ui.glass;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** An on/off switch. */
public final class GlassToggle extends AbstractButton {

    public static final int WIDTH = 26;
    public static final int HEIGHT = 14;

    private final Consumer<Boolean> onChange;
    private boolean on;

    public GlassToggle(int x, int y, Component name, boolean on, Consumer<Boolean> onChange) {
        super(x, y, WIDTH, HEIGHT, name);
        this.on = on;
        this.onChange = onChange;
    }

    public boolean on() {
        return on;
    }

    public void set(boolean value) {
        on = value;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        on = !on;
        onChange.accept(on);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        boolean hot = isHovered() || isFocused();
        int track = on ? (hot ? 0xFFA6E5FF : GlassStyle.ACCENT) : (hot ? 0x38FFFFFF : 0x24FFFFFF);
        Glass.fill(graphics, getX(), getY(), WIDTH, HEIGHT, HEIGHT / 2.0, track);
        int knob = HEIGHT - 4;
        int knobX = on ? getX() + WIDTH - knob - 2 : getX() + 2;
        Glass.fill(graphics, knobX, getY() + 2, knob, knob, knob / 2.0, on ? GlassStyle.ON_ACCENT : GlassStyle.LABEL);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, CommonComponents.optionNameValue(getMessage(), on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
    }
}
