package io.github.dirnei.mc.heavymatica.targeting;

import io.github.dirnei.mc.heavymatica.platform.Platform;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The mistake pointer below the crosshair, under the targeting HUD when that shows: an arrow toward the candidate, its
 * height and distance, and its state; or that nothing is left in the pointer's mode.
 */
public final class PointerHud {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "mistake_pointer");

    private static final int PADDING = 5;
    private static final int LINE = 10;
    private static final int GAP = 4;

    private final Supplier<@Nullable MistakePointer> pointer;

    /** Looked up per frame: GUI layers are registered before the client services exist. */
    public PointerHud(Supplier<@Nullable MistakePointer> pointer) {
        this.pointer = pointer;
    }

    public void register() {
        Platform.get().hudLayers().above(TargetHud.ID, ID, this::render);
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        List<TargetHud.Line> lines = shownLines(minecraft, pointer.get());
        if (lines == null) {
            return;
        }
        Font font = minecraft.font;
        int width = 0;
        for (TargetHud.Line line : lines) {
            width = Math.max(width, font.width(line.text()));
        }
        width += 2 * PADDING;
        int height = lines.size() * LINE + 2 * PADDING - 2;
        int x = (graphics.guiWidth() - width) / 2;
        int below = TargetHud.bottomThisFrame();
        int y = below >= 0 ? below + GAP : graphics.guiHeight() / 2 + TargetHud.GAP_BELOW_CROSSHAIR;
        Glass.panel(graphics, x, y, width, height, Math.min(8, height / 2), 0xC71C232C, 0xC70E1218, 0x1AFFFFFF);
        int lineY = y + PADDING;
        for (TargetHud.Line line : lines) {
            graphics.centeredText(font, line.text(), x + width / 2, lineY, line.color());
            lineY += LINE;
        }
    }

    /** The lines to show, or null while the pointer is hidden. */
    private static @Nullable List<TargetHud.Line> shownLines(Minecraft minecraft, @Nullable MistakePointer pointer) {
        MistakePointer.Result result = pointer == null ? null : pointer.current();
        if (result == null || minecraft.options.hideGui || minecraft.screen != null || minecraft.player == null) {
            return null;
        }
        return lines(result, minecraft.player.getEyePosition(), minecraft.player.getYRot());
    }

    /** The direction line and the state line toward the target, or the line saying nothing is left. */
    public static List<TargetHud.Line> lines(MistakePointer.Result result, Vec3 eye, float yaw) {
        MistakePointer.Target target = result.target();
        if (target == null) {
            String key = result.mode() == PointerMode.MISTAKES_AND_MISSING ? "heavymatica.pointer.nothing_left" : "heavymatica.pointer.no_mistakes";
            return List.of(new TargetHud.Line(Component.translatable(key), TargetLines.MUTED));
        }
        double dx = target.pos().getX() + 0.5 - eye.x;
        double dy = target.pos().getY() + 0.5 - eye.y;
        double dz = target.pos().getZ() + 0.5 - eye.z;
        List<String> parts = new ArrayList<>();
        String arrow = PointerDirection.arrow(PointerDirection.step(yaw, dx, dz));
        if (!arrow.isEmpty()) {
            parts.add(arrow);
        }
        PointerDirection.Height height = PointerDirection.height(target.pos().getY(), eye.y);
        if (height != null) {
            parts.add(Component.translatable(height.key(), height.blocks()).getString());
        }
        long distance = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
        parts.add(Component.translatable(distance == 1 ? "heavymatica.pointer.distance.one" : "heavymatica.pointer.distance", distance).getString());
        return List.of(new TargetHud.Line(Component.literal(String.join("  ", parts)), TargetLines.TEXT), stateLine(target.state()));
    }

    static TargetHud.Line stateLine(CellState state) {
        TargetLines.StateLine line = TargetLines.state(state, null, false);
        int color = line == null ? TargetLines.TEXT : line.color();
        return new TargetHud.Line(Component.translatable("heavymatica.pointer.state." + state.name().toLowerCase(Locale.ROOT)), color);
    }

    /** The pointer's lines as text, or why it is hidden, for development checks. */
    public static String textForDevelopment(Minecraft minecraft, @Nullable MistakePointer pointer) {
        if (pointer == null || pointer.current() == null) {
            return "hidden (off or no locked placement selected)";
        }
        List<TargetHud.Line> lines = shownLines(minecraft, pointer);
        if (lines == null) {
            return "hidden (GUI hidden or screen open)";
        }
        MistakePointer.Target target = pointer.current().target();
        List<String> text = new ArrayList<>();
        lines.forEach(line -> text.add(line.text().getString() + " #" + String.format(Locale.ROOT, "%06X", line.color() & 0xFFFFFF)));
        return String.join(" | ", text) + (target == null ? "" : " @ " + target.pos().toShortString());
    }
}
