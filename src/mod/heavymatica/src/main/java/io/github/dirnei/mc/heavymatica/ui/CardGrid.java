package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** A scrollable grid of library cards: thumbnail (or placeholder), name, dimensions and block count. */
final class CardGrid extends AbstractWidget {

    static final int CARD_WIDTH = 88;
    static final int CARD_HEIGHT = 100;
    private static final int GAP = 6;
    private static final int THUMBNAIL = 64;

    private final ThumbnailTextures thumbnails;
    private final Consumer<LibraryEntry> onSelect;
    private final Predicate<LibraryEntry> available;
    private List<LibraryEntry> entries = List.of();
    private @Nullable String selected;
    private double scroll;

    /** @param available false for cloud entries that cannot be opened right now (offline and not cached) */
    CardGrid(int x, int y, int width, int height, ThumbnailTextures thumbnails, Predicate<LibraryEntry> available, Consumer<LibraryEntry> onSelect) {
        super(x, y, width, height, Component.translatable("heavymatica.library.cards"));
        this.thumbnails = thumbnails;
        this.available = available;
        this.onSelect = onSelect;
    }

    void setEntries(List<LibraryEntry> entries) {
        this.entries = entries;
        scroll = Math.min(scroll, maxScroll());
    }

    void setSelected(@Nullable String id) {
        selected = id;
    }

    /** Scrolls so the card at {@code index} is fully visible. */
    void reveal(int index) {
        int top = (index / columns()) * (CARD_HEIGHT + GAP);
        if (top < scroll) {
            scroll = top;
        } else if (top + CARD_HEIGHT > scroll + height) {
            scroll = Math.min(maxScroll(), top + CARD_HEIGHT - height);
        }
    }

    /** Centre of the card at {@code index}, in screen coordinates. */
    double[] pointOf(int index) {
        int columns = columns();
        return new double[] {getX() + (index % columns) * (CARD_WIDTH + GAP) + CARD_WIDTH / 2.0,
                getY() + (index / columns) * (CARD_HEIGHT + GAP) - scroll + CARD_HEIGHT / 2.0};
    }

    private int columns() {
        return Math.max(1, (width + GAP) / (CARD_WIDTH + GAP));
    }

    private int maxScroll() {
        int rows = (entries.size() + columns() - 1) / columns();
        return Math.max(0, rows * (CARD_HEIGHT + GAP) - height);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
        int columns = columns();
        for (int i = 0; i < entries.size(); i++) {
            LibraryEntry entry = entries.get(i);
            int x = getX() + (i % columns) * (CARD_WIDTH + GAP);
            int y = getY() + (i / columns) * (CARD_HEIGHT + GAP) - (int) scroll;
            if (y + CARD_HEIGHT < getY() || y > getY() + height) {
                continue;
            }
            boolean hovered = mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= y && mouseY < y + CARD_HEIGHT && isMouseOver(mouseX, mouseY);
            boolean usable = available.test(entry);
            graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, entry.id().equals(selected) ? 0xC0305080 : hovered ? 0xA0303040 : 0x80202028);
            graphics.outline(x, y, CARD_WIDTH, CARD_HEIGHT, entry.id().equals(selected) ? 0xFF80B0FF : 0x40FFFFFF);

            int tx = x + (CARD_WIDTH - THUMBNAIL) / 2;
            int ty = y + 4;
            thumbnails.get(entry).ifPresentOrElse(
                    texture -> graphics.blit(RenderPipelines.GUI_TEXTURED, texture, tx, ty, 0, 0, THUMBNAIL, THUMBNAIL, 256, 256, 256, 256),
                    () -> {
                        graphics.fill(tx, ty, tx + THUMBNAIL, ty + THUMBNAIL, 0x60000000);
                        graphics.centeredText(font, "?", tx + THUMBNAIL / 2, ty + THUMBNAIL / 2 - 4, 0xFF808080);
                    });
            if (!entry.cloud()) {
                badge(graphics, font, x + 2, y + 2, "LOCAL", 0xFFE0A030);
            } else if (!usable) {
                badge(graphics, font, x + 2, y + 2, "OFFLINE", 0xFF909090);
            }

            int text = usable ? 0xFFFFFFFF : 0xFF808080;
            graphics.centeredText(font, ellipsize(font, entry.name(), CARD_WIDTH - 6), x + CARD_WIDTH / 2, y + 72, text);
            graphics.centeredText(font, entry.dimensions(), x + CARD_WIDTH / 2, y + 82, 0xFFA0A0A0);
            graphics.centeredText(font, entry.blockTotal() + " blocks", x + CARD_WIDTH / 2, y + 91, 0xFFA0A0A0);
        }
        graphics.disableScissor();
    }

    private static void badge(GuiGraphicsExtractor graphics, Font font, int x, int y, String text, int color) {
        graphics.fill(x, y, x + font.width(text) + 4, y + 10, 0xC0000000);
        graphics.text(font, text, x + 2, y + 1, color, false);
    }

    static String ellipsize(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String shortened = text;
        while (!shortened.isEmpty() && font.width(shortened + "...") > width) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "...";
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int columns = columns();
        int column = (int) ((event.x() - getX()) / (CARD_WIDTH + GAP));
        int row = (int) ((event.y() - getY() + scroll) / (CARD_HEIGHT + GAP));
        double inCardX = (event.x() - getX()) % (CARD_WIDTH + GAP);
        double inCardY = (event.y() - getY() + scroll) % (CARD_HEIGHT + GAP);
        int index = row * columns + column;
        if (column < columns && inCardX < CARD_WIDTH && inCardY < CARD_HEIGHT && index >= 0 && index < entries.size()) {
            onSelect.accept(entries.get(index));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - scrollY * 24));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("heavymatica.library.cards.narration", entries.size()));
    }
}
