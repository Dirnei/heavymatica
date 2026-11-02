package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import io.github.dirnei.mc.heavymatica.library.LibraryItem;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.TextFit;
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
 * A scrollable grid of library cards: the way up, folders with their counts, and schematics with thumbnail (or a
 * placeholder), name, version or local mark, and block count.
 */
final class CardGrid extends AbstractWidget {

    private final ThumbnailTextures thumbnails;
    private final Consumer<LibraryEntry> onSelect;
    private final Consumer<String> onOpen;
    private final Predicate<LibraryEntry> available;
    private List<LibraryItem> items = List.of();
    private @Nullable String selected;
    private double scroll;
    private int columns = 1;
    private int cardWidth = LibraryLayout.MIN_CARD;
    private boolean marks;

    /**
     * @param available false for cloud entries that cannot be opened right now (offline and not cached)
     * @param onOpen    receives the folder to open, {@code ""} for the top level
     */
    CardGrid(int x, int y, int width, int height, int columns, int cardWidth, ThumbnailTextures thumbnails, Predicate<LibraryEntry> available,
             Consumer<LibraryEntry> onSelect, Consumer<String> onOpen) {
        super(x, y, width, height, Component.translatable("heavymatica.library.cards"));
        this.columns = columns;
        this.cardWidth = cardWidth;
        this.thumbnails = thumbnails;
        this.available = available;
        this.onSelect = onSelect;
        this.onOpen = onOpen;
    }

    void setItems(List<LibraryItem> items) {
        this.items = items;
        scroll = Math.min(scroll, maxScroll());
    }

    /** Whether cards carry a cloud or local mark: only when cloud and local entries can be told apart. */
    void setMarks(boolean marks) {
        this.marks = marks;
    }

    List<LibraryItem> items() {
        return items;
    }

    void scrollToTop() {
        scroll = 0;
    }

    void setSelected(@Nullable String id) {
        selected = id;
    }

    private int step() {
        return LibraryLayout.CARD_HEIGHT + LibraryLayout.CARD_GAP;
    }

    /** Scrolls so the card at {@code index} is fully visible. */
    void reveal(int index) {
        int top = (index / columns) * step();
        if (top < scroll) {
            scroll = top;
        } else if (top + LibraryLayout.CARD_HEIGHT > scroll + height) {
            scroll = Math.min(maxScroll(), top + LibraryLayout.CARD_HEIGHT - height);
        }
    }

    /** Centre of the card at {@code index}, in screen coordinates. */
    double[] pointOf(int index) {
        return new double[] {cardX(index) + cardWidth / 2.0, cardY(index) + LibraryLayout.CARD_HEIGHT / 2.0};
    }

    private int cardX(int index) {
        return getX() + (index % columns) * (cardWidth + LibraryLayout.CARD_GAP);
    }

    private int cardY(int index) {
        return getY() + (index / columns) * step() - (int) scroll;
    }

