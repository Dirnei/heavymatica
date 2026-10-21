package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.placement.MaterialList;
import io.github.dirnei.mc.heavymatica.placement.Materials;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The full material list of the selected locked placement: every item its visible layers still need with needed,
 * have and short, then the blocks no item places. Follows the world and the inventory while open, and does not pause
 * the game, so the comparison keeps scanning.
 */
public final class MaterialListScreen extends Screen {

    private static final int ROW = 18;
    private static final int TOP = 44;
    private static final int PANEL = 300;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int SHORT = 0xFFFF5050;
    private static final int HEADER = 0xFFFFE080;

    private final PlacementSession session;
    private @Nullable MaterialList<Item, Block> list;
    private double scroll;

    public MaterialListScreen(PlacementSession session) {
        super(Component.translatable("heavymatica.materials.title",
                session.placements().selected().map(Placement::name).orElse("")));
        this.session = session;
        session.refreshMaterials();
        refresh();
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(width / 2 - 60, height - 26, 120, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The session rebuilds the list every {@link Materials#REFRESH_TICKS} ticks; asking each tick picks that up. */
    @Override
    public void tick() {
        refresh();
    }

    private void refresh() {
        Placement placement = session.placements().selected().filter(Placement::locked).orElse(null);
        list = placement == null ? null : session.materials(placement);
    }

    /** The list's rows for development checks; empty while there is no list. */
    public List<MaterialList.Row<Item>> rowsForDevelopment() {
        return list == null ? List.of() : list.rows();
    }

    private int rowCount() {
        if (list == null) {
            return 0;
        }
        return list.rows().size() + (list.cantCarry().isEmpty() ? 0 : list.cantCarry().size() + 1);
    }

    private int bottom() {
        return height - 32;
    }

    private double maxScroll() {
        return Math.max(0, rowCount() * ROW - (bottom() - TOP));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - scrollY * ROW, 0, maxScroll());
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        int left = width / 2 - PANEL / 2;
        int neededX = left + PANEL - 120;
        int haveX = left + PANEL - 70;
        int shortX = left + PANEL - 20;
        graphics.centeredText(font, title, width / 2, 10, TEXT);
        graphics.text(font, Component.translatable("heavymatica.materials.item"), left + 20, TOP - 14, HEADER, false);
        right(graphics, Component.translatable("heavymatica.materials.needed"), neededX, TOP - 14, HEADER);
        right(graphics, Component.translatable("heavymatica.materials.have"), haveX, TOP - 14, HEADER);
        right(graphics, Component.translatable("heavymatica.materials.short"), shortX, TOP - 14, HEADER);
        graphics.fill(left, TOP - 3, left + PANEL, TOP - 2, 0x60FFFFFF);
        if (list == null || rowCount() == 0) {
            graphics.centeredText(font, Component.translatable("heavymatica.materials.empty"), width / 2, TOP + 6, MUTED);
            return;
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
        graphics.enableScissor(left, TOP, left + PANEL, bottom());
        int y = TOP - (int) scroll;
        for (MaterialList.Row<Item> row : list.rows()) {
            if (y + ROW > TOP && y < bottom()) {
                ItemStack stack = row.item().getDefaultInstance();
                graphics.item(stack, left, y + 1);
                graphics.text(font, row.item().getName(stack), left + 20, y + 5, TEXT, false);
                right(graphics, Component.literal(Integer.toString(row.needed())), neededX, y + 5, TEXT);
                right(graphics, Component.literal(Integer.toString(row.had())), haveX, y + 5, TEXT);
                right(graphics, Component.literal(Integer.toString(row.shortage())), shortX, y + 5, row.shortage() > 0 ? SHORT : MUTED);
            }
            y += ROW;
        }
        if (!list.cantCarry().isEmpty()) {
            graphics.text(font, Component.translatable("heavymatica.materials.cant_carry"), left, y + 5, HEADER, false);
            y += ROW;
            for (MaterialList.CantCarry<Block> entry : list.cantCarry()) {
                if (y + ROW > TOP && y < bottom()) {
                    graphics.text(font, entry.block().getName(), left + 20, y + 5, MUTED, false);
                    right(graphics, Component.literal(Integer.toString(entry.count())), neededX, y + 5, MUTED);
                }
                y += ROW;
            }
        }
        graphics.disableScissor();
    }

    private void right(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(font, text, x - font.width(text), y, color, false);
    }
}
