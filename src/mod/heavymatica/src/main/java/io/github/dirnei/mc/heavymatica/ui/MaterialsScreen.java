package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.MaterialList;
import io.github.dirnei.mc.heavymatica.placement.Materials;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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
 * The block types of the selected placement's schematic, each with its total and what it shows as, with a pick and
 * a reset per row; changes apply at once. While the placement is locked, each row also shows needed, have and short
 * for the item of the block it shows as, and the blocks no item places follow under "Can't carry". A search narrows
 * the rows. Follows the swaps, the world and the inventory while open, and does not pause the game.
 */
public final class MaterialsScreen extends Screen {

    private static final int ROW = 20;
    private static final int TOP = 58;
    private static final int MAX_PANEL = 520;
    private static final int BUTTONS = 88;
    private static final int NUMBERS = 120;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int MISSING = 0xFFFF5050;
    private static final int SHORT = 0xFFFF5050;
    private static final int SWAPPED = 0xFF80E0FF;
    private static final int HEADER = 0xFFFFE080;

    private final PlacementSession session;
    private final @Nullable UUID placementId;
    private List<SchematicModel.BlockCount> counts = List.of();
    private @Nullable MaterialList<Item, Block> list;
    private List<MaterialRows.Row<Item>> all = List.of();
    private List<MaterialRows.Row<Item>> rows = List.of();
    private List<MaterialList.CantCarry<Block>> cantCarry = List.of();
    private final ScrollBar scroll = new ScrollBar();
    private final List<Button> pickButtons = new ArrayList<>();
    private final List<Button> resetButtons = new ArrayList<>();
    private @Nullable EditBox search;

    public MaterialsScreen(PlacementSession session) {
        super(Component.translatable("heavymatica.materials.title", session.placements().selected().map(Placement::name).orElse("")));
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
        all = MaterialRows.of(counts, swaps, blockId -> block(blockId) != null, MaterialsScreen::itemOf, list, session::carried);
        filter();
    }

    private void filter() {
        String query = query();
        rows = all.stream().filter(row -> matches(query, row.block()) || row.target() != null && matches(query, row.target())).toList();
        cantCarry = list == null ? List.of() : list.cantCarry().stream()
                .filter(entry -> ScrollBars.matches(query, entry.block().getName().getString(), BuiltInRegistries.BLOCK.getKey(entry.block()).toString()))
                .toList();
    }

    private static boolean matches(String query, String blockId) {
        Block block = block(blockId);
        return ScrollBars.matches(query, block == null ? blockId : block.getName().getString(), blockId);
    }

    private String query() {
        return search == null ? "" : search.getValue();
    }

    /** The listed rows for development checks, in screen order. */
    List<MaterialRows.Row<Item>> rowsForDevelopment() {
        return rows;
    }

    /** The listed can't-carry blocks for development checks. */
    List<MaterialList.CantCarry<Block>> cantCarryForDevelopment() {
        return cantCarry;
    }

    /** Sets the search text, for development checks. */
    public void searchForDevelopment(String query) {
        if (search != null) {
            search.setValue(query);
        }
    }

    @Override
    protected void init() {
        int left = left();
        int right = left + panel();
        String query = query();
        search = new EditBox(font, left, 22, panel(), 16, Component.translatable("heavymatica.materials.search"));
        search.setHint(Component.translatable("heavymatica.materials.search"));
        search.setValue(query);
        search.setResponder(text -> {
            filter();
            scroll.reset();
            layoutRows();
        });
        addRenderableWidget(search);
        pickButtons.clear();
        resetButtons.clear();
        for (int slot = 0; slot < visibleRows(); slot++) {
            int y = TOP + slot * ROW;
            int index = slot;
            pickButtons.add(addRenderableWidget(Button.builder(Component.translatable("heavymatica.materials.pick"), button -> onRow(index, this::pick))
                    .bounds(right - 84, y, 40, 18).build()));
            resetButtons.add(addRenderableWidget(Button.builder(Component.translatable("heavymatica.materials.reset"), button -> onRow(index, this::reset))
                    .bounds(right - 42, y, 42, 18).build()));
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(width / 2 - 60, height - 26, 120, 20).build());
        layoutRows();
    }