    private int maxScroll() {
        int rows = (items.size() + columns - 1) / columns;
        return Math.max(0, rows * step() - LibraryLayout.CARD_GAP - height);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        graphics.enableScissor(getX() - 2, getY(), getX() + width + 2, getY() + height);
        for (int i = 0; i < items.size(); i++) {
            int x = cardX(i);
            int y = cardY(i);
            if (y + LibraryLayout.CARD_HEIGHT < getY() || y > getY() + height) {
                continue;
            }
            boolean hovered = isMouseOver(mouseX, mouseY) && mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + LibraryLayout.CARD_HEIGHT;
            TextFit name = switch (items.get(i)) {
                case LibraryItem.Up up -> folderCard(graphics, font, x, y, hovered, Component.translatable("heavymatica.library.up").getString(), null, true);
                case LibraryItem.Folder folder -> folderCard(graphics, font, x, y, hovered, folder.name(),
                        Component.translatable("heavymatica.library.folder.count", folder.totalCount()).getString(), false);
                case LibraryItem.Entry entry -> entryCard(graphics, font, x, y, hovered, entry.entry());
            };
            if (hovered) {
                Glass.tooltipIfShortened(graphics, font, name, x, y, cardWidth, LibraryLayout.CARD_HEIGHT, mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        int content = maxScroll() + height;
        if (maxScroll() > 0) {
            int thumb = Math.max(12, height * height / content);
            int thumbTop = getY() + (int) Math.round(scroll / maxScroll() * (height - thumb));
            Glass.scrollBar(graphics, getX() + width + 3, getY(), height, thumbTop, thumb, false, true);
        }
    }

    private TextFit folderCard(GuiGraphicsExtractor graphics, Font font, int x, int y, boolean hovered, String name, @Nullable String detail, boolean up) {
        Glass.box(graphics, x, y, cardWidth, LibraryLayout.CARD_HEIGHT, 7, hovered ? GlassStyle.CARD_HOVERED : GlassStyle.CARD, 0x14FFFFFF);
        int iconX = x + cardWidth / 2 - 14;
        int iconY = y + 22;
        if (up) {
            Glass.rim(graphics, iconX + 4, iconY, 20, 20, 10, 1.5, GlassStyle.HINT);
            graphics.text(font, "↑", iconX + 11, iconY + 6, GlassStyle.LABEL, false);
        } else {
            Glass.fill(graphics, iconX, iconY, 12, 6, 2, 0x8C7FD8FF);
            Glass.box(graphics, iconX, iconY + 4, 28, 18, 3, 0x2E7FD8FF, 0xCC7FD8FF);
        }
        TextFit fit = Glass.textCentred(graphics, font, name, x + cardWidth / 2, y + LibraryLayout.CARD_HEIGHT - 30, up ? GlassStyle.LABEL : GlassStyle.TEXT,
                cardWidth - 10);
        if (detail != null) {
            Glass.textCentred(graphics, font, detail, x + cardWidth / 2, y + LibraryLayout.CARD_HEIGHT - 18, GlassStyle.HINT, cardWidth - 10);
        }
        return fit;
    }

    private TextFit entryCard(GuiGraphicsExtractor graphics, Font font, int x, int y, boolean hovered, LibraryEntry entry) {
        boolean usable = available.test(entry);
        boolean chosen = entry.id().equals(selected);
        int fill = chosen ? 0x1F7FD8FF : hovered ? GlassStyle.CARD_HOVERED : GlassStyle.CARD;
        if (chosen) {
            Glass.fill(graphics, x - 2, y - 2, cardWidth + 4, LibraryLayout.CARD_HEIGHT + 4, 9, 0x267FD8FF);
        }
        Glass.box(graphics, x, y, cardWidth, LibraryLayout.CARD_HEIGHT, 7, fill, chosen ? GlassStyle.ACCENT : 0x14FFFFFF);
        int wellX = x + 5;
        int wellY = y + 5;
        int wellW = cardWidth - 10;
        int wellH = 54;
        Glass.fill(graphics, wellX, wellY, wellW, wellH, 5, GlassStyle.WELL);
        int size = Math.min(wellH - 4, wellW - 4);
        int tx = wellX + (wellW - size) / 2;
        int ty = wellY + (wellH - size) / 2;
        thumbnails.get(entry).ifPresentOrElse(
                texture -> graphics.blit(RenderPipelines.GUI_TEXTURED, texture, tx, ty, 0, 0, size, size, 256, 256, 256, 256),
                () -> placeholder(graphics, wellX + wellW / 2, wellY + wellH / 2));
        if (marks) {
            mark(graphics, x + cardWidth - 18, y + 8, entry.cloud());
        }
        int text = usable ? GlassStyle.TEXT : GlassStyle.SUBTITLE;
        TextFit fit = Glass.text(graphics, font, entry.name(), x + 6, y + 64, text, cardWidth - 12);
        String meta = (entry.cloud() ? "v" + entry.version() : Component.translatable("heavymatica.library.card.local").getString())
                + " · " + Component.translatable("heavymatica.library.card.blocks", entry.blockTotal()).getString();
        if (!usable) {
            meta = Component.translatable("heavymatica.library.card.offline").getString();
        }
        Glass.text(graphics, font, meta, x + 6, y + 76, GlassStyle.HINT, cardWidth - 12);
        return fit;
    }

    /** A small iso block outline standing in for a missing thumbnail. */
    static void placeholder(GuiGraphicsExtractor graphics, int cx, int cy) {
        Glass.box(graphics, cx - 9, cy - 9, 18, 18, 3, 0x147FD8FF, 0x667FD8FF);
    }

    /** A cloud or computer mark in a small dark square. */
    static void mark(GuiGraphicsExtractor graphics, int x, int y, boolean cloud) {
        Glass.fill(graphics, x, y, 13, 13, 3, 0xC7080B0F);
        if (cloud) {
            Glass.fill(graphics, x + 3, y + 5, 8, 4, 2, GlassStyle.ACCENT);
            Glass.fill(graphics, x + 5, y + 3, 4, 4, 2, GlassStyle.ACCENT);
        } else {
            Glass.rim(graphics, x + 3, y + 3, 7, 5, 1, 1, GlassStyle.LABEL);
            graphics.fill(x + 5, y + 9, x + 8, y + 10, GlassStyle.LABEL);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        for (int i = 0; i < items.size(); i++) {
            int x = cardX(i);
            int y = cardY(i);
            if (event.x() >= x && event.x() < x + cardWidth && event.y() >= y && event.y() < y + LibraryLayout.CARD_HEIGHT) {
                switch (items.get(i)) {
                    case LibraryItem.Up up -> onOpen.accept(up.parent());
                    case LibraryItem.Folder folder -> onOpen.accept(folder.path());
                    case LibraryItem.Entry entry -> onSelect.accept(entry.entry());
                }
                return;
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
