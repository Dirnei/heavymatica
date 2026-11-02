package io.github.dirnei.mc.heavymatica.ui.glass;

import io.github.dirnei.mc.heavymatica.remote.WheelMeshRenderState;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Draws glass shapes and fitted text. Each shape is its own GUI element, so the GUI layers it above whatever it covers
 * that was submitted before it, text included.
 */
public final class Glass {

    public static final int RADIUS = 8;
    public static final int SMALL_RADIUS = 5;
    public static final int LINE = 10;

    private Glass() {
    }

    /** One screen pixel in GUI units. */
    public static double feather() {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        return scale <= 0 ? 1 : 1 / scale;
    }

    public static GlassMesh mesh() {
        return new GlassMesh(feather());
    }

    public static void submit(GuiGraphicsExtractor graphics, GlassMesh mesh) {
        float[] bounds = mesh.bounds();
        if (bounds != null) {
            WheelMeshRenderState.submit(graphics, mesh.positions(), mesh.colors(), bounds);
        }
    }

    /** A glass panel: the body gradient with a faint rim. */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        panel(graphics, x, y, w, h, RADIUS, GlassStyle.BODY_TOP, GlassStyle.BODY_BOTTOM, GlassStyle.RIM);
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int top, int bottom, int rim) {
        GlassMesh mesh = mesh();
        mesh.roundRect(x, y, w, h, radius, top, bottom);
        if ((rim >>> 24) != 0) {
            mesh.rim(x, y, w, h, radius, feather(), rim);
        }
        submit(graphics, mesh);
    }

    public static void fill(GuiGraphicsExtractor graphics, double x, double y, double w, double h, double radius, int color) {
        GlassMesh mesh = mesh();
        mesh.roundRect(x, y, w, h, radius, color);
        submit(graphics, mesh);
    }

    public static void rim(GuiGraphicsExtractor graphics, double x, double y, double w, double h, double radius, double width, int color) {
        GlassMesh mesh = mesh();
        mesh.rim(x, y, w, h, radius, width, color);
        submit(graphics, mesh);
    }

    /** A filled shape with a rim on top of it. */
    public static void box(GuiGraphicsExtractor graphics, double x, double y, double w, double h, double radius, int fill, int rim) {
        GlassMesh mesh = mesh();
        mesh.roundRect(x, y, w, h, radius, fill);
        if ((rim >>> 24) != 0) {
            mesh.rim(x, y, w, h, radius, Math.max(feather(), 1), rim);
        }
        submit(graphics, mesh);
    }

    /** The screen behind a dialog or a Heavymatica screen, dimmed. */
    public static void dim(GuiGraphicsExtractor graphics, int width, int height, int color) {
        graphics.fill(0, 0, width, height, color);
    }

    /** A bar on a faint track: parts of {@code total} side by side in their colours. */
    public static void bar(GuiGraphicsExtractor graphics, double x, double y, double w, double h, long[] parts, long total, int[] colors) {
        GlassMesh mesh = mesh();
        mesh.roundRect(x, y, w, h, h / 2, 0x14FFFFFF);
        mesh.segmentedBar(x, y, w, h, parts, Math.max(total, 1), colors, Math.min(2, h / 3));
        submit(graphics, mesh);
    }

    /** Text cut to {@code maxWidth} with an ellipsis; returns the fit, so a caller can show the full text on hover. */
    public static TextFit text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, int maxWidth) {
        TextFit fit = TextFit.of(text, Math.max(0, maxWidth), font::width);
        graphics.text(font, fit.shown(), x, y, color, false);
        return fit;
    }

    public static TextFit text(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, int maxWidth) {
        return text(graphics, font, text.getString(), x, y, color, maxWidth);
    }

    /** Text right-aligned to {@code right}. */
    public static void textRight(GuiGraphicsExtractor graphics, Font font, String text, int right, int y, int color) {
        graphics.text(font, text, right - font.width(text), y, color, false);
    }

    /** Fitted text centred on {@code centreX}. */
    public static TextFit textCentred(GuiGraphicsExtractor graphics, Font font, String text, int centreX, int y, int color, int maxWidth) {
        TextFit fit = TextFit.of(text, Math.max(0, maxWidth), font::width);
        graphics.text(font, fit.shown(), centreX - font.width(fit.shown()) / 2, y, color, false);
        return fit;
    }

    /** Text scaled by a whole factor, for titles and big numbers. */
    public static void bigText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, int scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    /** Shows the full text as a tooltip when the mouse is over a shortened text's area. */
    public static void tooltipIfShortened(GuiGraphicsExtractor graphics, Font font, TextFit fit, int x, int y, int w, int h, int mouseX, int mouseY) {
        if (fit.shortened() && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            graphics.setTooltipForNextFrame(font, Component.literal(fit.full()), mouseX, mouseY);
        }
    }

    /** A rounded label: returns its width. */
    public static int chip(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int fill, int textColor, int rim) {
        int w = font.width(text) + 10;
        box(graphics, x, y, w, 13, 6.5, fill, rim);
        graphics.text(font, text, x + 5, y + 3, textColor, false);
        return w;
    }

    /** How wide {@link #chip} draws a text. */
    public static int chipWidth(Font font, String text) {
        return font.width(text) + 10;
    }

    /** A key drawn as a small keycap; returns its width. */
    public static int keycap(GuiGraphicsExtractor graphics, Font font, Component key, int x, int y) {
        int w = Math.max(11, font.width(key) + 6);
        box(graphics, x, y, w, 12, 3, 0x1FFFFFFF, 0x38FFFFFF);
        graphics.fill(x + 1, y + 11, x + w - 1, y + 12, 0x38FFFFFF);
        graphics.text(font, key, x + (w - font.width(key)) / 2, y + 2, GlassStyle.TEXT, false);
        return w;
    }

    public static int keycapWidth(Font font, Component key) {
        return Math.max(11, font.width(key) + 6);
    }

    /** A small square swatch, for a block's colour where no icon is drawn. */
    public static void swatch(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        fill(graphics, x, y, size, size, Math.max(1, size / 5.0), color);
    }

    /** Wraps text to lines no wider than {@code width}. */
    public static List<FormattedCharSequence> wrap(Font font, Component text, int width) {
        return font.split(text, Math.max(1, width));
    }

    /** Draws wrapped text and returns the height it took. */
    public static int paragraph(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = wrap(font, text, width);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x, y + i * LINE, color, false);
        }
        return lines.size() * LINE;
    }

    /** A thin scroll bar at {@code x}: a faint track and a thumb, only when the content is taller than the view. */
    public static void scrollBar(GuiGraphicsExtractor graphics, int x, int top, int view, int thumbTop, int thumbHeight, boolean hot,
                                 @Nullable Boolean visible) {
        if (visible != null && !visible) {
            return;
        }
        fill(graphics, x, top, 3, view, 1.5, 0x10FFFFFF);
        fill(graphics, x, thumbTop, 3, thumbHeight, 1.5, hot ? GlassStyle.withAlpha(GlassStyle.ACCENT, 0xE0) : GlassStyle.withAlpha(GlassStyle.ACCENT, 0x80));
    }
}
