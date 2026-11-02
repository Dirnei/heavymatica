package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.cloud.model.SchematicDetails;
import io.github.dirnei.mc.heavymatica.cloud.model.Version;
import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import io.github.dirnei.mc.heavymatica.ui.glass.TextFit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The selected entry beside the grid: preview, name, kind, size and block count, the versions of a cloud schematic or
 * the upload offer of a local one, and every block type in a scrolling list. The screen places its buttons in the
 * {@link Slots} this panel lays out.
 */
final class DetailsPanel extends AbstractWidget {

    static final int PAD = 10;
    private static final int ROW = 12;
    private static final int VERSION_ROW = 14;
    private static final int MAX_VERSION_ROWS = 4;
    private static final int UPLOAD_BOX = 52;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    /** Where the screen puts the panel's buttons; {@code upload} is null when the panel offers no upload. */
    record Slots(Rect allDetails, Rect place, List<Rect> actions, @Nullable Rect upload) {
    }

    private final IntConsumer onVersion;
    private final ThumbnailTextures thumbnails;
    private @Nullable LibraryEntry entry;
    private @Nullable SchematicDetails details;
    private int version;
    private @Nullable Component status;
    private EntryFacts.@Nullable Facts facts;
    private boolean uploadOffered;
    private int actionCount;
    private int actionColumns = 3;
    private double listScroll;
    private int versionScroll;
    private Rect listArea = new Rect(0, 0, 0, 0);
    private Rect versionArea = new Rect(0, 0, 0, 0);

    DetailsPanel(Rect area, ThumbnailTextures thumbnails, IntConsumer onVersion) {
        super(area.x(), area.y(), area.w(), area.h(), Component.translatable("heavymatica.library.details"));
        this.thumbnails = thumbnails;
        this.onVersion = onVersion;
    }

    /**
     * @param uploadOffered whether a local entry offers upload here
     * @param actionCount   how many action buttons sit at the bottom, three per row
     */
    void show(@Nullable LibraryEntry entry, @Nullable SchematicDetails details, int version, @Nullable Component status,
              EntryFacts.@Nullable Facts facts, boolean uploadOffered, int actionCount, int widestLabel) {
        if (entry == null || this.entry == null || !entry.id().equals(this.entry.id())) {
            listScroll = 0;
            versionScroll = 0;
        }
        this.entry = entry;
        this.details = details;
        this.version = version;
        this.status = status;
        this.facts = facts;
        this.uploadOffered = uploadOffered && entry != null && !entry.cloud();
        this.actionCount = actionCount;
        int threeAcross = (width - 2 * PAD - 8) / 3;
        this.actionColumns = widestLabel + 10 <= threeAcross ? 3 : 2;
    }

    int version() {
        return version;
    }

    /** No preview in a short panel: the selected card shows the thumbnail already, and the block list needs the room. */
    private int previewHeight() {
        return height < 300 ? 0 : Math.clamp(height / 5, 40, 86);
    }

    private int actionRows() {
        return (actionCount + actionColumns - 1) / actionColumns;
    }

    private int bottomBlock() {
        return 20 + 6 + actionRows() * 22;
    }

    private int nameY() {
        int preview = previewHeight();
        return getY() + PAD + (preview > 0 ? preview + 8 : 2);
    }

    /** Where the buttons go for what the panel shows now. */
    Slots slots() {
        Font font = Minecraft.getInstance().font;
        int inner = width - 2 * PAD;
        String link = Component.translatable("heavymatica.library.all_details").getString();
        int linkWidth = font.width(link) + 8;
        Rect allDetails = new Rect(getX() + width - PAD - linkWidth, nameY() - 3, linkWidth, 14);
        int bottom = getY() + height - PAD;
        int rows = actionRows();
        List<Rect> actions = new ArrayList<>();
        int buttonWidth = (inner - (actionColumns - 1) * 4) / actionColumns;
        int top = bottom - rows * 22 + 4;
        for (int i = 0; i < actionCount; i++) {
            actions.add(new Rect(getX() + PAD + (i % actionColumns) * (buttonWidth + 4), top + (i / actionColumns) * 22, buttonWidth, 18));
        }
        Rect place = new Rect(getX() + PAD, top - 26, inner, 20);
        Rect upload = uploadOffered ? new Rect(getX() + PAD + 8, uploadBoxY() + UPLOAD_BOX - 24, 64, 16) : null;
        return new Slots(allDetails, place, actions, upload);
    }

    private int chipsY() {
        return nameY() + 13;
    }

    private int afterChips() {
        return chipsY() + 13 + 4 + (status != null ? 11 : 0);
    }

