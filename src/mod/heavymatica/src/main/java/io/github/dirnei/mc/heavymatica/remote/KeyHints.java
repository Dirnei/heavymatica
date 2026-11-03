package io.github.dirnei.mc.heavymatica.remote;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.capture.CaptureSession;
import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.remote.KeyHintRules.Hint;
import io.github.dirnei.mc.heavymatica.remote.KeyHintRules.Selected;
import io.github.dirnei.mc.heavymatica.remote.KeyHintRules.Situation;
import io.github.dirnei.mc.heavymatica.remote.KeyHintRules.WheelKey;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The key hints in the bottom-right corner: for a locked placement a header with its layer setting first, then the keys
 * that work in the current state, naming the keys the player actually bound.
 */
public final class KeyHints {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "key_hints");

    private static final int PADDING = 6;
    private static final int ROW = 14;
    private static final int HEADER = 12;
    private static final int GAP = 5;
    private static final int KEYCAP_HEIGHT = 12;

    /** A row: the key (empty for the header) and what it does. */
    private record Row(Component key, Component text, int color) {
    }

    private final Supplier<@Nullable PlacementSession> sessions;

    /** The session is looked up per frame: GUI layers are registered before the client services exist. */
    public KeyHints(Supplier<@Nullable PlacementSession> sessions) {
        this.sessions = sessions;
    }

    public void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ID, this::render);
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        PlacementSession session = sessions.get();
        if (session == null || minecraft.player == null) {
            return;
        }
        Optional<Placement> selected = session.placements().selected();
        Situation situation = situation(session, selected.orElse(null));
        if (!KeyHintRules.shows(minecraft.options.hideGui, minecraft.screen != null, HeavymaticaConfig.overlayKeyHints(),
                HeavymaticaConfig.overlayPinned(), situation)) {
            return;
        }
        List<Row> rows = rows(minecraft, situation, selected.orElse(null));
        Font font = minecraft.font;
        int inner = rows.stream().mapToInt(row -> width(font, row)).max().orElse(0);
        int width = inner + 2 * PADDING;
        int height = rows.stream().mapToInt(KeyHints::height).sum() + 2 * PADDING - 2;
        int bottom = KeyHintRules.bottom(graphics.guiWidth(), graphics.guiHeight(), width, minecraft.options.showSubtitles().get());
        int x = graphics.guiWidth() - KeyHintRules.MARGIN - width;
        int y = bottom - height;
        Glass.panel(graphics, x, y, width, height, Glass.SMALL_RADIUS, 0xB81C232C, 0xB30E1218, 0x14FFFFFF);
        int right = x + width - PADDING;
        int rowY = y + PADDING;
        for (Row row : rows) {
            draw(graphics, font, row, right, rowY);
            rowY += height(row);
        }
    }

    private static int height(Row row) {
        return row.key().getString().isEmpty() ? HEADER : ROW;
    }

    private static int width(Font font, Row row) {
        if (row.key().getString().isEmpty()) {
            return font.width(row.text());
        }
        return font.width(row.text()) + GAP + Glass.keycapWidth(font, row.key());
    }

    /** Right-aligned: the action, then the key as a keycap at the right edge; a header is plain text. */
    private static void draw(GuiGraphicsExtractor graphics, Font font, Row row, int right, int y) {
        if (row.key().getString().isEmpty()) {
            Glass.textRight(graphics, font, row.text().getString(), right, y, row.color());
            return;
        }
        int capWidth = Glass.keycapWidth(font, row.key());
        int capX = right - capWidth;
        Glass.box(graphics, capX, y, capWidth, KEYCAP_HEIGHT, 3, 0x1FFFFFFF, 0x38FFFFFF);
        graphics.fill(capX + 1, y + KEYCAP_HEIGHT - 1, capX + capWidth - 1, y + KEYCAP_HEIGHT, 0x38FFFFFF);
        graphics.text(font, row.key(), capX + (capWidth - font.width(row.key())) / 2, y + 2, GlassStyle.ACCENT, false);
        Glass.textRight(graphics, font, row.text().getString(), capX - GAP, y + 2, row.color());
    }

    private static Situation situation(PlacementSession session, @Nullable Placement placement) {
        CaptureSession capture = session.capture();
        int corners = capture.box().map(box -> box.corners()).orElse(0);
        Selected selected = placement == null ? Selected.NONE : switch (placement.state()) {
            case FOLLOWING -> Selected.FOLLOWING;
            case ANCHORED -> Selected.ANCHORED;
            case LOCKED -> Selected.LOCKED;
        };
        Layers.Mode layers = placement == null ? Layers.Mode.OFF : placement.layers().mode();
        long now = System.currentTimeMillis();
        return new Situation(Remotes.held(), capture.on(), corners, capture.progress().isPresent(), selected, layers,
                session.controller().removePending(now), session.controller().unlockPending(now), bound());
    }

    private static Set<WheelKey> bound() {
        Set<WheelKey> bound = EnumSet.noneOf(WheelKey.class);
        if (!RemoteKeys.LOCK.isUnbound()) {
            bound.add(WheelKey.LOCK);
        }
        if (!RemoteKeys.REMOVE.isUnbound()) {
            bound.add(WheelKey.REMOVE);
        }
        if (!RemoteKeys.MATERIALS.isUnbound()) {
            bound.add(WheelKey.MATERIALS);
        }
        if (!RemoteKeys.IN_HAND.isUnbound()) {
            bound.add(WheelKey.IN_HAND);
        }
        if (!RemoteKeys.CYCLE_LAYERS.isUnbound()) {
            bound.add(WheelKey.CYCLE_LAYERS);
        }
        return bound;
    }

    private static List<Row> rows(Minecraft minecraft, Situation situation, @Nullable Placement placement) {
        List<Row> rows = new ArrayList<>();
        if (KeyHintRules.header(situation) && placement != null) {
            rows.add(new Row(Component.empty(), header(placement), GlassStyle.ACCENT));
        }
        for (Hint hint : KeyHintRules.hints(situation)) {
            rows.add(row(minecraft, hint));
        }
        return rows;
    }

    /** The hints as text, for development checks. */
    public static List<String> textForDevelopment(PlacementSession session) {
        Optional<Placement> selected = session.placements().selected();
        Situation situation = situation(session, selected.orElse(null));
        return rows(Minecraft.getInstance(), situation, selected.orElse(null)).stream()
                .map(row -> row.key().getString().isEmpty() ? row.text().getString() : row.key().getString() + "  " + row.text().getString())
                .toList();
    }

    /** The layer setting and its layers, for the header. */
    static Component header(Placement placement) {
        int height = placement.height();
        Layers.Range range = Layers.visible(placement, PlacementSession.feetLayer(placement));
        return switch (placement.layers().mode()) {
            case OFF -> Component.translatable("heavymatica.hints.layers.off");
            case FROM_TO -> Component.translatable("heavymatica.hints.layers.from_to", range.from(), range.to(), height);
            case FOLLOW -> Component.translatable("heavymatica.hints.layers.follow", placement.layers().followOffset(), range.from(), range.to(), height);
        };
    }

    private static Row row(Minecraft minecraft, Hint hint) {
        int muted = GlassStyle.LABEL;
        return switch (hint) {
            case SELECT -> hint(minecraft.options.keyUse, "heavymatica.overlay.hint.select");
            case ANCHOR -> hint(minecraft.options.keyUse, "heavymatica.overlay.hint.anchor");
            case MOVE -> new Row(arrows(), Component.translatable("heavymatica.overlay.hint.move"), muted);
            case UP_DOWN -> new Row(keys(RemoteKeys.MOVE_UP, RemoteKeys.MOVE_DOWN), Component.translatable("heavymatica.overlay.hint.up_down"), muted);
            case ROTATE -> hint(RemoteKeys.ROTATE, "heavymatica.overlay.hint.rotate");
            case MIRROR -> hint(RemoteKeys.MIRROR, "heavymatica.overlay.hint.mirror");
            case RELEASE -> new Row(Component.translatable("heavymatica.overlay.key.sneak_use", minecraft.options.keyShift.getTranslatedKeyMessage(),
                    minecraft.options.keyUse.getTranslatedKeyMessage()), Component.translatable("heavymatica.overlay.hint.release"), muted);
            case MENU -> hint(RemoteKeys.WHEEL, "heavymatica.overlay.hint.menu");
            case LOCK -> hint(RemoteKeys.LOCK, "heavymatica.overlay.hint.lock");
            case UNLOCK -> hint(RemoteKeys.LOCK, "heavymatica.overlay.hint.unlock");
            case REMOVE -> hint(RemoteKeys.REMOVE, "heavymatica.overlay.hint.remove");
            case CANCEL -> hint(RemoteKeys.REMOVE, "heavymatica.overlay.hint.cancel");
            case MATERIALS -> hint(RemoteKeys.MATERIALS, "heavymatica.overlay.hint.materials");
            case IN_HAND -> hint(RemoteKeys.IN_HAND, "heavymatica.overlay.hint.in_hand");
            case CYCLE_LAYERS -> hint(RemoteKeys.CYCLE_LAYERS, "heavymatica.overlay.hint.cycle_layers");
            case WINDOW -> new Row(pair(RemoteKeys.MOVE_FORWARD, RemoteKeys.MOVE_BACK), Component.translatable("heavymatica.overlay.hint.window"), muted);
            case WINDOW_SIZE -> new Row(pair(RemoteKeys.MOVE_UP, RemoteKeys.MOVE_DOWN), Component.translatable("heavymatica.overlay.hint.window_size"), muted);
            case FOLLOW_HEIGHT -> new Row(pair(RemoteKeys.MOVE_UP, RemoteKeys.MOVE_DOWN), Component.translatable("heavymatica.overlay.hint.follow_height"),
                    muted);
            case REMOVE_AGAIN -> new Row(name(RemoteKeys.REMOVE), Component.translatable("heavymatica.overlay.remove_again"), GlassStyle.DANGER_TEXT);
            case UNLOCK_AGAIN -> new Row(name(RemoteKeys.LOCK), Component.translatable("heavymatica.overlay.unlock_again"), GlassStyle.DANGER_TEXT);
            case CORNER1 -> hint(minecraft.options.keyUse, "heavymatica.overlay.hint.corner1");
            case CORNER2 -> hint(minecraft.options.keyUse, "heavymatica.overlay.hint.corner2");
            case MOVE_CORNER_CLICK -> hint(minecraft.options.keyUse, "heavymatica.overlay.hint.move_corner_click");
            case MOVE_CORNER -> new Row(arrows(), Component.translatable("heavymatica.overlay.hint.move_corner"), muted);
            case CORNER_UP_DOWN -> new Row(keys(RemoteKeys.MOVE_UP, RemoteKeys.MOVE_DOWN), Component.translatable("heavymatica.overlay.hint.corner_up_down"),
                    muted);
            case SWAP_CORNER -> new Row(RemoteKeys.SWAP_CORNER.isUnbound() ? Component.translatable("heavymatica.overlay.key.shift_scroll",
                    minecraft.options.keyShift.getTranslatedKeyMessage()) : name(RemoteKeys.SWAP_CORNER),
                    Component.translatable("heavymatica.overlay.hint.swap_corner"), muted);
            case CAPTURE_MENU -> hint(RemoteKeys.WHEEL, "heavymatica.overlay.hint.capture_menu");
        };
    }

    private static Row hint(KeyMapping key, String text) {
        return new Row(name(key), Component.translatable(text), GlassStyle.LABEL);
    }

    private static Component arrows() {
        boolean arrows = RemoteKeys.MOVE_FORWARD.isDefault() && RemoteKeys.MOVE_BACK.isDefault() && RemoteKeys.MOVE_LEFT.isDefault()
                && RemoteKeys.MOVE_RIGHT.isDefault();
        return arrows ? Component.translatable("heavymatica.overlay.key.arrows")
                : keys(RemoteKeys.MOVE_FORWARD, RemoteKeys.MOVE_BACK, RemoteKeys.MOVE_LEFT, RemoteKeys.MOVE_RIGHT);
    }

    private static Component pair(KeyMapping first, KeyMapping second) {
        return Component.empty().append(name(first)).append(" / ").append(name(second));
    }

    /** The bound key's name; keypad keys get a "Num" prefix, since their names alone ("+") match main keys. */
    static Component name(KeyMapping key) {
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
