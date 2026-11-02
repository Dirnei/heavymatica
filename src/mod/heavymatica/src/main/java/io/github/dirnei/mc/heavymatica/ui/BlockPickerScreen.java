package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassDialogScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassEditBox;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import io.github.dirnei.mc.heavymatica.ui.glass.TextFit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Picks the block a schematic block is built with, over the materials screen: blocks of the original's kind first,
 * then every block that has an item, searchable by name and ID, each with how many of it the player carries. Clicking
 * a block applies it and closes; Keep original resets the swap.
 */
public final class BlockPickerScreen extends GlassDialogScreen {

    private static final int TILE_HEIGHT = 26;
    private static final int MIN_TILE = 112;
    private static final int GAP = 4;
    private static final int HEADING = 14;

    record Choice(Block block, String id, String name, Item item) {
    }

    /** A line of the grid: a heading, or a row of tiles. */
    private record Line(@Nullable Component heading, List<Choice> tiles) {
    }

    private final String originalId;
    private final @Nullable String currentTarget;
    private final @Nullable Block original;
    private final int positions;
    private final int need;
    private final boolean locked;
    private final ToIntFunction<Item> carried;
    private final Consumer<@Nullable String> choose;
    private final List<Choice> all;
    private List<Choice> suggested = List.of();
    private List<Choice> others = List.of();
    private List<Line> lines = List.of();
    private @Nullable EditBox search;
    private String query = "";
    private double scroll;
    private int columns = 1;
    private int tileWidth = MIN_TILE;
    private boolean answered;

    /**
     * @param currentTarget the block the row is swapped to, or null
     * @param positions     how many positions the swap changes
     * @param need          how many the row needs: still needed while locked, every position otherwise
     * @param choose        receives the chosen block ID, or null to keep the original
     */
    public BlockPickerScreen(Screen parent, String originalId, @Nullable String currentTarget, int positions, int need, boolean locked,
                             ToIntFunction<Item> carried, Consumer<@Nullable String> choose) {
        super(parent, Component.translatable("heavymatica.picker.title", name(originalId)));
        this.originalId = originalId;
        this.currentTarget = currentTarget;
        this.original = MaterialsScreen.block(originalId);
        this.positions = positions;
        this.need = need;
        this.locked = locked;
        this.carried = carried;
        this.choose = choose;
        this.all = BuiltInRegistries.BLOCK.stream().filter(block -> block.asItem() != Items.AIR)
                .map(block -> new Choice(block, BuiltInRegistries.BLOCK.getKey(block).toString(), block.getName().getString(), block.asItem())).toList();
        filter("");
    }

    private static Component name(String blockId) {
        Block block = MaterialsScreen.block(blockId);
        return block == null ? Component.literal(blockId) : block.getName();
    }

    @Override
    protected int dialogWidth() {
        return Math.min(480, Math.max(300, width - 120));
    }

    @Override
    protected int dialogHeight() {
        return Math.min(380, height - 24);
    }

    private Rect grid() {
        Rect frame = frame();
        int top = frame.y() + PAD + 30 + 24;
        return new Rect(frame.x() + PAD, top, frame.w() - 2 * PAD - 6, Math.max(0, frame.bottom() - PAD - 16 - top));
    }

    @Override
    protected void init() {
        super.init();
        Rect frame = frame();
        int fieldY = frame.y() + PAD + 30;
        String keep = Component.translatable("heavymatica.picker.keep").getString();
        int keepWidth = font.width(keep) + 16;
        search = new GlassEditBox(font, frame.x() + PAD, fieldY, frame.w() - 2 * PAD - keepWidth - 6, GlassEditBox.HEIGHT,
                Component.translatable("heavymatica.picker.search"), true);
        search.setValue(query);
        search.setResponder(this::filter);
        addRenderableWidget(search);
        GlassButton keepButton = addRenderableWidget(new GlassButton(frame.right() - PAD - keepWidth, fieldY, keepWidth, GlassEditBox.HEIGHT,
                Component.translatable("heavymatica.picker.keep"), GlassButton.Kind.SECONDARY, button -> answer(null)));
        keepButton.enable(currentTarget != null, Component.translatable("heavymatica.picker.keep_none"));
        Rect grid = grid();
        columns = Math.max(1, (grid.w() + GAP) / (MIN_TILE + GAP));
        tileWidth = (grid.w() - GAP * (columns - 1)) / columns;
        layoutLines();
    }

