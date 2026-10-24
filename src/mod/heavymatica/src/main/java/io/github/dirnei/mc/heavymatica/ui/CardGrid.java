package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import io.github.dirnei.mc.heavymatica.library.LibraryItem;
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

/**
 * A scrollable grid of library cards: the way up, folders (name and count), and schematics (thumbnail or placeholder,
 * name, dimensions and block count).
 */
final class CardGrid extends AbstractWidget {

    static final int CARD_WIDTH = 88;
    static final int CARD_HEIGHT = 100;
    private static final int GAP = 6;
    private static final int THUMBNAIL = 64;
    private static final int FOLDER_COLOR = 0xFFD8A848;
    private static final int FOLDER_TAB_COLOR = 0xFFB88830;

    private final ThumbnailTextures thumbnails;
    private final Consumer<LibraryEntry> onSelect;
    private final Consumer<String> onOpen;
    private final Predicate<LibraryEntry> available;
    private List<LibraryItem> items = List.of();
    private @Nullable String selected;
    private double scroll;

    /**
     * @param available false for cloud entries that cannot be opened right now (offline and not cached)
     * @param onOpen    receives the folder to open, {@code ""} for the top level
     */
    CardGrid(int x, int y, int width, int height, ThumbnailTextures thumbnails, Predicate<LibraryEntry> available, Consumer<LibraryEntry> onSelect,
             Consumer<String> onOpen) {
        super(x, y, width, height, Component.translatable("heavymatica.library.cards"));
        this.thumbnails = thumbnails;
        this.available = available;
        this.onSelect = onSelect;
        this.onOpen = onOpen;
    }

    void setItems(List<LibraryItem> items) {
        this.items = items;
        scroll = Math.min(scroll, maxScroll());
    }

    void scrollToTop() {
        scroll = 0;
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
        int rows = (items.size() + columns() - 1) / columns();
        return Math.max(0, rows * (CARD_HEIGHT + GAP) - height);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
        int columns = columns();
        for (int i = 0; i < items.size(); i++) {
            int x = getX() + (i % columns) * (CARD_WIDTH + GAP);
            int y = getY() + (i / columns) * (CARD_HEIGHT + GAP) - (int) scroll;
            if (y + CARD_HEIGHT < getY() || y > getY() + height) {
                continue;
            }
            boolean hovered = mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= y && mouseY < y + CARD_HEIGHT && isMouseOver(mouseX, mouseY);
            switch (items.get(i)) {
                case LibraryItem.Up up -> drawFolderCard(graphics, font, x, y, hovered, "..", Component.translatable("heavymatica.library.up").getString());
                case LibraryItem.Folder folder -> drawFolderCard(graphics, font, x, y, hovered, folder.name(),
                        Component.translatable("heavymatica.library.folder.count", folder.totalCount()).getString());
                case LibraryItem.Entry entry -> drawEntryCard(graphics, font, x, y, hovered, entry.entry());
            }
        }
        graphics.disableScissor();
    }

    private static void drawFolderCard(GuiGraphicsExtractor graphics, Font font, int x, int y, boolean hovered, String name, String detail) {
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, hovered ? 0xA0303040 : 0x80202028);
        graphics.outline(x, y, CARD_WIDTH, CARD_HEIGHT, 0x40FFFFFF);
        int fx = x + (CARD_WIDTH - 56) / 2;
        int fy = y + 18;
        graphics.fill(fx, fy, fx + 22, fy + 8, FOLDER_TAB_COLOR);
        graphics.fill(fx, fy + 6, fx + 56, fy + 44, FOLDER_COLOR);
        graphics.fill(fx, fy + 6, fx + 56, fy + 9, FOLDER_TAB_COLOR);
        graphics.centeredText(font, ellipsize(font, name, CARD_WIDTH - 6), x + CARD_WIDTH / 2, y + 72, 0xFFFFFFFF);
        graphics.centeredText(font, detail, x + CARD_WIDTH / 2, y + 84, 0xFFA0A0A0);
    }

    private void drawEntryCard(GuiGraphicsExtractor graphics, Font font, int x, int y, boolean hovered, LibraryEntry entry) {
        boolean usable = available.test(entry);
        boolean chosen = entry.id().equals(selected);
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, chosen ? 0xC0305080 : hovered ? 0xA0303040 : 0x80202028);
        graphics.outline(x, y, CARD_WIDTH, CARD_HEIGHT, chosen ? 0xFF80B0FF : 0x40FFFFFF);

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
        if (column < columns && inCardX < CARD_WIDTH && inCardY < CARD_HEIGHT && index >= 0 && index < items.size()) {
            switch (items.get(index)) {
                case LibraryItem.Up up -> onOpen.accept(up.parent());
                case LibraryItem.Folder folder -> onOpen.accept(folder.path());
                case LibraryItem.Entry entry -> onSelect.accept(entry.entry());
            }
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
        output.add(NarratedElementType.TITLE, Component.translatable("heavymatica.library.cards.narration", items.size()));
    }
}
