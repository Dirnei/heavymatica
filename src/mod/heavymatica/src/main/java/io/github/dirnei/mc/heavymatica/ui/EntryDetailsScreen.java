package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.cloud.model.Version;
import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassEditBox;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import io.github.dirnei.mc.heavymatica.ui.glass.TextFit;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * All the details of one library version on a page of its own: a large preview with its size, facts the file records,
 * the entry's actions, and the full block list with a filter and a bar for each block's share. Back returns to the
 * library with the entry still selected.
 */
final class EntryDetailsScreen extends GlassScreen {

    /** The library's actions on its selection, and whether each is available now. */
    record Actions(Runnable place, Runnable export, Runnable rename, Runnable delete, BooleanSupplier canPlace, BooleanSupplier canExport,
                   BooleanSupplier canRename, BooleanSupplier canDelete) {
    }

    private static final int ROW = 16;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final LibraryScreen parent;
    private final LibraryEntry entry;
    private final int version;
    private final String sha256;
    private final @Nullable Version cloudVersion;
    private final EntryFacts facts;
    private final ThumbnailTextures thumbnails;
    private final Actions actions;
    private EntryDetailsLayout layout;
    private @Nullable EditBox filter;
    private String query = "";
    private double scroll;
    private GlassButton placeButton;
    private GlassButton exportButton;
    private GlassButton renameButton;
    private GlassButton deleteButton;

    EntryDetailsScreen(LibraryScreen parent, LibraryEntry entry, int version, String sha256, @Nullable Version cloudVersion, EntryFacts facts,
                       ThumbnailTextures thumbnails, Actions actions) {
        super(Component.literal(entry.name()));
        this.parent = parent;
        this.entry = entry;
        this.version = version;
        this.sha256 = sha256;
        this.cloudVersion = cloudVersion;
        this.facts = facts;
        this.thumbnails = thumbnails;
        this.actions = actions;
    }

    @Override
    protected void init() {
        layout = EntryDetailsLayout.of(width, height);
        Rect back = layout.back();
        addRenderableWidget(new GlassButton(back.x(), back.y(), back.w(), back.h(), Component.translatable("heavymatica.details.back"),
                GlassButton.Kind.SECONDARY, button -> onClose()));
        Rect row = layout.actions();
        placeButton = addRenderableWidget(new GlassButton(row.x(), row.y(), row.w(), 20, Component.translatable("heavymatica.library.place"),
                GlassButton.Kind.PRIMARY, button -> run(actions.place())).radius(7));
        int small = (row.w() - 2 * 4) / 3;
        int y = row.y() + 24;
        exportButton = addRenderableWidget(new GlassButton(row.x(), y, small, 18, Component.translatable("heavymatica.library.export"),
                GlassButton.Kind.SECONDARY, button -> run(actions.export())));
        renameButton = addRenderableWidget(new GlassButton(row.x() + small + 4, y, small, 18, Component.translatable("heavymatica.library.rename"),
                GlassButton.Kind.SECONDARY, button -> run(actions.rename())));
        deleteButton = addRenderableWidget(new GlassButton(row.x() + 2 * (small + 4), y, small, 18, Component.translatable("heavymatica.library.delete"),
                GlassButton.Kind.DANGER, button -> run(actions.delete())));
        Rect f = layout.filter();
        filter = new GlassEditBox(font, f.x(), f.y(), f.w(), f.h(), Component.translatable("heavymatica.details.filter"), true);
        filter.setValue(query);
        filter.setResponder(text -> {
            query = text;
            scroll = 0;
        });
        addRenderableWidget(filter);
        updateButtons();
    }

    private void updateButtons() {
        Component unavailable = Component.translatable("heavymatica.library.needs_connection");
        placeButton.enable(actions.canPlace().getAsBoolean(), unavailable);
        exportButton.enable(actions.canExport().getAsBoolean(), unavailable);
        renameButton.enable(actions.canRename().getAsBoolean(), Component.translatable("heavymatica.library.sign_in_needed"));
        deleteButton.enable(actions.canDelete().getAsBoolean(), Component.translatable("heavymatica.library.sign_in_needed"));
    }

    /** Back to the library first, so what the action opens returns there. */
    private void run(Runnable action) {
        minecraft.setScreen(parent);
        action.run();
    }

    /** Sets the filter text, for development checks. */
    void filterForDevelopment(String text) {
        if (filter != null) {
            filter.setValue(text);
        }
    }

