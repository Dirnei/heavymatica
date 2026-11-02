package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.blocks.BlockResolver;
import io.github.dirnei.mc.heavymatica.formats.BlockStates;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.BlockEquivalents;
import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.placement.MaterialList;
import io.github.dirnei.mc.heavymatica.placement.Materials;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassEditBox;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import io.github.dirnei.mc.heavymatica.ui.glass.TextFit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The block types of the selected placement's schematic, each under the block it is built with: a swapped row shows
 * its target in the accent colour with an undo button and the original on hover, and clicking a name picks another
 * block. While the placement is locked, a bar per row splits its planned blocks in the visible layers into built,
 * carried and short, a bar for the whole placement sits on top, and the blocks no item places follow under "can't
 * carry". Unknown blocks come last in their own part. Follows the swaps, the world and the inventory while open, and
 * does not pause the game.
 */
public final class MaterialsScreen extends GlassScreen {

    private static final int LOCKED_ROW = 24;
    private static final int ROW = 16;
    private static final int HEADER_ROW = 18;

    /** One line of the table: a part's heading, a block type, or a block no item places. */
    private record Line(@Nullable Component heading, MaterialRows.@Nullable Row<Item> row, MaterialList.@Nullable CantCarry<Block> cantCarry) {
    }

    private final PlacementSession session;
    private final @Nullable UUID placementId;
    private List<SchematicModel.BlockCount> counts = List.of();
    private @Nullable MaterialList<Item, Block> list;
    private List<MaterialRows.Row<Item>> all = List.of();
    private List<MaterialRows.Row<Item>> rows = List.of();
    private List<MaterialList.CantCarry<Block>> cantCarry = List.of();
    private List<Map.Entry<String, Integer>> unknown = List.of();
    private List<Line> lines = List.of();
    private @Nullable EditBox search;
    private String query = "";
    private MaterialsLayout layout;
    private boolean locked;
    private double scroll;

    public MaterialsScreen(PlacementSession session) {
        super(Component.translatable("heavymatica.materials.heading"));
        this.session = session;
        this.placementId = session.placements().selected().map(Placement::id).orElse(null);
        session.refreshMaterials();
        refresh();
    }

    private Optional<Placement> placement() {
        return placementId == null ? Optional.empty() : session.placements().get(placementId);
    }

    private void refresh() {
        Placement placement = placement().orElse(null);
        if (placement != null && counts.isEmpty()) {
            SchematicModel model = session.model(placement);
            counts = model == null ? List.of() : model.blockCounts();
        }
        list = placement != null && placement.locked() ? session.materials(placement) : null;
        Map<String, String> swaps = placement == null ? Map.of() : placement.swaps();
        SchematicModel model = placement == null ? null : session.model(placement);
        Integer dataVersion = model == null ? null : model.dataVersion();
        all = MaterialRows.of(counts, swaps, blockId -> BlockStates.id(BlockResolver.resolution().upgraded(blockId, dataVersion)),
                blockId -> block(blockId) != null, MaterialsScreen::itemOf, MaterialsScreen::block, list, session::carried);
        filter();
    }

    private void filter() {
        rows = all.stream()
                .filter(row -> matches(query, row.current()) || matches(query, row.block()) || row.target() != null && matches(query, row.target()))
                .toList();
        cantCarry = list == null ? List.of() : list.cantCarry().stream()
                .filter(entry -> ScrollBars.matches(query, entry.block().getName().getString(), BuiltInRegistries.BLOCK.getKey(entry.block()).toString()))
                .toList();
        unknown = list == null ? List.of() : list.unknown().entrySet().stream().filter(entry -> ScrollBars.matches(query, entry.getKey(), entry.getKey())).toList();
        List<Line> next = new ArrayList<>();
        rows.stream().filter(row -> !row.unknownPart()).forEach(row -> next.add(new Line(null, row, null)));
        if (!cantCarry.isEmpty()) {
            next.add(new Line(Component.translatable("heavymatica.materials.cant_carry"), null, null));
            cantCarry.forEach(entry -> next.add(new Line(null, null, entry)));
        }
        List<MaterialRows.Row<Item>> unknownRows = rows.stream().filter(MaterialRows.Row::unknownPart).toList();
        if (!unknownRows.isEmpty()) {
            next.add(new Line(Component.translatable("heavymatica.materials.unknown_blocks"), null, null));
            unknownRows.forEach(row -> next.add(new Line(null, row, null)));
        }
        lines = next;
    }