    @Override
    protected void setInitialFocus() {
        if (search != null) {
            setInitialFocus(search);
        }
    }

    /** The listed block IDs for development checks, suggestions first. */
    public List<String> idsForDevelopment() {
        List<String> ids = new ArrayList<>();
        suggested.forEach(choice -> ids.add(choice.id()));
        others.forEach(choice -> ids.add(choice.id()));
        return ids;
    }

    /** Sets the search text, for development checks. */
    public void searchForDevelopment(String text) {
        if (search != null) {
            search.setValue(text);
        } else {
            filter(text);
        }
    }

    private void filter(String text) {
        query = text;
        Class<?> kind = original == null ? null : original.getClass();
        BlockChoices<Choice> choices = BlockChoices.of(all, choice -> choice.block().getClass() == kind, Choice::name, Choice::id, text);
        suggested = choices.suggested();
        others = choices.others();
        scroll = 0;
        layoutLines();
    }

    private void layoutLines() {
        List<Line> next = new ArrayList<>();
        addPart(next, "heavymatica.picker.suggested", suggested);
        addPart(next, "heavymatica.picker.all", others);
        lines = next;
    }

    private void addPart(List<Line> into, String heading, List<Choice> choices) {
        if (choices.isEmpty()) {
            return;
        }
        into.add(new Line(Component.translatable(heading), List.of()));
        for (int i = 0; i < choices.size(); i += columns) {
            into.add(new Line(null, choices.subList(i, Math.min(choices.size(), i + columns))));
        }
    }

    private static int height(Line line) {
        return line.heading() != null ? HEADING : TILE_HEIGHT + GAP;
    }

    private int contentHeight() {
        return lines.stream().mapToInt(BlockPickerScreen::height).sum();
    }

    private void answer(@Nullable String blockId) {
        if (answered) {
            return;
        }
        answered = true;
        minecraft.setScreen(parent);
        choose.accept(blockId);
    }

    @Override
    public void onClose() {
        answered = true;
        super.onClose();
    }

    @Override
    protected void extractDialog(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Rect frame = frame();
        int x = frame.x() + PAD;
        Item icon = original == null ? Items.AIR : original.asItem();
        int titleX = x;
        if (icon != Items.AIR) {
            graphics.item(icon.getDefaultInstance(), x, frame.y() + PAD - 4);
            titleX += 20;
        }
        Glass.text(graphics, font, Component.translatable("heavymatica.picker.heading", name(originalId)).getString(), titleX, frame.y() + PAD,
                GlassStyle.TEXT, frame.right() - PAD - titleX);
        Glass.text(graphics, font, Component.translatable("heavymatica.picker.subtitle", positions, name(originalId)).getString(), x, frame.y() + PAD + 13,
                GlassStyle.HINT, frame.w() - 2 * PAD);
        Rect grid = grid();
        if (lines.isEmpty()) {
            Glass.text(graphics, font, Component.translatable("heavymatica.picker.nothing").getString(), grid.x(), grid.y() + 4, GlassStyle.HINT, grid.w());
        }
        int content = contentHeight();
        scroll = Math.clamp(scroll, 0, Math.max(0, content - grid.h()));
        graphics.enableScissor(grid.x(), grid.y(), grid.right(), grid.bottom());
        int y = grid.y() - (int) scroll;
        for (Line line : lines) {
            int h = height(line);
            if (y + h >= grid.y() && y <= grid.bottom()) {
                if (line.heading() != null) {
                    graphics.text(font, line.heading().getString().toUpperCase(Locale.ROOT), grid.x(), y + 3, GlassStyle.HINT, false);
                } else {
                    for (int i = 0; i < line.tiles().size(); i++) {
                        tile(graphics, line.tiles().get(i), grid.x() + i * (tileWidth + GAP), y, mouseX, mouseY, grid);
                    }
                }
            }
            y += h;
        }
        graphics.disableScissor();
        if (content > grid.h()) {
            int thumb = Math.max(12, grid.h() * grid.h() / content);
            int thumbTop = grid.y() + (int) Math.round(scroll / (content - grid.h()) * (grid.h() - thumb));
            Glass.scrollBar(graphics, grid.right() + 3, grid.y(), grid.h(), thumbTop, thumb, false, true);
        }
        footer(graphics, frame);
    }

