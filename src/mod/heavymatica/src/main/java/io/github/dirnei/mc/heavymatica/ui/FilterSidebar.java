package io.github.dirnei.mc.heavymatica.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Folders and tags to filter by. Clicking a selected row clears that filter. */
final class FilterSidebar extends AbstractWidget {

    private static final int ROW = 12;

    private record Row(String label, @Nullable String folder, @Nullable String tag, boolean heading) {
    }

    private final BiConsumer<String, String> onChange;
    private final List<Row> rows = new ArrayList<>();
    private @Nullable String folder;
    private @Nullable String tag;
    private double scroll;

    /** @param onChange receives the selected folder and tag, each null when not filtering */
    FilterSidebar(int x, int y, int width, int height, BiConsumer<String, String> onChange) {
        super(x, y, width, height, Component.translatable("heavymatica.library.filters"));
        this.onChange = onChange;
    }

    void setOptions(List<String> folders, List<String> tags) {
        rows.clear();
        rows.add(new Row(Component.translatable("heavymatica.library.folders").getString(), null, null, true));
        folders.forEach(value -> rows.add(new Row(value, value, null, false)));
        rows.add(new Row(Component.translatable("heavymatica.library.tags").getString(), null, null, true));
        tags.forEach(value -> rows.add(new Row("#" + value, null, value, false)));
        if (folder != null && !folders.contains(folder)) {
            folder = null;
        }
        if (tag != null && !tags.contains(tag)) {
            tag = null;
        }
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
            boolean active = row.folder() != null && row.folder().equals(folder) || row.tag() != null && row.tag().equals(tag);
            if (active) {
                graphics.fill(getX() + 2, y - 2, getX() + width - 2, y + ROW - 2, 0xA0305080);
            }
            int color = row.heading() ? 0xFFFFD060 : active ? 0xFFFFFFFF : 0xFFC0C0C0;
            graphics.text(font, CardGrid.ellipsize(font, row.label(), width - 10), getX() + (row.heading() ? 4 : 8), y, color, false);
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
        if (row.folder() != null) {
            folder = Objects.equals(folder, row.folder()) ? null : row.folder();
        } else {
            tag = Objects.equals(tag, row.tag()) ? null : row.tag();
        }
        onChange.accept(folder, tag);
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
