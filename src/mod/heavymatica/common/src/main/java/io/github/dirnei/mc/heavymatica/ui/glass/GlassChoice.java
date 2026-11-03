package io.github.dirnei.mc.heavymatica.ui.glass;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A segmented choice of a few options side by side; the chosen one is filled with the accent. */
public final class GlassChoice<T> extends AbstractWidget {

    public static final int HEIGHT = 16;

    private final List<T> values;
    private final List<Component> labels;
    private final Consumer<T> onChange;
    private int chosen;

    public GlassChoice(int x, int y, Component name, List<T> values, List<Component> labels, T value, Consumer<T> onChange) {
        super(x, y, widthOf(labels), HEIGHT, name);
        this.values = List.copyOf(values);
        this.labels = List.copyOf(labels);
        this.onChange = onChange;
        this.chosen = Math.max(0, values.indexOf(value));
    }

    /** The width the options need side by side. */
    public static int widthOf(List<Component> labels) {
        Font font = Minecraft.getInstance().font;
        return labels.stream().mapToInt(label -> font.width(label) + 14).sum() + 4;
    }

    public T value() {
        return values.get(chosen);
    }

    public void set(T value) {
        chosen = Math.max(0, values.indexOf(value));
    }

    private int optionX(int index) {
        Font font = Minecraft.getInstance().font;
        int x = getX() + 2;
        for (int i = 0; i < index; i++) {
            x += font.width(labels.get(i)) + 14;
        }
        return x;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Font font = Minecraft.getInstance().font;
        Glass.box(graphics, getX(), getY(), width, height, 6, GlassStyle.FIELD, isFocused() ? 0x80FFFFFF : 0x1FFFFFFF);
        for (int i = 0; i < labels.size(); i++) {
            int x = optionX(i);
            int w = font.width(labels.get(i)) + 14;
            boolean hovered = isHovered() && mouseX >= x && mouseX < x + w;
            if (i == chosen) {
                Glass.fill(graphics, x, getY() + 2, w, height - 4, 4, GlassStyle.ACCENT);
            } else if (hovered) {
                Glass.fill(graphics, x, getY() + 2, w, height - 4, 4, 0x1AFFFFFF);
            }
            graphics.text(font, labels.get(i), x + 7, getY() + (height - 8) / 2, i == chosen ? GlassStyle.ON_ACCENT : GlassStyle.LABEL, false);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        for (int i = labels.size() - 1; i >= 0; i--) {
            if (event.x() >= optionX(i)) {
                choose(i);
                return;
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_LEFT || event.key() == GLFW.GLFW_KEY_RIGHT) {
            int step = event.key() == GLFW.GLFW_KEY_LEFT ? -1 : 1;
            choose(Math.floorMod(chosen + step, values.size()));
            return true;
        }
        return super.keyPressed(event);
    }

    private void choose(int index) {
        if (index != chosen) {
            chosen = index;
            onChange.accept(values.get(index));
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, CommonComponents.optionNameValue(getMessage(), labels.get(chosen)));
    }
}