    private int uploadBoxY() {
        return afterChips() + 2;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        Glass.panel(graphics, getX(), getY(), width, height, Glass.RADIUS, 0xDB1C232C, 0xD60E1218, 0x1AFFFFFF);
        int x = getX() + PAD;
        int inner = width - 2 * PAD;
        if (entry == null) {
            Glass.paragraph(graphics, font, Component.translatable("heavymatica.library.details.none"), x, getY() + PAD + 4, inner, GlassStyle.HINT);
            return;
        }
        int previewH = previewHeight();
        if (previewH > 0) {
            Glass.fill(graphics, x, getY() + PAD, inner, previewH, 7, GlassStyle.WELL);
            Glass.fill(graphics, x + inner / 2 - previewH, getY() + PAD + 4, previewH * 2, previewH - 8, previewH / 2.0, 0x0F7FD8FF);
            int size = previewH - 8;
            int px = x + (inner - size) / 2;
            int py = getY() + PAD + 4;
            thumbnails.get(entry).ifPresentOrElse(
                    texture -> graphics.blit(RenderPipelines.GUI_TEXTURED, texture, px, py, 0, 0, size, size, 256, 256, 256, 256),
                    () -> CardGrid.placeholder(graphics, x + inner / 2, getY() + PAD + previewH / 2));
        }

        Slots slots = slots();
        TextFit name = Glass.text(graphics, font, entry.name(), x, nameY(), GlassStyle.TEXT, slots.allDetails().x() - x - 4);
        Glass.tooltipIfShortened(graphics, font, name, x, nameY(), slots.allDetails().x() - x, 10, mouseX, mouseY);
        int chipX = x;
        Version shown = shownVersion();
        String kind = entry.cloud() ? Component.translatable("heavymatica.library.chip.cloud", "v" + version).getString()
                : Component.translatable("heavymatica.library.chip.local").getString();
        chipX += Glass.chip(graphics, font, kind, chipX, chipsY(), 0x147FD8FF, entry.cloud() ? GlassStyle.ACCENT : GlassStyle.LABEL,
                entry.cloud() ? 0x8C7FD8FF : 0x4DFFFFFF) + 4;
        String dims = shown == null ? entry.dimensions().replace(" x ", " × ")
                : shown.getDimensions().getWidth() + " × " + shown.getDimensions().getHeight() + " × " + shown.getDimensions().getLength();
        long blocks = shown == null ? entry.blockTotal() : shown.getBlockTotal();
        String blocksText = Component.translatable("heavymatica.library.card.blocks", blocks).getString();
        if (chipX + Glass.chipWidth(font, dims) <= x + inner) {
            chipX += Glass.chip(graphics, font, dims, chipX, chipsY(), 0x12FFFFFF, GlassStyle.LABEL, 0) + 4;
        }
        if (chipX + Glass.chipWidth(font, blocksText) <= x + inner) {
            Glass.chip(graphics, font, blocksText, chipX, chipsY(), 0x12FFFFFF, GlassStyle.LABEL, 0);
        }
        int y = chipsY() + 17;
        if (status != null) {
            Glass.text(graphics, font, status.getString(), x, y, GlassStyle.WARNING, inner);
            y += 11;
        }
        y += 2;
        if (uploadOffered) {
            Glass.box(graphics, x, y, inner, UPLOAD_BOX, 7, 0x0F7FD8FF, 0x527FD8FF);
            Glass.text(graphics, font, Component.translatable("heavymatica.library.upload_box.title").getString(), x + 8, y + 6, GlassStyle.TEXT, inner - 16);
            Glass.text(graphics, font, Component.translatable("heavymatica.library.upload_box.hint").getString(), x + 8, y + 17, GlassStyle.HINT, inner - 16);
            y += UPLOAD_BOX + 6;
        } else if (entry.cloud() && details != null) {
            List<Version> versions = versions();
            Glass.text(graphics, font, Component.translatable("heavymatica.library.details.versions_count", versions.size()).getString().toUpperCase(java.util.Locale.ROOT),
                    x, y, GlassStyle.HINT, inner);
            y += 11;
            int rows = Math.min(MAX_VERSION_ROWS, versions.size());
            versionArea = new Rect(x, y, inner, rows * VERSION_ROW);
            versionScroll = Math.clamp(versionScroll, 0, Math.max(0, versions.size() - rows));
            for (int i = 0; i < rows; i++) {
                Version candidate = versions.get(i + versionScroll);
                int rowY = y + i * VERSION_ROW;
                boolean chosen = candidate.getNumber() == version;
                boolean hovered = versionArea.contains(mouseX, mouseY) && mouseY >= rowY && mouseY < rowY + VERSION_ROW - 1;
                if (chosen || hovered) {
                    Glass.box(graphics, x, rowY, inner, VERSION_ROW - 2, 4, chosen ? 0x1F7FD8FF : 0x0DFFFFFF, chosen ? 0x807FD8FF : 0);
                }
                graphics.text(font, "v" + candidate.getNumber(), x + 5, rowY + 2, chosen ? GlassStyle.ACCENT : GlassStyle.TEXT, false);
                Glass.text(graphics, font, WHEN.format(candidate.getCreatedAt()), x + 28, rowY + 2, GlassStyle.LABEL, inner - 80);
                String note = candidate.getNumber() == entry.version() ? Component.translatable("heavymatica.library.version.latest").getString() : "";
                Glass.textRight(graphics, font, note, x + inner - 5, rowY + 2, GlassStyle.HINT);
            }
            y += rows * VERSION_ROW + 4;
        } else {
            versionArea = new Rect(0, 0, 0, 0);
        }

        int listBottom = slots.place().y() - 6;
        if (facts == null) {
            Glass.paragraph(graphics, font, Component.translatable(entry.cloud() ? "heavymatica.library.details.blocks_cloud"
                    : "heavymatica.library.details.blocks_pending"), x, y, inner, GlassStyle.SUBTITLE);
            listArea = new Rect(0, 0, 0, 0);
            return;
        }
        DetailBlockCounts counts = facts.counts();
        String label = Component.translatable("heavymatica.library.details.types", counts.rows().size()).getString().toUpperCase(java.util.Locale.ROOT);
        Glass.text(graphics, font, label, x, y, GlassStyle.HINT, inner);
        if (counts.unknownTypes() > 0) {
            Glass.textRight(graphics, font, Component.translatable("heavymatica.library.details.unknown", counts.unknownTypes()).getString(), x + inner, y,
                    GlassStyle.DANGER_TEXT);
        }
        y += 12;
        listArea = new Rect(x, y, inner, Math.max(0, listBottom - y));
        blockList(graphics, font, counts, listArea, mouseX, mouseY);
    }

