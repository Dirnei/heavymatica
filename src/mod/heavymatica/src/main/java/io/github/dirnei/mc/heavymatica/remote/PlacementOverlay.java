package io.github.dirnei.mc.heavymatica.remote;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.placement.ComparisonState;
import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.placement.MaterialList;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementSource;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform;
import io.github.dirnei.mc.heavymatica.placement.WorldComparison;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The HUD panel shown while the remote is held: the selected placement's name, version, state badge, position,
 * rotation and mirror, and hints for the keys that work in its state, naming the keys the player actually bound.
 */
public final class PlacementOverlay {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "placement_overlay");

    private static final int PADDING = 4;
    private static final int LINE = 10;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int KEY = 0xFFFFE080;
    private static final int WARNING = 0xFFFF6060;
    private static final int FOLLOWING = 0xFFFFD040;
    private static final int ANCHORED = 0xFF40E060;
    private static final int LOCKED = 0xFF40D0F0;
    private static final int MISTAKE_RED = 0xFFFF5050;
    private static final int MISTAKE_ORANGE = 0xFFFFA040;
    private static final int MISTAKE_CYAN = 0xFF40E8F8;
    private static final int MISTAKE_MAGENTA = 0xFFFF60FF;
    private static final int SHORTAGE_LINES = 3;

    /** One overlay line: an optional key name in its own colour, then text. */
    record Line(Component key, Component text, int color) {

        static Line text(Component text, int color) {
            return new Line(Component.empty(), text, color);
        }
    }

    private final Supplier<@Nullable PlacementSession> sessions;

    /** The session is looked up per frame: GUI layers are registered before the client services exist. */
    public PlacementOverlay(Supplier<@Nullable PlacementSession> sessions) {
        this.sessions = sessions;
    }

    public void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ID, this::render);
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        PlacementSession session = sessions.get();
        if (session == null || minecraft.options.hideGui || minecraft.screen != null || minecraft.player == null || !Remotes.held()) {
            return;
        }
        List<Line> lines = lines(minecraft, session);
        Font font = minecraft.font;
        int keyWidth = lines.stream().mapToInt(line -> font.width(line.key())).max().orElse(0);
        int keyColumn = keyWidth == 0 ? 0 : keyWidth + 6;
        int width = lines.stream().mapToInt(line -> (line.key().getString().isEmpty() ? 0 : keyColumn) + font.width(line.text())).max().orElse(0)
                + 2 * PADDING;
        int height = lines.size() * LINE + 2 * PADDING - 2;
        int x = HeavymaticaConfig.overlaySide() == HeavymaticaConfig.OverlaySide.LEFT ? 4 : graphics.guiWidth() - width - 4;
        int y = (graphics.guiHeight() - height) / 2;
        graphics.fill(x, y, x + width, y + height, 0x90000000);
        int lineY = y + PADDING;
        for (Line line : lines) {
            if (line == SEPARATOR) {
                graphics.fill(x + PADDING, lineY + 3, x + width - PADDING, lineY + 4, 0x60FFFFFF);
            } else if (line.key().getString().isEmpty()) {
                graphics.text(font, line.text(), x + PADDING, lineY, line.color(), false);
            } else {
                graphics.text(font, line.key(), x + PADDING, lineY, KEY, false);
                graphics.text(font, line.text(), x + PADDING + keyColumn, lineY, line.color(), false);
            }
            lineY += LINE;
        }
    }

    private static final Line SEPARATOR = Line.text(Component.empty(), 0);

    /** The overlay's lines as text, for development checks. */
    public static List<String> textForDevelopment(Minecraft minecraft, PlacementSession session) {
        return lines(minecraft, session).stream().filter(line -> line != SEPARATOR)
                .map(line -> (line.key().getString().isEmpty() ? "" : line.key().getString() + "  ") + line.text().getString()).toList();
    }

    static List<Line> lines(Minecraft minecraft, PlacementSession session) {
        List<Line> lines = new ArrayList<>();
        Optional<Placement> selected = session.placements().selected();
        if (selected.isEmpty()) {
            lines.add(Line.text(Component.translatable("heavymatica.overlay.none"), TEXT));
            if (!HeavymaticaConfig.overlayCompact()) {
                lines.add(SEPARATOR);
                lines.add(hint(minecraft.options.keyUse, "heavymatica.overlay.hint.select"));
            }
            return lines;
        }
        Placement placement = selected.get();
        Component version = placement.source() instanceof PlacementSource.Cloud cloud
                ? Component.literal("v" + cloud.version())
                : Component.translatable("heavymatica.overlay.local");
        lines.add(Line.text(Component.literal(placement.name() + "  ").append(version), TEXT));
        lines.add(switch (placement.state()) {
            case FOLLOWING -> Line.text(Component.translatable("heavymatica.overlay.following"), FOLLOWING);
            case ANCHORED -> Line.text(Component.translatable("heavymatica.overlay.anchored"), ANCHORED);
            case LOCKED -> Line.text(Component.translatable("heavymatica.overlay.locked"), LOCKED);
        });
        if (HeavymaticaConfig.overlayCompact()) {
            return lines;
        }
        PlacementTransform.Bounds bounds = PlacementTransform.bounds(placement);
        lines.add(Line.text(Component.translatable("heavymatica.overlay.position", bounds.minX(), bounds.minY(), bounds.minZ()), MUTED));
        lines.add(Line.text(Component.translatable("heavymatica.overlay.rotation", placement.rotation().degrees(),
                Component.translatable("heavymatica.overlay.mirror." + placement.mirror().name().toLowerCase(Locale.ROOT))), MUTED));
        if (placement.locked()) {
            lines.add(Line.text(layersText(placement), TEXT));
            lines.add(Line.text(inHandText(placement), TEXT));
            WorldComparison comparison = session.comparison(placement);
            if (comparison != null) {
                comparisonLines(minecraft, placement, comparison, lines);
            }
            MaterialList<Item, Block> materials = session.materials(placement);
            if (materials != null) {
                shortageLines(materials, lines);
            }
        }
        lines.add(SEPARATOR);
        if (session.unavailable(placement)) {
            lines.add(Line.text(Component.translatable("heavymatica.overlay.unavailable"), WARNING));
        }
        if (session.controller().removePending(System.currentTimeMillis())) {
            lines.add(new Line(name(RemoteKeys.REMOVE), Component.translatable("heavymatica.overlay.remove_again"), WARNING));
            return lines;
        }
        if (session.controller().unlockPending(System.currentTimeMillis())) {
            lines.add(new Line(name(RemoteKeys.LOCK), Component.translatable("heavymatica.overlay.unlock_again"), WARNING));
            return lines;
        }
        if (placement.locked()) {
            Layers.Mode mode = placement.layers().mode();
            if (mode == Layers.Mode.FROM_TO) {
                lines.add(new Line(pair(RemoteKeys.MOVE_FORWARD, RemoteKeys.MOVE_BACK), Component.translatable("heavymatica.overlay.hint.window"), TEXT));
                lines.add(new Line(pair(RemoteKeys.MOVE_UP, RemoteKeys.MOVE_DOWN), Component.translatable("heavymatica.overlay.hint.window_size"), TEXT));
            } else if (mode == Layers.Mode.FOLLOW) {
                lines.add(new Line(pair(RemoteKeys.MOVE_UP, RemoteKeys.MOVE_DOWN), Component.translatable("heavymatica.overlay.hint.follow_height"), TEXT));
            }
            lines.add(new Line(pair(RemoteKeys.MOVE_LEFT, RemoteKeys.MOVE_RIGHT), Component.translatable("heavymatica.overlay.hint.layer_mode"), TEXT));
            lines.add(hint(RemoteKeys.IN_HAND, "heavymatica.overlay.hint.in_hand"));
            lines.add(hint(RemoteKeys.MATERIALS, "heavymatica.overlay.hint.materials"));
            lines.add(hint(RemoteKeys.LOCK, "heavymatica.overlay.hint.unlock"));
            return lines;
        }
        if (placement.following()) {
            lines.add(hint(minecraft.options.keyUse, "heavymatica.overlay.hint.anchor"));
        } else {
            boolean arrows = RemoteKeys.MOVE_FORWARD.isDefault() && RemoteKeys.MOVE_BACK.isDefault() && RemoteKeys.MOVE_LEFT.isDefault()
                    && RemoteKeys.MOVE_RIGHT.isDefault();
            lines.add(new Line(arrows ? Component.translatable("heavymatica.overlay.key.arrows")
                    : keys(RemoteKeys.MOVE_FORWARD, RemoteKeys.MOVE_BACK, RemoteKeys.MOVE_LEFT, RemoteKeys.MOVE_RIGHT),
                    Component.translatable("heavymatica.overlay.hint.move"), TEXT));
        }
        lines.add(new Line(keys(RemoteKeys.MOVE_UP, RemoteKeys.MOVE_DOWN), Component.translatable("heavymatica.overlay.hint.up_down"), TEXT));
        lines.add(hint(RemoteKeys.ROTATE, "heavymatica.overlay.hint.rotate"));
        lines.add(hint(RemoteKeys.MIRROR, "heavymatica.overlay.hint.mirror"));
        if (!placement.following()) {
            lines.add(new Line(Component.translatable("heavymatica.overlay.key.sneak_use", minecraft.options.keyShift.getTranslatedKeyMessage(),
                    minecraft.options.keyUse.getTranslatedKeyMessage()), Component.translatable("heavymatica.overlay.hint.release"), TEXT));
        }
        if (!placement.following()) {
            lines.add(hint(RemoteKeys.LOCK, "heavymatica.overlay.hint.lock"));
        }
        lines.add(hint(RemoteKeys.REMOVE, placement.following() ? "heavymatica.overlay.hint.cancel" : "heavymatica.overlay.hint.remove"));
        return lines;
    }

    private static Component layersText(Placement placement) {
        int height = placement.height();
        Layers.Range range = Layers.visible(placement, PlacementSession.feetLayer(placement));
        return switch (placement.layers().mode()) {
            case OFF -> Component.translatable("heavymatica.overlay.layers.all", height);
            case FROM_TO -> Component.translatable("heavymatica.overlay.layers.from_to", range.from(), range.to(), height);
            case FOLLOW -> Component.translatable("heavymatica.overlay.layers.follow", placement.layers().followOffset(), range.from(), range.to(),
                    height);
        };
    }

    /** Progress, layer progress, the mistake counts and, when the crosshair is on a mistake, what belongs there. */
    private static void comparisonLines(Minecraft minecraft, Placement placement, WorldComparison comparison, List<Line> lines) {
        ComparisonState state = comparison.state();
        if (state.scanning()) {
            lines.add(Line.text(Component.translatable("heavymatica.overlay.progress.scanning"), MUTED));
        } else {
            lines.add(Line.text(progress("heavymatica.overlay.progress", state.matching(0, state.sizeY() - 1), state.counted(0, state.sizeY() - 1)), TEXT));
            if (placement.layers().mode() != Layers.Mode.OFF) {
                Layers.Range range = Layers.visible(placement, PlacementSession.feetLayer(placement));
                lines.add(Line.text(progress("heavymatica.overlay.progress.layers", state.matching(range.from(), range.to()),
                        state.counted(range.from(), range.to())), TEXT));
            }
        }
        lines.add(Line.text(Component.empty()
                .append(count(state, CellState.WRONG_BLOCK, "wrong_block", MISTAKE_RED)).append("  ")
                .append(count(state, CellState.WRONG_ORIENTATION, "wrong_orientation", MISTAKE_ORANGE)).append("  ")
                .append(count(state, CellState.WRONG_WATER, "wrong_water", MISTAKE_CYAN)).append("  ")
                .append(count(state, CellState.EXTRA, "extra", MISTAKE_MAGENTA)), TEXT));
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && minecraft.level != null) {
            CellState target = comparison.stateAt(hit.getBlockPos());
            if (target != null && target.mistake()) {
                lines.add(Line.text(Component.translatable("heavymatica.overlay.expected", block(comparison.expectedAt(hit.getBlockPos()))), MUTED));
                lines.add(Line.text(Component.translatable("heavymatica.overlay.found", block(minecraft.level.getBlockState(hit.getBlockPos()))), MUTED));
            }
        }
    }

    /** The largest shortages of the visible layers, or that nothing is short. */
    private static void shortageLines(MaterialList<Item, Block> materials, List<Line> lines) {
        MaterialList.Shortages<Item> shortages = materials.shortages(SHORTAGE_LINES);
        if (shortages.none()) {
            lines.add(Line.text(Component.translatable("heavymatica.overlay.materials.nothing_short"), ANCHORED));
            return;
        }
        for (MaterialList.Row<Item> row : shortages.top()) {
            lines.add(Line.text(Component.translatable("heavymatica.overlay.materials.short", row.item().getName(row.item().getDefaultInstance()),
                    row.shortage()), WARNING));
        }
        if (shortages.more() > 0) {
            lines.add(Line.text(Component.translatable("heavymatica.overlay.materials.more", shortages.more(), name(RemoteKeys.MATERIALS)), MUTED));
        }
    }

    private static Component progress(String key, int matching, int counted) {
        int percent = counted == 0 ? 100 : (int) (matching * 100L / counted);
        return Component.translatable(key, percent, matching, counted);
    }

    private static Component count(ComparisonState state, CellState cell, String name, int color) {
        return Component.translatable("heavymatica.overlay.count." + name, state.total(cell)).withColor(color & 0xFFFFFF);
    }

    private static Component block(BlockState state) {
        String details = WorldComparison.details(state);
        MutableComponent name = state.getBlock().getName().copy();
        return details.isEmpty() ? name : name.append(" (" + details + ")");
    }

    private static Component inHandText(Placement placement) {
        return Component.translatable(placement.inHand() ? "heavymatica.overlay.in_hand.on" : "heavymatica.overlay.in_hand.off");
    }

    private static Component pair(KeyMapping first, KeyMapping second) {
        return Component.empty().append(name(first)).append(" / ").append(name(second));
    }

    private static Line hint(KeyMapping key, String text) {
        return new Line(name(key), Component.translatable(text), TEXT);
    }

    /** The bound key's name; keypad keys get a "Num" prefix, since their names alone ("+") match main keys. */
    private static Component name(KeyMapping key) {
        InputConstants.Key bound = key.getKey();
        if (bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() >= GLFW.GLFW_KEY_KP_0 && bound.getValue() <= GLFW.GLFW_KEY_KP_EQUAL) {
            return Component.translatable("heavymatica.overlay.key.numpad", key.getTranslatedKeyMessage());
        }
        return key.getTranslatedKeyMessage();
    }

    private static Component keys(KeyMapping... keys) {
        Component joined = Component.empty();
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                joined = joined.copy().append(" ");
            }
            joined = joined.copy().append(name(keys[i]));
        }
        return joined;
    }
}
