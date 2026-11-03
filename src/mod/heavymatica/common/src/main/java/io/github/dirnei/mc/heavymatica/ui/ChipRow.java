package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A row of chips: path segments, separators and filter chips side by side. The row scrolls sideways with the mouse
 * wheel when it is wider than its space.
 */
final class ChipRow extends AbstractWidget {

    enum Kind {
        /** A folder of the path: plain text, clickable. */
        PATH,
        /** The arrow between path segments. */
        ARROW,
        /** A gap with a thin line. */
        DIVIDER,
        FILTER,
    }

    record Chip(Kind kind, String label, boolean selected, @Nullable Runnable action) {

        static Chip path(String label, boolean current, Runnable action) {
            return new Chip(Kind.PATH, label, current, action);
        }

        static Chip arrow() {
            return new Chip(Kind.ARROW, "›", false, null);
        }

        static Chip divider() {
            return new Chip(Kind.DIVIDER, "", false, null);
        }

        static Chip filter(String label, boolean selected, Runnable action) {
            return new Chip(Kind.FILTER, label, selected, action);
        }
    }

    private List<Chip> chips = List.of();
    private double scroll;

    ChipRow(int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("heavymatica.library.filters"));
    }

    void setChips(List<Chip> chips) {
        this.chips = chips;
        scroll = Math.min(scroll, maxScroll());
    }

    private static int widthOf(Font font, Chip chip) {
        return switch (chip.kind()) {
            case PATH -> font.width(chip.label()) + 2;
            case ARROW -> font.width(chip.label()) + 2;
            case DIVIDER -> 9;
            case FILTER -> Glass.chipWidth(font, chip.label());
        };
    }

    private int contentWidth(Font font) {
        int total = 0;
        for (Chip chip : chips) {
            total += widthOf(font, chip) + 4;
        }
        return total;
    }

    private int maxScroll() {
        return Math.max(0, contentWidth(Minecraft.getInstance().font) - width);
    }

    /** Centre of the first chip with this label, in screen coordinates; or the row's start when there is none. */
    double[] pointOf(String label) {
        Font font = Minecraft.getInstance().font;
        int x = getX() - (int) scroll;
        for (Chip chip : chips) {
            int w = widthOf(font, chip);
            if (chip.label().equals(label)) {
                return new double[] {x + w / 2.0, getY() + height / 2.0};
            }
            x += w + 4;
        }
        return new double[] {getX(), getY()};
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        graphics.enableScissor(getX(), getY() - 1, getX() + width, getY() + height + 1);
        int x = getX() - (int) scroll;
        int textY = getY() + (height - 8) / 2;
        for (Chip chip : chips) {
            int w = widthOf(font, chip);
            boolean hovered = chip.action() != null && isMouseOver(mouseX, mouseY) && mouseX >= x && mouseX < x + w;
            switch (chip.kind()) {
                case PATH -> graphics.text(font, chip.label(), x + 1, textY, chip.selected() ? GlassStyle.TEXT : hovered ? GlassStyle.LABEL : GlassStyle.HINT,
                        false);
                case ARROW -> graphics.text(font, chip.label(), x + 1, textY, GlassStyle.SUBTITLE, false);
                case DIVIDER -> graphics.fill(x + 4, getY() + 2, x + 5, getY() + height - 2, 0x24FFFFFF);
                case FILTER -> {
                    int fill = chip.selected() ? 0x247FD8FF : hovered ? 0x1AFFFFFF : 0x0DFFFFFF;
                    int rim = chip.selected() ? 0x8C7FD8FF : 0x1FFFFFFF;
                    Glass.chip(graphics, font, chip.label(), x, getY() + (height - 13) / 2, fill, chip.selected() ? GlassStyle.ACCENT : GlassStyle.LABEL, rim);
                }
            }
            x += w + 4;
        }
        graphics.disableScissor();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        Font font = Minecraft.getInstance().font;
        int x = getX() - (int) scroll;
        for (Chip chip : chips) {
            int w = widthOf(font, chip);
            if (event.x() >= x && event.x() < x + w) {
                if (chip.action() != null) {
                    chip.action().run();
                }
                return;
            }
            x += w + 4;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || maxScroll() == 0) {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (scrollY + scrollX) * 20));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
