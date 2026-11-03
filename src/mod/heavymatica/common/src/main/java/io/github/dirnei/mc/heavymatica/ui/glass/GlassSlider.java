package io.github.dirnei.mc.heavymatica.ui.glass;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A slider over a range, with its value as text beside the track. */
public final class GlassSlider extends AbstractSliderButton {

    public static final int HEIGHT = 14;
    private static final int LABEL_WIDTH = 48;
    private static final int KNOB = 10;

    private final Component name;
    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<String> format;
    private final DoubleConsumer onChange;

    /** @param step values snap to multiples of it above {@code min}; 0 for none */
    public GlassSlider(int x, int y, int width, Component name, double min, double max, double step, double value, DoubleFunction<String> format,
                       DoubleConsumer onChange) {
        super(x, y, width, HEIGHT, Component.empty(), (value - min) / (max - min));
        this.name = name;
        this.min = min;
        this.max = max;
        this.step = step;
        this.format = format;
        this.onChange = onChange;
        updateMessage();
    }

    public double current() {
        double raw = min + value * (max - min);
        return step > 0 ? Math.min(max, min + Math.round((raw - min) / step) * step) : raw;
    }

    public void set(double newValue) {
        value = Math.clamp((newValue - min) / (max - min), 0, 1);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.empty().append(name).append(": ").append(format.apply(current())));
    }

    @Override
    protected void applyValue() {
        onChange.accept(current());
    }

    // The vanilla slider maps the mouse across the whole widget; this one's track leaves room for the value label and
    // the knob travels the track minus its own width, so both click and drag map onto the knob's path instead.
    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        dragging = active;
        setValue(shareAt(event.x()));
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        setValue(shareAt(event.x()));
    }

    /** The share of the range that puts the knob's centre under {@code mouseX}. */
    double shareAt(double mouseX) {
        return Math.clamp((mouseX - getX() - KNOB / 2.0) / travel(), 0, 1);
    }

    private int travel() {
        return Math.max(1, width - LABEL_WIDTH - KNOB);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Font font = Minecraft.getInstance().font;
        int track = width - LABEL_WIDTH;
        int y = getY() + height / 2 - 2;
        double share = (current() - min) / (max - min);
        Glass.fill(graphics, getX(), y, track, 4, 2, 0x1FFFFFFF);
        Glass.fill(graphics, getX(), y, Math.max(4, KNOB / 2.0 + share * travel()), 4, 2, GlassStyle.ACCENT);
        int knobX = (int) Math.round(getX() + share * travel());
        boolean hot = isHovered() || isFocused();
        if (hot) {
            Glass.fill(graphics, knobX - 2, getY() - 1, KNOB + 4, height + 2, 7, 0x4D7FD8FF);
        }
        Glass.fill(graphics, knobX, getY() + 2, KNOB, height - 4, 5, GlassStyle.TEXT);
        Glass.textRight(graphics, font, format.apply(current()), getX() + width, getY() + (height - 8) / 2, GlassStyle.TEXT);
    }
}