    private void blockList(GuiGraphicsExtractor graphics, Font font, DetailBlockCounts counts, Rect area, int mouseX, int mouseY) {
        if (area.h() < ROW) {
            return;
        }
        int content = counts.rows().size() * ROW;
        listScroll = Math.clamp(listScroll, 0, Math.max(0, content - area.h()));
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        int y = area.y() - (int) listScroll;
        int textWidth = area.w() - 6;
        for (DetailBlockCounts.Row row : counts.rows()) {
            if (y + ROW >= area.y() && y <= area.bottom()) {
                blockRow(graphics, font, row.block(), row.known(), row.count(), area.x(), y, textWidth, mouseX, mouseY);
            }
            y += ROW;
        }
        graphics.disableScissor();
        if (content > area.h()) {
            int thumb = Math.max(10, area.h() * area.h() / content);
            int thumbTop = area.y() + (int) Math.round(listScroll / (content - area.h()) * (area.h() - thumb));
            Glass.scrollBar(graphics, area.right() - 2, area.y(), area.h(), thumbTop, thumb, false, true);
        }
    }

    /** One block with its small icon, name and count; unknown blocks by ID in the danger colour. */
    static void blockRow(GuiGraphicsExtractor graphics, Font font, String blockId, boolean known, long count, int x, int y, int width, int mouseX,
                         int mouseY) {
        Block block = known ? MaterialsScreen.block(blockId) : null;
        Item item = block == null ? Items.AIR : block.asItem();
        if (item != Items.AIR) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(0.625f, 0.625f);
            graphics.item(item.getDefaultInstance(), 0, 0);
            graphics.pose().popMatrix();
        } else {
            Glass.rim(graphics, x + 1, y + 1, 8, 8, 2, 1, known ? GlassStyle.SUBTITLE : GlassStyle.DANGER_TEXT);
        }
        String countText = Long.toString(count);
        String name = block != null ? block.getName().getString() : blockId;
        TextFit fit = Glass.text(graphics, font, name, x + 13, y + 1, known ? GlassStyle.TEXT : GlassStyle.DANGER_TEXT, width - 13 - font.width(countText) - 6);
        Glass.textRight(graphics, font, countText, x + width, y + 1, GlassStyle.LABEL);
        Glass.tooltipIfShortened(graphics, font, fit, x, y, width, ROW, mouseX, mouseY);
    }

    private List<Version> versions() {
        return details == null ? List.of() : details.getVersions().stream().sorted(Comparator.comparingInt(Version::getNumber).reversed()).toList();
    }

    @Nullable Version shownVersion() {
        return versions().stream().filter(candidate -> candidate.getNumber() == version).findFirst().orElse(null);
    }

    /** Centre of the version row at {@code index}, in screen coordinates. */
    double[] versionPoint(int index) {
        return new double[] {versionArea.x() + 30, versionArea.y() + (index - versionScroll) * VERSION_ROW + VERSION_ROW / 2.0};
    }

    /**
     * The screen hands clicks and scrolling to the first child under the mouse, so the panel only counts as under it
     * on its version rows and block list; the buttons the screen places on the panel get the rest.
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && (versionArea.contains(mouseX, mouseY) || listArea.contains(mouseX, mouseY));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!visible || !versionArea.contains(event.x(), event.y())) {
            return false;
        }
        onClick(event, doubleClick);
        return true;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!versionArea.contains(event.x(), event.y())) {
            return;
        }
        int index = (int) ((event.y() - versionArea.y()) / VERSION_ROW) + versionScroll;
        List<Version> versions = versions();
        if (index >= 0 && index < versions.size()) {
            onVersion.accept(versions.get(index).getNumber());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (versionArea.contains(mouseX, mouseY)) {
            versionScroll -= (int) Math.signum(scrollY);
            return true;
        }
        if (listArea.contains(mouseX, mouseY)) {
            listScroll -= scrollY * ROW * 2;
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, entry == null ? getMessage() : Component.literal(entry.name()));
    }
}
