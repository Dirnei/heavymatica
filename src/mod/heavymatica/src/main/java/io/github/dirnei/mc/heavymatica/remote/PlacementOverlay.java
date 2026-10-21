package io.github.dirnei.mc.heavymatica.remote;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementSource;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform;
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
        lines.add(Line.text(Component.translatable(placement.following() ? "heavymatica.overlay.following" : "heavymatica.overlay.anchored"),
                placement.following() ? FOLLOWING : ANCHORED));
        if (HeavymaticaConfig.overlayCompact()) {
            return lines;
        }
        PlacementTransform.Bounds bounds = PlacementTransform.bounds(placement);
        lines.add(Line.text(Component.translatable("heavymatica.overlay.position", bounds.minX(), bounds.minY(), bounds.minZ()), MUTED));
        lines.add(Line.text(Component.translatable("heavymatica.overlay.rotation", placement.rotation().degrees(),
                Component.translatable("heavymatica.overlay.mirror." + placement.mirror().name().toLowerCase(Locale.ROOT))), MUTED));
        lines.add(SEPARATOR);
        if (session.unavailable(placement)) {
            lines.add(Line.text(Component.translatable("heavymatica.overlay.unavailable"), WARNING));
        }
        if (session.controller().removePending(System.currentTimeMillis())) {
            lines.add(new Line(name(RemoteKeys.REMOVE), Component.translatable("heavymatica.overlay.remove_again"), WARNING));
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
        lines.add(hint(RemoteKeys.REMOVE, placement.following() ? "heavymatica.overlay.hint.cancel" : "heavymatica.overlay.hint.remove"));
        return lines;
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
