package io.github.dirnei.mc.heavymatica.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** The "All schematics" switch and the tags to filter by. Clicking a selected row turns it off again. */
final class FilterSidebar extends AbstractWidget {

    private static final int ROW = 12;

    /** Receives whether the flat view of everything is on and the selected tag, null when not filtering. */
    interface Listener {
        void changed(boolean all, @Nullable String tag);
    }

    private record Row(String label, boolean allRow, @Nullable String tag, boolean heading) {
    }

    private final Listener onChange;
    private final List<Row> rows = new ArrayList<>();
    private boolean all;
    private @Nullable String tag;
    private double scroll;

    FilterSidebar(int x, int y, int width, int height, Listener onChange) {
        super(x, y, width, height, Component.translatable("heavymatica.library.filters"));
        this.onChange = onChange;
    }

    void setOptions(List<String> tags) {
        rows.clear();
        rows.add(new Row(Component.translatable("heavymatica.library.all").getString(), true, null, false));
        rows.add(new Row(Component.translatable("heavymatica.library.tags").getString(), false, null, true));
        tags.forEach(value -> rows.add(new Row("#" + value, false, value, false)));
        if (tag != null && !tags.contains(tag)) {
            tag = null;
        }
    }

    void setAll(boolean all) {
        this.all = all;
    }

    /** Centre of the row with this label, in screen coordinates. */
    double[] pointOf(String label) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).label().equals(label)) {
                return new double[] {getX() + width / 2.0, getY() + 4 + i * ROW - scroll + 3};
            }
        }
        throw new IllegalArgumentException("No sidebar row " + label);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x60000000);
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = getY() + 4 + i * ROW - (int) scroll;
            boolean active = row.allRow() && all || row.tag() != null && row.tag().equals(tag);
            if (active) {
                graphics.fill(getX() + 2, y - 2, getX() + width - 2, y + ROW - 2, 0xA0305080);
            }
            int color = row.heading() ? 0xFFFFD060 : active ? 0xFFFFFFFF : 0xFFC0C0C0;
            graphics.text(font, CardGrid.ellipsize(font, row.label(), width - 10), getX() + (row.heading() || row.allRow() ? 4 : 8), y, color, false);
        }
        graphics.disableScissor();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int index = (int) ((event.y() - getY() - 4 + scroll + 2) / ROW);
        if (index < 0 || index >= rows.size() || rows.get(index).heading()) {
            return;
        }
        Row row = rows.get(index);
        if (row.allRow()) {
            all = !all;
        } else {
            tag = Objects.equals(tag, row.tag()) ? null : row.tag();
        }
        onChange.changed(all, tag);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() * ROW + 8 - height), scroll - scrollY * ROW));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