    @Override
    protected void setInitialFocus() {
        if (search != null) {
            setInitialFocus(search);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * The schematic may still be loading when the screen opens, and the session rebuilds the material list every
     * {@link Materials#REFRESH_TICKS} ticks; asking each tick picks both up.
     */
    @Override
    public void tick() {
        refresh();
        layoutRows();
    }

    private void pick(String blockId) {
        minecraft.setScreen(new BlockPickerScreen(this, blockId, target -> session.controller().swap(blockId, target)));
    }

    private void reset(String blockId) {
        session.controller().resetSwap(blockId);
        refresh();
        layoutRows();
    }

    private void onRow(int slot, Consumer<String> action) {
        int index = firstRow() + slot;
        if (index < rows.size()) {
            action.accept(rows.get(index).block());
        }
    }

    /** Points the row buttons at the rows now in view. */
    private void layoutRows() {
        scroll.clamp(content(), view());
        Placement placement = placement().orElse(null);
        for (int slot = 0; slot < pickButtons.size(); slot++) {
            int index = firstRow() + slot;
            boolean shown = index < rows.size();
            pickButtons.get(slot).visible = shown;
            resetButtons.get(slot).visible = shown;
            resetButtons.get(slot).active = shown && placement != null && placement.swaps().containsKey(rows.get(index).block());
        }
    }

    private int panel() {
        return Math.min(MAX_PANEL, width - 40);
    }

    private int left() {
        return width / 2 - panel() / 2;
    }

    private int visibleRows() {
        return Math.max(1, (height - 32 - TOP) / ROW);
    }

    private int firstRow() {
        return (int) Math.round(scroll.offset() / ROW);
    }

    private int lineCount() {
        return rows.size() + (cantCarry.isEmpty() ? 0 : cantCarry.size() + 1);
    }

    private int content() {
        return lineCount() * ROW;
    }

    private int view() {
        return visibleRows() * ROW;
    }

    private int barX() {
        return left() + panel() + 4;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll.scrollBy(-Math.signum(scrollY) * ROW, content(), view());
        layoutRows();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (ScrollBars.onTrack(event.x(), barX()) && scroll.press(event.y(), TOP, content(), view())) {
            layoutRows();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (scroll.dragging()) {
            scroll.drag(event.y(), TOP, content(), view());
            layoutRows();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        scroll.release();
        return super.mouseReleased(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        int left = left();
        int right = left + panel();
        boolean numbers = list != null;
        int columns = right - BUTTONS - (numbers ? NUMBERS : 0) - 6;
        int blockWidth = (columns - left - 50) / 2;
        int totalX = left + blockWidth + 40;
        int targetX = totalX + 14;
        int targetWidth = columns - targetX;
        int shortX = right - BUTTONS - 4;
        int haveX = shortX - 40;
        int neededX = haveX - 40;
        graphics.centeredText(font, title, width / 2, 10, TEXT);
        graphics.text(font, Component.translatable("heavymatica.materials.block"), left + 20, TOP - 14, HEADER, false);
        right(graphics, Component.translatable("heavymatica.materials.total"), totalX, TOP - 14, HEADER);
        graphics.text(font, Component.translatable("heavymatica.materials.target"), targetX + 20, TOP - 14, HEADER, false);
        if (numbers) {
            right(graphics, Component.translatable("heavymatica.materials.needed"), neededX, TOP - 14, HEADER);
            right(graphics, Component.translatable("heavymatica.materials.have"), haveX, TOP - 14, HEADER);
            right(graphics, Component.translatable("heavymatica.materials.short"), shortX, TOP - 14, HEADER);
        }
        graphics.fill(left, TOP - 3, right, TOP - 2, 0x60FFFFFF);
        Placement placement = placement().orElse(null);
        if (placement != null && !all.isEmpty() && lineCount() == 0) {
            graphics.centeredText(font, Component.translatable("heavymatica.materials.nothing"), width / 2, TOP + 6, MUTED);
            return;
        }
        if (placement == null || all.isEmpty()) {
            boolean loading = placement != null && session.model(placement) == null;
            graphics.centeredText(font, Component.translatable(loading ? "heavymatica.materials.loading" : "heavymatica.materials.empty"), width / 2,
                    TOP + 6, MUTED);
            return;
        }
        ScrollBars.draw(graphics, scroll, barX(), TOP, content(), view(), mouseX, mouseY);
        int firstRow = firstRow();
        for (int i = firstRow; i < Math.min(lineCount(), firstRow + visibleRows()); i++) {
            int y = TOP + (i - firstRow) * ROW;
            if (i < rows.size()) {
                MaterialRows.Row<Item> row = rows.get(i);
                block(graphics, row.block(), left, y, blockWidth, TEXT);
                right(graphics, Component.literal(Long.toString(row.total())), totalX, y + 5, TEXT);
                graphics.text(font, "→", totalX + 4, y + 5, MUTED, false);
                target(graphics, row.target(), targetX, y, targetWidth);
                if (row.numbers() != null) {
                    MaterialList.Row<Item> counts = row.numbers();
                    right(graphics, Component.literal(Integer.toString(counts.needed())), neededX, y + 5, TEXT);
                    right(graphics, Component.literal(Integer.toString(counts.had())), haveX, y + 5, TEXT);
                    right(graphics, Component.literal(Integer.toString(counts.shortage())), shortX, y + 5, counts.shortage() > 0 ? SHORT : MUTED);
                }
            } else if (i == rows.size()) {
                graphics.text(font, Component.translatable("heavymatica.materials.cant_carry"), left, y + 5, HEADER, false);
            } else {
                MaterialList.CantCarry<Block> entry = cantCarry.get(i - rows.size() - 1);
                graphics.text(font, fit(entry.block().getName().getString(), columns - left - 20), left + 20, y + 5, MUTED, false);
                right(graphics, Component.literal(Integer.toString(entry.count())), neededX, y + 5, MUTED);
            }
        }
    }

    private void target(GuiGraphicsExtractor graphics, @Nullable String target, int x, int y, int width) {
        if (target == null) {
            graphics.text(font, Component.translatable("heavymatica.materials.none"), x + 20, y + 5, MUTED, false);
        } else if (block(target) == null) {
            graphics.text(font, fit(Component.translatable("heavymatica.materials.missing", target).getString(), width), x, y + 5, MISSING, false);
        } else {
            block(graphics, target, x, y, width, SWAPPED);
        }
    }

    /** A block's icon and name, or its ID when the game does not know it. */
    private void block(GuiGraphicsExtractor graphics, String blockId, int x, int y, int width, int color) {
        Block block = block(blockId);
        if (block == null) {
            graphics.text(font, fit(blockId, width - 20), x + 20, y + 5, MISSING, false);
            return;
        }
        Item item = block.asItem();
        if (item != Items.AIR) {
            graphics.item(item.getDefaultInstance(), x, y + 1);
        }
        graphics.text(font, fit(block.getName().getString(), width - 20), x + 20, y + 5, color, false);
    }

    static @Nullable Block block(String blockId) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(blockId)).orElse(null);
    }

    private static @Nullable Item itemOf(String blockId) {
        Block block = block(blockId);
        Item item = block == null ? Items.AIR : block.asItem();
        return item == Items.AIR ? null : item;
    }

    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private void right(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(font, text, x - font.width(text), y, color, false);
    }
}
