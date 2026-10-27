package io.github.dirnei.mc.heavymatica.targeting;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.WorldComparison;
import io.github.dirnei.mc.heavymatica.targeting.GhostTargeting.GhostTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

/**
 * A small HUD below the crosshair naming the ghost target: the planned block's icon and name, its orientation, its
 * state, the placement when it is not the selected one, and whether it is out of reach.
 */
public final class TargetHud {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "target_hud");

    private static final int PADDING = 3;
    private static final int LINE = 10;
    private static final int ICON = 16;
    private static final int GAP_BELOW_CROSSHAIR = 12;

    private static volatile boolean shownByJade;

    /** One HUD line; the first line is the block name, drawn next to the icon. */
    public record Line(Component text, int color) {
    }

    private final Supplier<@Nullable PlacementSession> sessions;
    private final Supplier<@Nullable GhostTargeting> targeting;

    /** Looked up per frame: GUI layers are registered before the client services exist. */
    public TargetHud(Supplier<@Nullable PlacementSession> sessions, Supplier<@Nullable GhostTargeting> targeting) {
        this.sessions = sessions;
        this.targeting = targeting;
    }

    /** Hands the target over to Jade's tooltip; the HUD stays hidden from then on. */
    public static void showInJade() {
        shownByJade = true;
    }

    public void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ID, this::render);
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        PlacementSession session = sessions.get();
        if (session == null || shownByJade || minecraft.options.hideGui || minecraft.screen != null || minecraft.player == null) {
            return;
        }
        GhostTarget target = shownTarget(targeting.get());
        if (target == null) {
            return;
        }
        WorldComparison comparison = session.comparison(target.placement());
        ItemStack icon = comparison == null ? ItemStack.EMPTY : icon(comparison, target);
        List<Line> lines = lines(target, selectedPlacement(session));
        Font font = minecraft.font;
        int iconWidth = icon.isEmpty() ? 0 : ICON + 2;
        int width = 0;
        for (int i = 0; i < lines.size(); i++) {
            width = Math.max(width, font.width(lines.get(i).text()) + (i == 0 ? iconWidth : 0));
        }
        width += 2 * PADDING;
        int firstLine = icon.isEmpty() ? LINE : ICON + 2;
        int height = firstLine + (lines.size() - 1) * LINE + 2 * PADDING - 2;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() / 2 + GAP_BELOW_CROSSHAIR;
        graphics.fill(x, y, x + width, y + height, 0x90000000);
        int lineY = y + PADDING;
        if (!icon.isEmpty()) {
            graphics.item(icon, x + PADDING, lineY);
        }
        int nameY = icon.isEmpty() ? lineY : lineY + (ICON - 8) / 2;
        graphics.text(font, lines.getFirst().text(), x + PADDING + iconWidth, nameY, lines.getFirst().color(), false);
        lineY += firstLine;
        for (Line line : lines.subList(1, lines.size())) {
            graphics.text(font, line.text(), x + PADDING, lineY, line.color(), false);
            lineY += LINE;
        }
    }

    /** The target when the HUD range shows it; null otherwise. */
    public static @Nullable GhostTarget shownTarget(@Nullable GhostTargeting targeting) {
        int range = HeavymaticaConfig.targetingHudRange();
        GhostTarget target = targeting == null || range == 0 ? null : targeting.current();
        return target == null || target.distance() > range ? null : target;
    }

    /** The item shown for the planned block: the first item that counts toward it, none when no item places it. */
    static ItemStack icon(WorldComparison comparison, GhostTarget target) {
        if (target.plannedIndex() < 0) {
            return ItemStack.EMPTY;
        }
        int[] accepts = comparison.accepts(target.plannedIndex());
        return accepts.length == 0 ? ItemStack.EMPTY : new ItemStack(comparison.item(accepts[0]));
    }

    public static @Nullable UUID selectedPlacement(PlacementSession session) {
        return session.placements().selected().map(Placement::id).orElse(null);
    }

    /** The name, orientation, state, placement and reach lines for a target. */
    public static List<Line> lines(GhostTarget target, @Nullable UUID selected) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(target.planned().getBlock().getName(), TargetLines.TEXT));
        String details = WorldComparison.details(target.planned());
        if (!details.isEmpty()) {
            lines.add(new Line(Component.literal(details), TargetLines.MUTED));
        }
        Line state = stateLine(target);
        if (state != null) {
            lines.add(state);
        }
        if (!target.placement().id().equals(selected)) {
            lines.add(new Line(Component.literal(target.placement().name()), TargetLines.MUTED));
        }
        if (!target.inReach()) {
            lines.add(new Line(Component.translatable("heavymatica.target.out_of_reach"), TargetLines.MUTED));
        }
        return lines;
    }

    /** The target's state in its comparison colour, naming the found block, its orientation or its amount where that differs. */
    public static @Nullable Line stateLine(GhostTarget target) {
        TargetLines.StateLine line = TargetLines.state(target.state(), target.finish(), target.plannedWaterlogged());
        if (line == null) {
            return null;
        }
        Component text;
        if (target.state() == CellState.WRONG_BLOCK && target.found() != null) {
            text = Component.translatable(line.key(), target.found().getBlock().getName());
        } else if (target.state() == CellState.WRONG_ORIENTATION && target.found() != null) {
            text = Component.translatable(line.key(), WorldComparison.details(target.found()));
        } else if ((target.state() == CellState.INCOMPLETE || target.state() == CellState.TOO_MANY) && target.found() != null) {
            text = amountText(target.state(), target.planned(), target.found());
        } else {
            text = Component.translatable(line.key());
        }
        return new Line(text, line.color());
    }

    /** The amount an incomplete or too many position holds against the planned amount. */
    public static Component amountText(CellState state, BlockState planned, BlockState found) {
        String key = state == CellState.INCOMPLETE ? "heavymatica.target.incomplete" : "heavymatica.target.too_many";
        return Component.translatable(key, WorldComparison.amount(found), WorldComparison.amount(planned));
    }

    /** The HUD's lines as text, or why it is hidden, for development checks. */
    public static String textForDevelopment(Minecraft minecraft, @Nullable PlacementSession session, @Nullable GhostTargeting targeting) {
        if (session == null) {
            return "hidden (no session)";
        }
        if (shownByJade) {
            return "hidden (Jade)";
        }
        if (minecraft.options.hideGui || minecraft.screen != null) {
            return "hidden (GUI hidden or screen open)";
        }
        GhostTarget target = shownTarget(targeting);
        if (target == null) {
            return "hidden (no target in range)";
        }
        WorldComparison comparison = session.comparison(target.placement());
        ItemStack icon = comparison == null ? ItemStack.EMPTY : icon(comparison, target);
        List<String> text = new ArrayList<>();
        text.add("[" + (icon.isEmpty() ? "no icon" : icon.getItem().toString()) + "]");
        lines(target, selectedPlacement(session)).forEach(line -> text.add(line.text().getString()));
        return String.join(" | ", text);
    }
}
