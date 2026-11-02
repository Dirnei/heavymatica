package io.github.dirnei.mc.heavymatica.remote;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaClient;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.capture.CaptureBox;
import io.github.dirnei.mc.heavymatica.capture.CaptureSession;
import io.github.dirnei.mc.heavymatica.paste.CreativePaste;
import io.github.dirnei.mc.heavymatica.placement.BlockEquivalents;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.placement.ComparisonState;
import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.placement.MaterialList;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementSource;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform;
import io.github.dirnei.mc.heavymatica.placement.UnknownBlocks;
import io.github.dirnei.mc.heavymatica.placement.WorldComparison;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
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
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The HUD panel shown while the remote is held: the selected placement's name, version, state badge, position,
 * rotation, mirror and swaps, and hints for the keys that work in its state, naming the keys the player actually bound.
 * Actions on the wheel only get a hint when the player bound a key for them.
 */
public final class PlacementOverlay {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "placement_overlay");

    private static final int PADDING = 8;
    private static final int LINE = 10;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFFAAAAAA;
    private static final int WARNING = GlassStyle.DANGER_TEXT;
    private static final int FOLLOWING = GlassStyle.WARNING;
    private static final int ANCHORED = GlassStyle.CONFIRM;
    private static final int LOCKED = GlassStyle.ACCENT;
    public static final int MISTAKE_RED = GlassStyle.MISTAKE_RED;
    public static final int MISTAKE_ORANGE = GlassStyle.MISTAKE_ORANGE;
    public static final int MISTAKE_CYAN = GlassStyle.MISTAKE_CYAN;
    public static final int MISTAKE_MAGENTA = GlassStyle.MISTAKE_MAGENTA;
    public static final int FINISHING_YELLOW = GlassStyle.WARNING;
    private static final int CAPTURE = 0xFF80FF80;
    private static final int SHORTAGE_LINES = 3;

    /** How an overlay line is drawn. */
    enum Kind {
        /** The placement's name with its version or local mark, and the state as a badge. */
        TITLE,
        TEXT,
        /** Smaller, muted details such as position and rotation. */
        SMALL,
        /** The progress text over a bar of built, carried and short. */
        PROGRESS,
        /** Coloured counts side by side. */
        CHIPS,
        /** A small heading. */
        SECTION,
        /** An item that is short, with how many on the right. */
        SHORTAGE,
        /** A key as a keycap, then what it does. */
        HINT,
        SEPARATOR,
    }

    /** A coloured piece of a {@link Kind#CHIPS} line. */
    record Chip(Component text, int color) {
    }

    /**
     * One overlay line. {@code key} is a hint's key, a title's state badge, or a shortage's count; {@code chips} are the
     * counts of a chips line; {@code bar} holds built, carried, short and planned of a progress line.
     */
    record Line(Kind kind, Component key, Component text, int color, List<Chip> chips, long[] bar) {

        Line(Component key, Component text, int color) {
            this(key.getString().isEmpty() ? Kind.TEXT : Kind.HINT, key, text, color, List.of(), new long[0]);
        }

        static Line text(Component text, int color) {
            return new Line(Kind.TEXT, Component.empty(), text, color, List.of(), new long[0]);
        }

        static Line of(Kind kind, Component text, int color) {
            return new Line(kind, Component.empty(), text, color, List.of(), new long[0]);
        }

        /** The line as plain text, for development checks. */
        String plain() {
            return switch (kind) {
                case CHIPS -> String.join("  ", chips.stream().map(chip -> chip.text().getString()).toList());
                case TITLE -> text.getString() + "  " + key.getString();
                case HINT -> key.getString() + "  " + text.getString();
                case SHORTAGE -> text.getString() + ": " + key.getString() + " short";
                default -> text.getString();
            };
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

    private static int height(Line line) {
        return switch (line.kind()) {
            case TITLE -> 16;
            case PROGRESS -> 20;
            case CHIPS -> 15;
            case HINT -> 14;
            case SEPARATOR -> 7;
            case SECTION -> 11;
            default -> LINE;
        };
    }

    private static int width(Font font, Line line) {
        return switch (line.kind()) {
            case TITLE -> font.width(line.text()) + 8 + Glass.chipWidth(font, line.key().getString());
            case CHIPS -> line.chips().stream().mapToInt(chip -> Glass.chipWidth(font, chip.text().getString()) + 4).sum();
            case HINT -> Glass.keycapWidth(font, line.key()) + 5 + font.width(line.text());
            case SHORTAGE -> font.width(line.text()) + 30;
            case PROGRESS -> font.width(line.text()) + 10;
            case SEPARATOR -> 0;
            default -> font.width(line.text());
        };
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        PlacementSession session = sessions.get();
        if (session == null || minecraft.options.hideGui || minecraft.screen != null || minecraft.player == null || !Remotes.held()) {
            return;
        }
        List<Line> lines = lines(minecraft, session);
        Font font = minecraft.font;
        int maxWidth = Math.max(150, graphics.guiWidth() / 3);
        int inner = Math.min(maxWidth - 2 * PADDING, Math.max(120, lines.stream().mapToInt(line -> width(font, line)).max().orElse(0)));
        int width = inner + 2 * PADDING;
        int height = lines.stream().mapToInt(PlacementOverlay::height).sum() + 2 * PADDING - 2;
        int x = HeavymaticaConfig.overlaySide() == HeavymaticaConfig.OverlaySide.LEFT ? 6 : graphics.guiWidth() - width - 6;
        int y = Math.max(4, (graphics.guiHeight() - height) / 2);
        Glass.panel(graphics, x, y, width, height, Glass.RADIUS, 0xD61C232C, 0xD10E1218, 0x1AFFFFFF);
        int lineY = y + PADDING;
        int left = x + PADDING;
        for (Line line : lines) {
            drawLine(graphics, font, line, left, lineY, inner);
            lineY += height(line);
        }
    }

    private static void drawLine(GuiGraphicsExtractor graphics, Font font, Line line, int x, int y, int width) {
        switch (line.kind()) {
            case TITLE -> {
                String badge = line.key().getString();
                int badgeWidth = badge.isEmpty() ? 0 : Glass.chipWidth(font, badge);
                Glass.text(graphics, font, line.text().getString(), x, y + 2, GlassStyle.TEXT, width - badgeWidth - 6);
                if (!badge.isEmpty()) {
                    Glass.chip(graphics, font, badge, x + width - badgeWidth, y, GlassStyle.withAlpha(line.color(), 0x24), line.color(),
                            GlassStyle.withAlpha(line.color(), 0x99));
                }
            }
            case PROGRESS -> {
                Glass.text(graphics, font, line.text().getString(), x, y, line.color(), width);
                long total = line.bar().length > 3 ? line.bar()[3] : 0;
                if (line.bar().length > 2) {
                    Glass.bar(graphics, x, y + 11, width, 5, new long[] {line.bar()[0], line.bar()[1], line.bar()[2]}, Math.max(1, total),
                            new int[] {GlassStyle.BUILT, GlassStyle.CARRIED, GlassStyle.SHORT});
                }
            }
            case CHIPS -> {
                int chipX = x;
                for (Chip chip : line.chips()) {
                    String text = chip.text().getString();
                    if (chipX + Glass.chipWidth(font, text) > x + width) {
                        break;
                    }
                    chipX += Glass.chip(graphics, font, text, chipX, y, GlassStyle.withAlpha(chip.color(), 0x24), chip.color(), 0) + 4;
                }
            }
            case SECTION -> graphics.text(font, line.text().getString().toUpperCase(Locale.ROOT), x, y + 1, GlassStyle.HINT, false);
            case SHORTAGE -> {
                Glass.text(graphics, font, line.text().getString(), x, y, line.color(), width - 28);
                Glass.textRight(graphics, font, line.key().getString(), x + width, y, GlassStyle.SHORT_TEXT);
            }
            case HINT -> {
                int capWidth = Glass.keycap(graphics, font, line.key(), x, y);
                Glass.text(graphics, font, line.text().getString(), x + capWidth + 5, y + 2, line.color(), width - capWidth - 5);
            }
            case SEPARATOR -> graphics.fill(x, y + 3, x + width, y + 4, 0x1FFFFFFF);
            case SMALL -> Glass.text(graphics, font, line.text().getString(), x, y, GlassStyle.SUBTITLE, width);
            case TEXT -> Glass.text(graphics, font, line.text().getString(), x, y, line.color(), width);
        }
    }

    private static final Line SEPARATOR = new Line(Kind.SEPARATOR, Component.empty(), Component.empty(), 0, List.of(), new long[0]);

    /** The overlay's lines as text, for development checks. */
    public static List<String> textForDevelopment(Minecraft minecraft, PlacementSession session) {
        return lines(minecraft, session).stream().filter(line -> line.kind() != Kind.SEPARATOR).map(Line::plain).toList();
    }

    static List<Line> lines(Minecraft minecraft, PlacementSession session) {
        List<Line> lines = new ArrayList<>();
        CreativePaste paste = HeavymaticaClient.paste();
        if (paste != null) {
            paste.progress().ifPresent(progress -> lines.add(Line.text(Component.translatable(progress.undo() ? "heavymatica.overlay.undoing"
                    : "heavymatica.overlay.pasting", (int) Math.floor(progress.share() * 100)), CAPTURE)));
        }
        if (session.capture().on()) {
            captureLines(minecraft, session.capture(), lines);
            return lines;
        }
        Optional<Placement> selected = session.placements().selected();
        if (selected.isEmpty()) {
            lines.add(Line.text(Component.translatable("heavymatica.overlay.none"), TEXT));
            if (!HeavymaticaConfig.overlayCompact()) {
                lines.add(SEPARATOR);
                lines.add(hint(minecraft.options.keyUse, "heavymatica.overlay.hint.select"));
                lines.add(hint(RemoteKeys.WHEEL, "heavymatica.overlay.hint.menu"));
            }
            return lines;
        }
        Placement placement = selected.get();
        Component version = placement.source() instanceof PlacementSource.Cloud cloud
                ? Component.literal("v" + cloud.version())
                : Component.translatable("heavymatica.overlay.local");
        Component badge = Component.translatable(switch (placement.state()) {
            case FOLLOWING -> "heavymatica.overlay.following";
            case ANCHORED -> "heavymatica.overlay.anchored";
            case LOCKED -> "heavymatica.overlay.locked";
        });
        int badgeColor = switch (placement.state()) {
            case FOLLOWING -> FOLLOWING;
            case ANCHORED -> ANCHORED;
            case LOCKED -> LOCKED;
        };
        lines.add(new Line(Kind.TITLE, badge, Component.literal(placement.name() + "  ").append(version), badgeColor, List.of(), new long[0]));
        if (HeavymaticaConfig.overlayCompact()) {
            return lines;
        }
        PlacementTransform.Bounds bounds = PlacementTransform.bounds(placement);
        lines.add(Line.of(Kind.SMALL, Component.translatable("heavymatica.overlay.position", bounds.minX(), bounds.minY(), bounds.minZ()).append("   ")
                .append(Component.translatable("heavymatica.overlay.rotation", placement.rotation().degrees(),
                        Component.translatable("heavymatica.overlay.mirror." + placement.mirror().name().toLowerCase(Locale.ROOT)))), MUTED));
        int swaps = placement.swaps().size();
        if (swaps > 0) {
            lines.add(Line.of(Kind.SMALL, Component.translatable(swaps == 1 ? "heavymatica.overlay.swaps.one" : "heavymatica.overlay.swaps", swaps), MUTED));
        }
        UnknownBlocks unknown = session.unknownBlocks(placement);
        if (unknown.any()) {
            lines.add(Line.text(Component.translatable(HeavymaticaConfig.ghostShowUnknownBlocks() ? "heavymatica.overlay.unknown" : "heavymatica.overlay.unknown.hidden",
                    unknown.types(), unknown.total()), WARNING));
        }
        if (placement.locked()) {
            lines.add(Line.text(layersText(placement), TEXT));
            lines.add(Line.text(inHandText(placement), TEXT));
            WorldComparison comparison = session.comparison(placement);
            MaterialList<Item, Block> materials = session.materials(placement);
            if (comparison != null) {
                comparisonLines(minecraft, placement, comparison, materials, lines);
            }
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
            lines.add(hint(RemoteKeys.WHEEL, "heavymatica.overlay.hint.menu"));
            boundHint(lines, RemoteKeys.CYCLE_LAYERS, "heavymatica.overlay.hint.cycle_layers");
            boundHint(lines, RemoteKeys.IN_HAND, "heavymatica.overlay.hint.in_hand");
            boundHint(lines, RemoteKeys.MATERIALS, "heavymatica.overlay.hint.materials");
            boundHint(lines, RemoteKeys.LOCK, "heavymatica.overlay.hint.unlock");
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
        lines.add(hint(RemoteKeys.WHEEL, "heavymatica.overlay.hint.menu"));
        if (!placement.following()) {
            boundHint(lines, RemoteKeys.LOCK, "heavymatica.overlay.hint.lock");
        }
        boundHint(lines, RemoteKeys.REMOVE, placement.following() ? "heavymatica.overlay.hint.cancel" : "heavymatica.overlay.hint.remove");
        boundHint(lines, RemoteKeys.MATERIALS, "heavymatica.overlay.hint.materials");
        return lines;
    }

    private static void captureLines(Minecraft minecraft, CaptureSession capture, List<Line> lines) {
        CaptureBox box = capture.box().orElseThrow();
        lines.add(Line.text(Component.translatable("heavymatica.overlay.capture"), CAPTURE));
        Optional<CaptureBox.Bounds> bounds = box.bounds();
        if (bounds.isPresent()) {
            CaptureBox.Bounds size = bounds.get();
            lines.add(Line.text(Component.translatable(size.withinLimits() ? "heavymatica.overlay.capture.size" : "heavymatica.overlay.capture.too_large",
                    size.width(), size.height(), size.length()), size.withinLimits() ? TEXT : WARNING));
        }
        box.active().ifPresent(corner -> lines.add(Line.text(Component.translatable(box.secondActive()
                ? "heavymatica.overlay.capture.corner2" : "heavymatica.overlay.capture.corner1", corner.x(), corner.y(), corner.z()), MUTED)));
        Optional<CaptureSession.Progress> progress = capture.progress();
        if (progress.isPresent()) {
            lines.add(Line.text(progress.get().saving() ? Component.translatable("heavymatica.overlay.capture.saving")
                    : Component.translatable("heavymatica.overlay.capture.reading", (int) Math.floor(progress.get().read() * 100)), TEXT));
            return;
        }
        if (HeavymaticaConfig.overlayCompact()) {
            return;
        }
        lines.add(SEPARATOR);
        lines.add(hint(minecraft.options.keyUse, box.corners() == 0 ? "heavymatica.overlay.hint.corner1"
                : box.corners() == 1 ? "heavymatica.overlay.hint.corner2" : "heavymatica.overlay.hint.move_corner_click"));
        if (box.corners() > 0) {
            boolean arrows = RemoteKeys.MOVE_FORWARD.isDefault() && RemoteKeys.MOVE_BACK.isDefault() && RemoteKeys.MOVE_LEFT.isDefault()
                    && RemoteKeys.MOVE_RIGHT.isDefault();
            lines.add(new Line(arrows ? Component.translatable("heavymatica.overlay.key.arrows")
                    : keys(RemoteKeys.MOVE_FORWARD, RemoteKeys.MOVE_BACK, RemoteKeys.MOVE_LEFT, RemoteKeys.MOVE_RIGHT),
                    Component.translatable("heavymatica.overlay.hint.move_corner"), TEXT));
            lines.add(new Line(keys(RemoteKeys.MOVE_UP, RemoteKeys.MOVE_DOWN), Component.translatable("heavymatica.overlay.hint.corner_up_down"), TEXT));
        }
        if (box.corners() == 2) {
            lines.add(new Line(RemoteKeys.SWAP_CORNER.isUnbound() ? Component.translatable("heavymatica.overlay.key.shift_scroll",
                    minecraft.options.keyShift.getTranslatedKeyMessage()) : name(RemoteKeys.SWAP_CORNER),
                    Component.translatable("heavymatica.overlay.hint.swap_corner"), TEXT));
        }
        lines.add(hint(RemoteKeys.WHEEL, "heavymatica.overlay.hint.capture_menu"));
    }

    /** The layer setting, the shown layers and the placement's height. */
    static Component layersText(Placement placement) {
        int height = placement.height();
        Layers.Range range = Layers.visible(placement, PlacementSession.feetLayer(placement));
        return switch (placement.layers().mode()) {
            case OFF -> Component.translatable("heavymatica.overlay.layers.all", height);
            case FROM_TO -> Component.translatable("heavymatica.overlay.layers.from_to", range.from(), range.to(), height);
            case FOLLOW -> Component.translatable("heavymatica.overlay.layers.follow", placement.layers().followOffset(), range.from(), range.to(),
                    height);
        };
    }

    /**
     * Progress, layer progress, the mistake counts, the positions to finish in the visible layers per step and, when
     * the crosshair is on a mistake, a block to finish or an incomplete block, what belongs there.
     */
    private static void comparisonLines(Minecraft minecraft, Placement placement, WorldComparison comparison, @Nullable MaterialList<Item, Block> materials,
                                        List<Line> lines) {
        ComparisonState state = comparison.state();
        if (state.scanning()) {
            lines.add(Line.text(Component.translatable("heavymatica.overlay.progress.scanning"), MUTED));
        } else {
            int matching = state.matching(0, state.sizeY() - 1);
            int counted = state.counted(0, state.sizeY() - 1);
            long[] bar;
            if (materials != null) {
                MaterialList.Bar total = materials.total();
                bar = new long[] {total.built(), total.carried(), total.shortage(), total.planned()};
            } else {
                bar = new long[] {matching, 0, 0, Math.max(1, counted)};
            }
            lines.add(new Line(Kind.PROGRESS, Component.empty(), progress("heavymatica.overlay.progress", matching, counted), TEXT, List.of(), bar));
            if (placement.layers().mode() != Layers.Mode.OFF) {
                Layers.Range range = Layers.visible(placement, PlacementSession.feetLayer(placement));
                lines.add(Line.text(progress("heavymatica.overlay.progress.layers", state.matching(range.from(), range.to()),
                        state.counted(range.from(), range.to())), TEXT));
            }
        }
        List<Chip> counts = new ArrayList<>();
        count(counts, state, CellState.WRONG_BLOCK, "wrong_block", MISTAKE_RED);
        count(counts, state, CellState.WRONG_ORIENTATION, "wrong_orientation", MISTAKE_ORANGE);
        count(counts, state, CellState.TOO_MANY, "too_many", MISTAKE_ORANGE);
        count(counts, state, CellState.WRONG_WATER, "wrong_water", MISTAKE_CYAN);
        count(counts, state, CellState.EXTRA, "extra", MISTAKE_MAGENTA);
        if (!counts.isEmpty()) {
            lines.add(new Line(Kind.CHIPS, Component.empty(), Component.empty(), TEXT, List.copyOf(counts), new long[0]));
        }
        finishingLines(placement, comparison, lines);
    }

    /** One line per finishing step that positions in the visible layers need. */
    private static void finishingLines(Placement placement, WorldComparison comparison, List<Line> lines) {
        Layers.Range range = Layers.visible(placement, PlacementSession.feetLayer(placement));
        int[] finishing = comparison.materials().sum(range.from(), range.to()).finishing();
        int[] steps = new int[BlockEquivalents.Finish.values().length];
        for (int block = 0; block < finishing.length; block++) {
            BlockEquivalents.Finish finish = comparison.finish(block);
            if (finish != null) {
                steps[finish.ordinal()] += finishing[block];
            }
        }
        List<Chip> chips = new ArrayList<>();
        for (BlockEquivalents.Finish finish : BlockEquivalents.Finish.values()) {
            int count = steps[finish.ordinal()];
            if (count > 0) {
                String key = "heavymatica.overlay.finish." + stepName(finish);
                chips.add(new Chip(Component.translatable(count == 1 ? key + ".one" : key, count), FINISHING_YELLOW));
            }
        }
        if (!chips.isEmpty()) {
            lines.add(new Line(Kind.CHIPS, Component.empty(), Component.empty(), FINISHING_YELLOW, List.copyOf(chips), new long[0]));
        }
    }

    private static String stepName(BlockEquivalents.Finish finish) {
        return finish.name().toLowerCase(Locale.ROOT);
    }

    /** The largest shortages of the visible layers, or that nothing is short. */
    private static void shortageLines(MaterialList<Item, Block> materials, List<Line> lines) {
        MaterialList.Shortages<Item> shortages = materials.shortages(SHORTAGE_LINES);
        if (shortages.none()) {
            lines.add(Line.text(Component.translatable("heavymatica.overlay.materials.nothing_short"), ANCHORED));
            return;
        }
        lines.add(Line.of(Kind.SECTION, Component.translatable("heavymatica.overlay.materials.fetch"), MUTED));
        for (MaterialList.Row<Item> row : shortages.top()) {
            lines.add(new Line(Kind.SHORTAGE, Component.literal(Integer.toString(row.shortage())), Component.translatable("heavymatica.overlay.materials.short",
                    row.item().getName(row.item().getDefaultInstance()), row.shortage()), TEXT, List.of(), new long[0]));
        }
        if (shortages.more() > 0) {
            lines.add(Line.of(Kind.SMALL, RemoteKeys.MATERIALS.isUnbound()
                    ? Component.translatable("heavymatica.overlay.materials.more_wheel", shortages.more(), name(RemoteKeys.WHEEL))
                    : Component.translatable("heavymatica.overlay.materials.more", shortages.more(), name(RemoteKeys.MATERIALS)), MUTED));
        }
    }

    private static Component progress(String key, int matching, int counted) {
        int percent = counted == 0 ? 100 : (int) (matching * 100L / counted);
        return Component.translatable(key, percent, matching, counted);
    }

    /** Adds a mistake count as a chip, unless it is 0. */
    private static void count(List<Chip> chips, ComparisonState state, CellState cell, String name, int color) {
        int total = state.total(cell);
        if (total > 0) {
            chips.add(new Chip(Component.translatable("heavymatica.overlay.count." + name, total), color));
        }
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

    /** A hint for an action that is also on the wheel, only when the player bound a key for it. */
    private static void boundHint(List<Line> lines, KeyMapping key, String text) {
        if (!key.isUnbound()) {
            lines.add(hint(key, text));
        }
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