    private void tile(GuiGraphicsExtractor graphics, Choice choice, int x, int y, int mouseX, int mouseY, Rect grid) {
        boolean current = choice.id().equals(currentTarget) || currentTarget == null && choice.id().equals(originalId);
        boolean hovered = grid.contains(mouseX, mouseY) && mouseX >= x && mouseX < x + tileWidth && mouseY >= y && mouseY < y + TILE_HEIGHT;
        int fill = current ? 0x1F7FD8FF : hovered ? 0x14FFFFFF : 0x0AFFFFFF;
        Glass.box(graphics, x, y, tileWidth, TILE_HEIGHT, 6, fill, current ? GlassStyle.ACCENT : hovered ? 0x33FFFFFF : 0x0FFFFFFF);
        graphics.item(choice.item().getDefaultInstance(), x + 5, y + 5);
        TextFit name = Glass.text(graphics, font, choice.name(), x + 25, y + 4, GlassStyle.TEXT, tileWidth - 30);
        CarriedLabel label = CarriedLabel.of(carried.applyAsInt(choice.item()), need);
        String text = switch (label.kind()) {
            case NONE -> Component.translatable("heavymatica.picker.carried.none").getString();
            case SOME -> Component.translatable("heavymatica.picker.carried.some", label.have()).getString();
            case ENOUGH -> Component.translatable("heavymatica.picker.carried.enough", label.have()).getString();
        };
        int color = switch (label.kind()) {
            case NONE -> GlassStyle.SUBTITLE;
            case SOME -> GlassStyle.LABEL;
            case ENOUGH -> GlassStyle.CONFIRM;
        };
        Glass.text(graphics, font, text, x + 25, y + 15, color, tileWidth - 30);
        if (hovered && name.shortened()) {
            graphics.setTooltipForNextFrame(font, Component.literal(name.full()), mouseX, mouseY);
        }
    }

    private void footer(GuiGraphicsExtractor graphics, Rect frame) {
        int y = frame.bottom() - PAD - 8;
        graphics.fill(frame.x() + PAD, y - 5, frame.right() - PAD, y - 4, 0x14FFFFFF);
        String now = currentTarget != null ? name(currentTarget).getString() : name(originalId).getString();
        Block shown = MaterialsScreen.block(currentTarget != null ? currentTarget : originalId);
        int have = shown == null || shown.asItem() == Items.AIR ? 0 : carried.applyAsInt(shown.asItem());
        int shortage = CarriedLabel.shortage(have, need);
        int x = frame.x() + PAD;
        String label = Component.translatable("heavymatica.picker.now").getString() + " ";
        graphics.text(font, label, x, y, GlassStyle.HINT, false);
        x += font.width(label);
        TextFit fit = Glass.text(graphics, font, now, x, y, GlassStyle.ACCENT, frame.w() / 2);
        x += font.width(fit.shown()) + 4;
        String fitText = shortage == 0 ? Component.translatable("heavymatica.picker.fit.enough").getString()
                : Component.translatable(locked ? "heavymatica.picker.fit.short" : "heavymatica.picker.fit.short_all", shortage).getString();
        Glass.text(graphics, font, "· " + fitText, x, y, shortage == 0 ? GlassStyle.CONFIRM : GlassStyle.SHORT_TEXT, frame.right() - PAD - x - 70);
        Glass.textRight(graphics, font, Component.translatable("heavymatica.picker.esc").getString(), frame.right() - PAD, y, GlassStyle.SUBTITLE);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * (TILE_HEIGHT + GAP);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        Rect grid = grid();
        if (!grid.contains(event.x(), event.y())) {
            return false;
        }
        int y = grid.y() - (int) scroll;
        for (Line line : lines) {
            int h = height(line);
            if (line.heading() == null && event.y() >= y && event.y() < y + TILE_HEIGHT) {
                int column = (int) ((event.x() - grid.x()) / (tileWidth + GAP));
                if (column >= 0 && column < line.tiles().size() && event.x() - grid.x() - column * (tileWidth + GAP) < tileWidth) {
                    Choice choice = line.tiles().get(column);
                    answer(choice.id().equals(originalId) ? null : choice.id());
                    return true;
                }
            }
            y += h;
        }
        return false;
    }
}