    @Override
    public void tick() {
        updateButtons();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        EntryFacts.Facts known = facts.get(entry, sha256, cloudVersion).orElse(null);
        Glass.text(graphics, font, Component.translatable("heavymatica.details.title").getString(), layout.back().right() + 10,
                layout.back().y() + 5, GlassStyle.HINT, width);
        preview(graphics);
        factTiles(graphics, known);
        side(graphics, known, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partial);
    }

    private void preview(GuiGraphicsExtractor graphics) {
        Rect p = layout.preview();
        Glass.panel(graphics, p.x(), p.y(), p.w(), p.h(), Glass.RADIUS, 0xD10E1218, 0xD60E1218, 0x14FFFFFF);
        Glass.fill(graphics, p.x() + p.w() / 2.0 - p.h() * 0.7, p.y() + p.h() * 0.15, p.h() * 1.4, p.h() * 0.7, p.h() * 0.35, 0x147FD8FF);
        int size = Math.min(p.w() - 20, p.h() - 34);
        int x = p.x() + (p.w() - size) / 2;
        int y = p.y() + 8;
        thumbnails.get(entry).ifPresentOrElse(
                texture -> graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, size, size, 256, 256, 256, 256),
                () -> CardGrid.placeholder(graphics, p.centreX(), p.y() + p.h() / 2));
        int[] dims = dims();
        String[] labels = {Component.translatable("heavymatica.details.wide", dims[0]).getString(),
                Component.translatable("heavymatica.details.high", dims[1]).getString(), Component.translatable("heavymatica.details.deep", dims[2]).getString()};
        int chipX = p.x() + 10;
        for (String label : labels) {
            chipX += Glass.chip(graphics, font, label, chipX, p.bottom() - 21, 0xB3080B0F, GlassStyle.LABEL, 0) + 4;
        }
    }

    private int[] dims() {
        if (cloudVersion != null) {
            return new int[] {cloudVersion.getDimensions().getWidth(), cloudVersion.getDimensions().getHeight(), cloudVersion.getDimensions().getLength()};
        }
        return new int[] {entry.width(), entry.height(), entry.length()};
    }

    private void factTiles(GuiGraphicsExtractor graphics, EntryFacts.@Nullable Facts known) {
        int[] dims = dims();
        String format = Component.translatable("heavymatica.format." + formatKey()).getString();
        String size = dims[0] + " × " + dims[1] + " × " + dims[2];
        long blocks = cloudVersion != null ? cloudVersion.getBlockTotal() : entry.blockTotal();
        Integer types = known == null ? null : known.counts().rows().size();
        String minecraftVersion = null;
        if (known != null && known.dataVersion() != null) {
            int game = SharedConstants.getCurrentVersion().dataVersion().version();
            int data = known.dataVersion();
            minecraftVersion = VersionNames.name(data, game, SharedConstants.getCurrentVersion().name())
                    .orElse(Component.translatable("heavymatica.details.data_version", data).getString());
        }
        OffsetDateTime time = cloudVersion != null ? cloudVersion.getCreatedAt() : entry.updatedAt();
        List<FactTiles.Tile> tiles = FactTiles.of(format, size, blocks, types, minecraftVersion, time == null ? null : WHEN.format(time), entry.cloud());
        Rect area = layout.facts();
        int columns = 3;
        int w = (area.w() - (columns - 1) * 6) / columns;
        for (int i = 0; i < tiles.size(); i++) {
            int x = area.x() + (i % columns) * (w + 6);
            int y = area.y() + (i / columns) * (EntryDetailsLayout.FACT_HEIGHT + 6);
            Glass.box(graphics, x, y, w, EntryDetailsLayout.FACT_HEIGHT, 7, 0xC70E1218, 0x14FFFFFF);
            Glass.text(graphics, font, Component.translatable(tiles.get(i).labelKey()).getString().toUpperCase(Locale.ROOT), x + 8, y + 5, GlassStyle.HINT, w - 16);
            Glass.text(graphics, font, tiles.get(i).value(), x + 8, y + 17, GlassStyle.TEXT, w - 16);
        }
    }

    private String formatKey() {
        return switch (entry.format()) {
            case "litematica" -> "litematica";
            case "structure" -> "structure";
            default -> "sponge";
        };
    }

    private void side(GuiGraphicsExtractor graphics, EntryFacts.@Nullable Facts known, int mouseX, int mouseY) {
        Rect side = layout.side();
        Glass.panel(graphics, side.x(), side.y(), side.w(), side.h(), Glass.RADIUS, 0xDB1C232C, 0xD60E1218, 0x1AFFFFFF);
        int x = side.x() + 10;
        int w = side.w() - 20;
        TextFit name = Glass.text(graphics, font, entry.name(), x, side.y() + 10, GlassStyle.TEXT, w);
        Glass.tooltipIfShortened(graphics, font, name, x, side.y() + 10, w, 10, mouseX, mouseY);
        String kind = entry.cloud() ? Component.translatable("heavymatica.library.chip.cloud", "v" + version).getString()
                : Component.translatable("heavymatica.library.chip.local").getString();
        int chipX = x + Glass.chip(graphics, font, kind, x, side.y() + 22, 0x147FD8FF, entry.cloud() ? GlassStyle.ACCENT : GlassStyle.LABEL,
                entry.cloud() ? 0x8C7FD8FF : 0x4DFFFFFF) + 4;
        if (known != null && known.counts().unknownTypes() > 0) {
            Glass.chip(graphics, font, Component.translatable("heavymatica.library.details.unknown", known.counts().unknownTypes()).getString(), chipX,
                    side.y() + 22, 0x14FF6B6B, GlassStyle.DANGER_TEXT, 0x66FF6B6B);
        }
        Rect list = layout.list();
        if (known == null) {
            Glass.paragraph(graphics, font, Component.translatable(entry.cloud() ? "heavymatica.library.details.blocks_cloud"
                    : "heavymatica.library.details.blocks_pending"), list.x(), list.y(), list.w(), GlassStyle.SUBTITLE);
            return;
        }
        List<DetailBlockCounts.Row> rows = known.counts().rows().stream()
                .filter(row -> ScrollBars.matches(query, displayName(row), row.block()))
                .toList();
        long max = rows.stream().mapToLong(DetailBlockCounts.Row::count).max().orElse(1);
        long total = known.counts().rows().stream().mapToLong(DetailBlockCounts.Row::count).sum();
        int content = rows.size() * ROW;
        scroll = Math.clamp(scroll, 0, Math.max(0, content - list.h()));
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int y = list.y() - (int) scroll;
        for (DetailBlockCounts.Row row : rows) {
            if (y + ROW >= list.y() && y <= list.bottom()) {
                DetailsPanel.blockRow(graphics, font, row.block(), row.known(), row.count(), list.x(), y, list.w() - 6, mouseX, mouseY);
                Glass.bar(graphics, list.x() + 13, y + 11, list.w() - 19, 2, new long[] {row.count()}, Math.max(max, 1),
                        new int[] {row.known() ? 0x997FD8FF : 0x99FF6B6B});
                if (list.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW && total > 0) {
                    graphics.setTooltipForNextFrame(font, Component.translatable("heavymatica.details.share",
                            String.format(Locale.ROOT, "%.1f", row.count() * 100.0 / total)), mouseX, mouseY);
                }
            }
            y += ROW;
        }
        graphics.disableScissor();
        if (rows.isEmpty()) {
            Glass.text(graphics, font, Component.translatable("heavymatica.materials.nothing").getString(), list.x(), list.y() + 2, GlassStyle.HINT, list.w());
        }
        if (content > list.h()) {
            int thumb = Math.max(12, list.h() * list.h() / content);
            int thumbTop = list.y() + (int) Math.round(scroll / (content - list.h()) * (list.h() - thumb));
            Glass.scrollBar(graphics, list.right() - 2, list.y(), list.h(), thumbTop, thumb, false, true);
        }
    }

    private static String displayName(DetailBlockCounts.Row row) {
        var block = row.known() ? MaterialsScreen.block(row.block()) : null;
        return block == null ? row.block() : block.getName().getString();
    }

    /** The block IDs the list shows, for development checks. */
    List<String> blocksForDevelopment() {
        EntryFacts.Facts known = facts.get(entry, sha256, cloudVersion).orElse(null);
        return known == null ? List.of() : known.counts().rows().stream().filter(row -> ScrollBars.matches(query, displayName(row), row.block()))
                .map(DetailBlockCounts.Row::block).toList();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * ROW * 2;
        return true;
    }
}
