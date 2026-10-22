package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The block types of the selected placement's schematic, most used first, each with its count and what it shows as.
 * Each row picks a replacement or resets it; changes apply at once. Does not pause the game.
 */
public final class MaterialsScreen extends Screen {

    private static final int ROW = 20;
    private static final int TOP = 44;
    private static final int PANEL = 400;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int MISSING = 0xFFFF5050;
    private static final int SWAPPED = 0xFF80E0FF;
    private static final int HEADER = 0xFFFFE080;

    private final PlacementSession session;
    private final @Nullable UUID placementId;
    private List<SchematicModel.BlockCount> rows = List.of();
    private int firstRow;

    public MaterialsScreen(PlacementSession session) {
        super(Component.translatable("heavymatica.swaps.title", session.placements().selected().map(Placement::name).orElse("")));
        this.session = session;
        this.placementId = session.placements().selected().map(Placement::id).orElse(null);
        refresh();
    }

    private Optional<Placement> placement() {
        return placementId == null ? Optional.empty() : session.placements().get(placementId);
    }

    private void refresh() {
        SchematicModel model = placement().map(session::model).orElse(null);
        rows = model == null ? List.of() : model.blockCounts();
    }

    /** The rows for development checks: block ID and count, most used first. */
    public List<SchematicModel.BlockCount> rowsForDevelopment() {
        return rows;
    }

    @Override
    protected void init() {
        firstRow = Mth.clamp(firstRow, 0, Math.max(0, rows.size() - visibleRows()));
        int left = width / 2 - PANEL / 2;
        Placement placement = placement().orElse(null);
        for (int i = firstRow; i < Math.min(rows.size(), firstRow + visibleRows()); i++) {
            String blockId = rows.get(i).block();
            int y = TOP + (i - firstRow) * ROW;
            addRenderableWidget(Button.builder(Component.translatable("heavymatica.swaps.pick"), button -> pick(blockId))
                    .bounds(left + PANEL - 84, y, 40, 18).build());
            Button reset = addRenderableWidget(Button.builder(Component.translatable("heavymatica.swaps.reset"), button -> reset(blockId))
                    .bounds(left + PANEL - 42, y, 42, 18).build());
            reset.active = placement != null && placement.swaps().containsKey(blockId);
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(width / 2 - 60, height - 26, 120, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The schematic may still be loading when the screen opens. */
    @Override
    public void tick() {
        if (rows.isEmpty()) {
            refresh();
            if (!rows.isEmpty()) {
                rebuildWidgets();
            }
        }
    }

    private void pick(String blockId) {
        minecraft.setScreen(new BlockPickerScreen(this, blockId, target -> session.controller().swap(blockId, target)));
    }

    private void reset(String blockId) {
        session.controller().resetSwap(blockId);
        rebuildWidgets();
    }

    private int visibleRows() {
        return Math.max(1, (height - 32 - TOP) / ROW);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int next = Mth.clamp(firstRow - (int) Math.signum(scrollY), 0, Math.max(0, rows.size() - visibleRows()));
        if (next != firstRow) {
            firstRow = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        int left = width / 2 - PANEL / 2;
        int countX = left + 190;
        int targetX = left + 210;
        graphics.centeredText(font, title, width / 2, 10, TEXT);
        graphics.text(font, Component.translatable("heavymatica.swaps.block"), left + 20, TOP - 14, HEADER, false);
        right(graphics, Component.translatable("heavymatica.swaps.count"), countX, TOP - 14, HEADER);
        graphics.text(font, Component.translatable("heavymatica.swaps.target"), targetX + 20, TOP - 14, HEADER, false);
        graphics.fill(left, TOP - 3, left + PANEL, TOP - 2, 0x60FFFFFF);
        Placement placement = placement().orElse(null);
        if (placement == null || rows.isEmpty()) {
            boolean loading = placement != null && session.model(placement) == null;
            graphics.centeredText(font, Component.translatable(loading ? "heavymatica.swaps.loading" : "heavymatica.swaps.empty"), width / 2, TOP + 6,
                    MUTED);
            return;
        }
        for (int i = firstRow; i < Math.min(rows.size(), firstRow + visibleRows()); i++) {
            SchematicModel.BlockCount row = rows.get(i);
            int y = TOP + (i - firstRow) * ROW;
            block(graphics, row.block(), left, y, 150, TEXT);
            right(graphics, Component.literal(Long.toString(row.count())), countX, y + 5, TEXT);
            graphics.text(font, "→", countX + 6, y + 5, MUTED, false);
            String target = placement.swaps().get(row.block());
            if (target == null) {
                graphics.text(font, Component.translatable("heavymatica.swaps.none"), targetX + 20, y + 5, MUTED, false);
            } else if (block(target) == null) {
                graphics.text(font, fit(Component.translatable("heavymatica.swaps.missing", target).getString(), 96), targetX, y + 5, MISSING, false);
            } else {
                block(graphics, target, targetX, y, 96, SWAPPED);
            }
        }
    }

    /** A block's icon and name, or its ID when the game does not know it. */
    private void block(GuiGraphicsExtractor graphics, String blockId, int x, int y, int width, int color) {
        Block block = block(blockId);
        if (block == null) {
            graphics.text(font, fit(blockId, width), x + 20, y + 5, MISSING, false);
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

    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    private void right(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(font, text, x - font.width(text), y, color, false);
    }
}
