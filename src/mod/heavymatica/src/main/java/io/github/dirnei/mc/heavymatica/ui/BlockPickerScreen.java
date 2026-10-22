package io.github.dirnei.mc.heavymatica.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Picks the block a schematic block shows as: every block that has an item, blocks of the same class as the original
 * first, searchable by name and ID. Choosing one applies it and returns to the previous screen.
 */
public final class BlockPickerScreen extends Screen {

    private static final int ROW = 18;
    private static final int TOP = 40;
    private static final int PANEL = 260;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int HEADER = 0xFFFFE080;
    private static final int ORIGINAL = 0xFF80E0FF;

    record Choice(Block block, String id, String name) {
    }

    /** A list line: a header when {@code choice} is null. */
    private record Line(@Nullable Choice choice, Component header) {
    }

    private final Screen parent;
    private final String originalId;
    private final @Nullable Block original;
    private final Consumer<String> choose;
    private final List<Choice> all;
    private List<Line> lines = List.of();
    private @Nullable EditBox search;
    private final ScrollBar scroll = new ScrollBar();

    public BlockPickerScreen(Screen parent, String originalId, Consumer<String> choose) {
        super(Component.translatable("heavymatica.picker.title", name(originalId)));
        this.parent = parent;
        this.originalId = originalId;
        this.original = MaterialsScreen.block(originalId);
        this.choose = choose;
        this.all = BuiltInRegistries.BLOCK.stream().filter(block -> block.asItem() != Items.AIR)
                .map(block -> new Choice(block, BuiltInRegistries.BLOCK.getKey(block).toString(), block.getName().getString())).toList();
        filter("");
    }

    private static Component name(String blockId) {
        Block block = MaterialsScreen.block(blockId);
        return block == null ? Component.literal(blockId) : block.getName();
    }

    @Override
    protected void init() {
        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, width / 2 - PANEL / 2, 20, PANEL, 16, Component.translatable("heavymatica.picker.search"));
        search.setHint(Component.translatable("heavymatica.picker.search"));
        search.setValue(query);
        search.setResponder(this::filter);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).bounds(width / 2 - 60, height - 26, 120, 20).build());
    }

    @Override
    protected void setInitialFocus() {
        if (search != null) {
            setInitialFocus(search);
        }
    }

    /** The listed block IDs for development checks, suggestions first. */
    public List<String> idsForDevelopment() {
        return lines.stream().filter(line -> line.choice() != null).map(line -> line.choice().id()).toList();
    }

    /** Sets the search text, for development checks. */
    public void searchForDevelopment(String query) {
        if (search != null) {
            search.setValue(query);
        } else {
            filter(query);
        }
    }

    private void filter(String query) {
        Class<?> kind = original == null ? null : original.getClass();
        BlockChoices<Choice> choices = BlockChoices.of(all, choice -> choice.block().getClass() == kind, Choice::name, Choice::id, query);
        List<Line> next = new ArrayList<>();
        if (!choices.suggested().isEmpty()) {
            next.add(new Line(null, Component.translatable("heavymatica.picker.suggested")));
            choices.suggested().forEach(choice -> next.add(new Line(choice, Component.empty())));
        }
        if (!choices.others().isEmpty()) {
            next.add(new Line(null, Component.translatable("heavymatica.picker.all")));
            choices.others().forEach(choice -> next.add(new Line(choice, Component.empty())));
        }
        lines = next;
        scroll.reset();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private int bottom() {
        return height - 32;
    }

    private int barX() {
        return width / 2 + PANEL / 2 + 4;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll.scrollBy(-scrollY * ROW, lines.size() * ROW, bottom() - TOP);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (scroll.dragging()) {
            scroll.drag(event.y(), TOP, lines.size() * ROW, bottom() - TOP);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (ScrollBars.onTrack(event.x(), barX()) && scroll.press(event.y(), TOP, lines.size() * ROW, bottom() - TOP)) {
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int left = width / 2 - PANEL / 2;
        if (event.x() < left || event.x() >= left + PANEL || event.y() < TOP || event.y() >= bottom()) {
            return false;
        }
        int index = (int) ((event.y() - TOP + scroll.offset()) / ROW);
        if (index >= 0 && index < lines.size() && lines.get(index).choice() != null) {
            choose.accept(lines.get(index).choice().id());
            minecraft.setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        int left = width / 2 - PANEL / 2;
        graphics.centeredText(font, title, width / 2, 6, TEXT);
        if (lines.isEmpty()) {
            graphics.centeredText(font, Component.translatable("heavymatica.picker.nothing"), width / 2, TOP + 6, MUTED);
            return;
        }
        scroll.clamp(lines.size() * ROW, bottom() - TOP);
        ScrollBars.draw(graphics, scroll, barX(), TOP, lines.size() * ROW, bottom() - TOP, mouseX, mouseY);
        graphics.enableScissor(left, TOP, left + PANEL, bottom());
        int y = TOP - (int) scroll.offset();
        for (Line line : lines) {
            if (y + ROW > TOP && y < bottom()) {
                Choice choice = line.choice();
                if (choice == null) {
                    graphics.text(font, line.header(), left, y + 5, HEADER, false);
                } else {
                    boolean hovered = mouseX >= left && mouseX < left + PANEL && mouseY >= Math.max(y, TOP) && mouseY < Math.min(y + ROW, bottom());
                    if (hovered) {
                        graphics.fill(left, y, left + PANEL, y + ROW, 0xA0305080);
                    }
                    graphics.item(choice.block().asItem().getDefaultInstance(), left + 8, y + 1);
                    graphics.text(font, choice.name(), left + 28, y + 5, choice.id().equals(originalId) ? ORIGINAL : TEXT, false);
                }
            }
            y += ROW;
        }
        graphics.disableScissor();
    }
}