    private static boolean matches(String query, String blockId) {
        Block block = block(blockId);
        return ScrollBars.matches(query, block == null ? blockId : block.getName().getString(), blockId);
    }

    /** The listed rows for development checks, in screen order. */
    List<MaterialRows.Row<Item>> rowsForDevelopment() {
        return lines.stream().filter(line -> line.row() != null).map(Line::row).toList();
    }

    /** The listed unknown blocks with their positions in the visible layers, for development checks. */
    List<Map.Entry<String, Integer>> unknownForDevelopment() {
        return unknown;
    }

    /** The listed can't-carry blocks for development checks. */
    List<MaterialList.CantCarry<Block>> cantCarryForDevelopment() {
        return cantCarry;
    }

    /** Sets the search text, for development checks. */
    public void searchForDevelopment(String text) {
        if (search != null) {
            search.setValue(text);
        }
    }

    /** Opens the block picker for the row of this block ID, or for the first row when it is empty, for development checks. */
    void pickForDevelopment(String blockId) {
        all.stream().filter(row -> blockId.isEmpty() || row.block().equals(blockId)).findFirst().ifPresent(this::pick);
    }

    @Override
    protected void init() {
        locked = placement().map(Placement::locked).orElse(false);
        layout = MaterialsLayout.of(width, height, locked);
        Rect s = layout.search();
        search = new GlassEditBox(font, s.x(), s.y(), s.w(), s.h(), Component.translatable("heavymatica.materials.search"), true);
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            filter();
            scroll = 0;
        });
        addRenderableWidget(search);
        Rect d = layout.done();
        addRenderableWidget(new GlassButton(d.x(), d.y(), d.w(), d.h(), CommonComponents.GUI_DONE, GlassButton.Kind.PRIMARY, button -> onClose()));
    }

    @Override
    protected void setInitialFocus() {
        if (search != null) {
            setInitialFocus(search);
        }
    }

    /**
     * The schematic may still be loading when the screen opens, and the session rebuilds the material list every
     * {@link Materials#REFRESH_TICKS} ticks; asking each tick picks both up.
     */
    @Override
    public void tick() {
        boolean wasLocked = locked;
        refresh();
        if (placement().map(Placement::locked).orElse(false) != wasLocked) {
            rebuildWidgets();
        }
    }

    /** Picks for the block as it shows today, so an upgraded block suggests its own kind; the swap stays under the ID as written. */
    private void pick(MaterialRows.Row<Item> row) {
        int need = row.numbers() != null ? row.numbers().needed() : (int) Math.min(Integer.MAX_VALUE, row.total());
        minecraft.setScreen(new BlockPickerScreen(this, row.current(), row.target(), (int) Math.min(Integer.MAX_VALUE, row.total()), need,
                list != null, session::carried, target -> {
                    if (target == null) {
                        session.controller().resetSwap(row.block());
                    } else {
                        session.controller().swap(row.block(), target);
                    }
                }));
    }

    private void reset(MaterialRows.Row<Item> row) {
        session.controller().resetSwap(row.block());
        refresh();
    }

    private int rowHeight(Line line) {
        if (line.heading() != null) {
            return HEADER_ROW;
        }
        return locked && line.row() != null && !line.row().unknownPart() ? LOCKED_ROW : ROW;
    }

    private int content() {
        return lines.stream().mapToInt(this::rowHeight).sum();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * ROW * 2;
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        Rect area = layout.rows();
        if (!area.contains(event.x(), event.y())) {
            return false;
        }
        MaterialsLayout.Columns columns = layout.columnsFor(locked);
        int y = area.y() - (int) scroll;
        for (Line line : lines) {
            int h = rowHeight(line);
            if (event.y() >= y && event.y() < y + h && line.row() != null) {
                MaterialRows.Row<Item> row = line.row();
                int nameRight = nameRight(row, columns);
                if (row.target() != null && event.x() >= nameRight + 4 && event.x() < nameRight + 16) {
                    reset(row);
                    return true;
                }
                if (event.x() < nameRight) {
                    pick(row);
                    return true;
                }
            }
            y += h;
        }
        return false;
    }

    /** Where a row's clickable name ends: the name's width, kept clear of the undo button and the count. */
    private int nameRight(MaterialRows.Row<Item> row, MaterialsLayout.Columns columns) {
        int maxName = columns.nameWidth() - (row.target() != null ? 18 : 0);
        int textWidth = font.width(TextFit.of(name(row.shown()), maxName - 14, font::width).shown());
        return columns.name() + 14 + textWidth + 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        Placement placement = placement().orElse(null);
        heading(graphics, placement);
        if (locked && list != null) {
            summary(graphics);
        }
        Rect table = layout.table();
        Glass.panel(graphics, table.x(), table.y(), table.w(), table.h());
        columnHeadings(graphics);
        if (placement == null || all.isEmpty()) {
            boolean loading = placement != null && session.model(placement) == null;
            Glass.text(graphics, font, Component.translatable(placement == null ? "heavymatica.materials.select_first"
                    : loading ? "heavymatica.materials.loading" : "heavymatica.materials.empty").getString(), layout.rows().x(), layout.rows().y() + 6,
                    GlassStyle.HINT, layout.rows().w());
        } else if (lines.isEmpty()) {
            Glass.text(graphics, font, Component.translatable("heavymatica.materials.nothing").getString(), layout.rows().x(), layout.rows().y() + 6,
                    GlassStyle.HINT, layout.rows().w());
        } else {
            table(graphics, mouseX, mouseY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partial);
    }

    private void heading(GuiGraphicsExtractor graphics, @Nullable Placement placement) {
        Rect t = layout.title();
        Glass.text(graphics, font, title.getString(), t.x(), t.y() + 1, GlassStyle.TEXT, t.w());
        if (placement == null) {
            return;
        }
        int x = t.x();
        int y = t.y() + 11;
        TextFit name = Glass.text(graphics, font, placement.name(), x, y + 2, GlassStyle.LABEL, Math.max(20, t.w() / 2));
        x += font.width(name.shown()) + 6;
        String state = Component.translatable("heavymatica.materials.state." + placement.state().name().toLowerCase(Locale.ROOT)).getString();
        if (x + Glass.chipWidth(font, state) <= t.right()) {
            boolean lockedState = placement.locked();
            x += Glass.chip(graphics, font, state, x, y, lockedState ? 0x247FD8FF : 0x12FFFFFF, lockedState ? GlassStyle.ACCENT : GlassStyle.LABEL,
                    lockedState ? 0x997FD8FF : 0x40FFFFFF) + 4;
        }
        if (placement.locked() && placement.layers().mode() != Layers.Mode.OFF) {
            Layers.Range range = Layers.visible(placement, PlacementSession.feetLayer(placement));
            String layers = Component.translatable("heavymatica.materials.layers", range.from() + 1, range.to() + 1, placement.height()).getString();
            if (x + Glass.chipWidth(font, layers) <= t.right()) {
                Glass.chip(graphics, font, layers, x, y, 0x12FFFFFF, GlassStyle.LABEL, 0);
            }
        }
    }

    private void summary(GuiGraphicsExtractor graphics) {
        Rect s = layout.summary();
        Glass.panel(graphics, s.x(), s.y(), s.w(), s.h(), Glass.RADIUS, 0xD10E1218, 0xD10E1218, 0x14FFFFFF);
        MaterialList.Bar total = list.total();
        int x = s.x() + 10;
        int w = s.w() - 20;
        Glass.text(graphics, font, Component.translatable("heavymatica.materials.whole", total.planned()).getString().toUpperCase(Locale.ROOT), x, s.y() + 6,
                GlassStyle.HINT, w / 2);
        int right = s.right() - 10;
        right = legend(graphics, right, s.y() + 6, GlassStyle.SHORT, GlassStyle.SHORT_TEXT,
                Component.translatable("heavymatica.materials.legend.short", total.shortage()).getString());
        right = legend(graphics, right, s.y() + 6, GlassStyle.CARRIED, GlassStyle.LABEL,
                Component.translatable("heavymatica.materials.legend.carried", total.carried()).getString());
        legend(graphics, right, s.y() + 6, GlassStyle.BUILT, GlassStyle.LABEL, Component.translatable("heavymatica.materials.legend.built", total.built()).getString());
        Glass.bar(graphics, x, s.y() + 20, w, 7, new long[] {total.built(), total.carried(), total.shortage()}, total.planned(),
                new int[] {GlassStyle.BUILT, GlassStyle.CARRIED, GlassStyle.SHORT});
    }

    /** A legend entry right-aligned to {@code right}; returns where the next one ends. */
    private int legend(GuiGraphicsExtractor graphics, int right, int y, int swatch, int textColor, String text) {
        int x = right - font.width(text);
        graphics.text(font, text, x, y, textColor, false);
        Glass.fill(graphics, x - 9, y + 1, 6, 6, 1.5, swatch);
        return x - 18;
    }

    private void columnHeadings(GuiGraphicsExtractor graphics) {
        Rect c = layout.columns();
        MaterialsLayout.Columns columns = layout.columnsFor(locked);
        int y = c.y() + 3;
        graphics.text(font, Component.translatable("heavymatica.materials.column.block").getString().toUpperCase(Locale.ROOT), columns.name(), y,
                GlassStyle.HINT, false);
        String inPlan = Component.translatable("heavymatica.materials.column.in_plan").getString().toUpperCase(Locale.ROOT);
        Glass.textRight(graphics, font, inPlan, columns.countRight(), y, GlassStyle.HINT);
        if (locked) {
            Glass.text(graphics, font, Component.translatable("heavymatica.materials.column.progress").getString().toUpperCase(Locale.ROOT), columns.bar(), y,
                    GlassStyle.HINT, columns.barWidth());
            Glass.textRight(graphics, font, Component.translatable("heavymatica.materials.column.short").getString().toUpperCase(Locale.ROOT),
                    columns.shortRight(), y, GlassStyle.HINT);
        }
        graphics.fill(c.x(), c.bottom(), c.right(), c.bottom() + 1, 0x14FFFFFF);
    }

    private void table(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Rect area = layout.rows();
        int content = content();
        scroll = Math.clamp(scroll, 0, Math.max(0, content - area.h()));
        MaterialsLayout.Columns columns = layout.columnsFor(locked);
        graphics.enableScissor(area.x(), area.y(), area.right() + 6, area.bottom());
        int y = area.y() - (int) scroll;
        for (Line line : lines) {
            int h = rowHeight(line);
            if (y + h >= area.y() && y <= area.bottom()) {
                if (line.heading() != null) {
                    graphics.text(font, line.heading().getString().toUpperCase(Locale.ROOT), columns.name(), y + 7, GlassStyle.HINT, false);
                } else if (line.cantCarry() != null) {
                    cantCarryRow(graphics, line.cantCarry(), columns, y);
                } else {
                    row(graphics, line.row(), columns, y, h, mouseX, mouseY);
                }
            }
            y += h;
        }
        graphics.disableScissor();
        if (content > area.h()) {
            int thumb = Math.max(12, area.h() * area.h() / content);
            int thumbTop = area.y() + (int) Math.round(scroll / (content - area.h()) * (area.h() - thumb));
            Glass.scrollBar(graphics, area.right() + 3, area.y(), area.h(), thumbTop, thumb, false, true);
        }
    }

    private void row(GuiGraphicsExtractor graphics, MaterialRows.Row<Item> row, MaterialsLayout.Columns columns, int y, int h, int mouseX, int mouseY) {
        int nameY = y + (h == LOCKED_ROW ? 4 : 4);
        boolean unknownBlock = row.unknownPart();
        int nameRight = nameRight(row, columns);
        boolean hovered = layout.rows().contains(mouseX, mouseY) && mouseY >= y && mouseY < y + h;
        boolean onName = hovered && mouseX >= columns.name() && mouseX < nameRight;
        if (hovered) {
            graphics.fill(layout.rows().x() - 4, y, layout.rows().right() + 2, y + h - 1, 0x08FFFFFF);
        }
        if (onName) {
            Glass.fill(graphics, columns.name() - 3, nameY - 3, nameRight - columns.name() + 6, 14, 4, 0x1AFFFFFF);
        }
        icon(graphics, row.shown(), columns.name(), nameY - 1, unknownBlock);
        int color = unknownBlock ? GlassStyle.DANGER_TEXT : row.swapped() ? GlassStyle.ACCENT : GlassStyle.TEXT;
        int maxName = columns.nameWidth() - (row.target() != null ? 18 : 0);
        TextFit name = Glass.text(graphics, font, name(row.shown()), columns.name() + 14, nameY, color, maxName - 14);
        if (row.target() != null) {
            int ux = nameRight + 4;
            boolean missing = !row.swapped();
            int accent = missing ? GlassStyle.DANGER_TEXT : GlassStyle.ACCENT;
            boolean overUndo = hovered && mouseX >= ux && mouseX < ux + 12;
            Glass.box(graphics, ux, nameY - 2, 12, 12, 3, GlassStyle.withAlpha(accent, overUndo ? 0x33 : 0x14), GlassStyle.withAlpha(accent, 0x59));
            graphics.text(font, "×", ux + 3, nameY, accent, false);
            if (overUndo) {
                graphics.setTooltipForNextFrame(font, Component.translatable(missing ? "heavymatica.materials.missing_undo" : "heavymatica.materials.undo",
                        row.target()), mouseX, mouseY);
            }
        }
        if (onName) {
            Component tip = row.target() != null && !row.swapped() ? Component.translatable("heavymatica.materials.missing", row.target())
                    : row.swapped() ? Component.translatable("heavymatica.materials.swapped_from", name(row.current()))
                    : unknownBlock ? Component.translatable("heavymatica.materials.unknown_tip", row.current())
                    : name.shortened() ? Component.translatable("heavymatica.materials.swap_tip_named", name.full())
                    : Component.translatable("heavymatica.materials.swap_tip");
            graphics.setTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
        Glass.textRight(graphics, font, Long.toString(row.total()), columns.countRight(), nameY, GlassStyle.LABEL);
        if (!locked) {
            return;
        }
        if (unknownBlock) {
            Integer visible = unknown.stream().filter(entry -> entry.getKey().equals(row.current())).map(Map.Entry::getValue).findFirst().orElse(null);
            String text = Component.translatable(visible == null ? "heavymatica.materials.unknown_hint" : "heavymatica.materials.unknown_visible",
                    visible == null ? 0 : visible).getString();
            Glass.text(graphics, font, text, columns.bar(), nameY, GlassStyle.HINT, columns.barWidth() + 40);
            return;
        }
        MaterialList.Bar bar = row.bar();
        if (bar == null) {
            Glass.text(graphics, font, Component.translatable("heavymatica.materials.not_needed").getString(), columns.bar(), nameY, GlassStyle.SUBTITLE,
                    columns.barWidth());
            return;
        }
        Glass.bar(graphics, columns.bar(), y + 4, columns.barWidth(), 5, new long[] {bar.built(), bar.carried(), bar.shortage()}, bar.planned(),
                new int[] {GlassStyle.BUILT, GlassStyle.CARRIED, GlassStyle.SHORT});
        Glass.text(graphics, font, legendText(row, bar), columns.bar(), y + 12, GlassStyle.HINT, columns.barWidth());
        String shortText = bar.shortage() > 0 ? Integer.toString(bar.shortage())
                : Component.translatable(bar.planned() == bar.built() ? "heavymatica.materials.done" : "heavymatica.materials.enough").getString();
        Glass.textRight(graphics, font, shortText, columns.shortRight(), y + 7, bar.shortage() > 0 ? GlassStyle.SHORT_TEXT : GlassStyle.CONFIRM);
    }

    /** "38 built · 22 carried · 36 short", and the finishing step when positions need one. */
    private String legendText(MaterialRows.Row<Item> row, MaterialList.Bar bar) {
        List<String> parts = new ArrayList<>();
        if (bar.built() > 0) {
            parts.add(Component.translatable("heavymatica.materials.legend.built", bar.built()).getString());
        }
        if (bar.carried() > 0) {
            parts.add(Component.translatable("heavymatica.materials.legend.carried_short", bar.carried()).getString());
        }
        if (bar.shortage() > 0) {
            parts.add(Component.translatable("heavymatica.materials.legend.short", bar.shortage()).getString());
        }
        BlockEquivalents.Finish finish = row.finish();
        if (finish != null && row.finishing() > 0) {
            parts.add(Component.translatable("heavymatica.materials.finish." + finish.name().toLowerCase(Locale.ROOT), row.finishing()).getString());
        }
        return String.join(" · ", parts);
    }

    private void cantCarryRow(GuiGraphicsExtractor graphics, MaterialList.CantCarry<Block> entry, MaterialsLayout.Columns columns, int y) {
        icon(graphics, BuiltInRegistries.BLOCK.getKey(entry.block()).toString(), columns.name(), y + 3, false);
        Glass.text(graphics, font, entry.block().getName().getString(), columns.name() + 14, y + 4, GlassStyle.TEXT, columns.nameWidth() - 14);
        Glass.textRight(graphics, font, Integer.toString(entry.count()), columns.countRight(), y + 4, GlassStyle.LABEL);
        if (locked) {
            Glass.text(graphics, font, Component.translatable("heavymatica.materials.cant_carry_hint").getString(), columns.bar(), y + 4, GlassStyle.HINT,
                    columns.barWidth());
        }
    }

    /** The block's item at a small size, or a dashed square for blocks without an item or unknown to the game. */
    private void icon(GuiGraphicsExtractor graphics, String blockId, int x, int y, boolean unknownBlock) {
        Block block = unknownBlock ? null : block(blockId);
        Item item = block == null ? Items.AIR : block.asItem();
        if (item != Items.AIR) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(0.625f, 0.625f);
            graphics.item(item.getDefaultInstance(), 0, 0);
            graphics.pose().popMatrix();
        } else {
            Glass.rim(graphics, x + 1, y + 1, 8, 8, 2, 1, unknownBlock ? GlassStyle.DANGER_TEXT : GlassStyle.SUBTITLE);
        }
    }

    private static String name(String blockId) {
        Block block = block(blockId);
        return block == null ? blockId : block.getName().getString();
    }

    static @Nullable Block block(String blockId) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(blockId)).orElse(null);
    }

    /** The item the equivalents table places a block with, or the block's own item when the table's is unknown or none. */
    private static @Nullable Item itemOf(String blockId) {
        Block block = block(blockId);
        if (block == null) {
            return null;
        }
        Block placedWith = block(BlockEquivalents.of(blockId).placedWith());
        Item item = placedWith == null ? Items.AIR : placedWith.asItem();
        if (item == Items.AIR) {
            item = block.asItem();
        }
        return item == Items.AIR ? null : item;
    }
}
