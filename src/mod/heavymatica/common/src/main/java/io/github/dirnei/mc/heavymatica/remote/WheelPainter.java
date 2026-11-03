package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.placement.OpacitySteps;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Segment;
import io.github.dirnei.mc.heavymatica.remote.WheelStyle.Kind;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Draws one frame of the wheel from values fixed when it was made, so the same frame can be drawn again while the
 * closed wheel fades away.
 */
final class WheelPainter {

    private static final double GAP = 2.5;
    private static final double ICON_LIFT = 4;
    private static final double LABEL_DROP = 13;
    private static final float MIN_LABEL_SCALE = 0.75F;
    private static final float HINT_SCALE = 0.75F;

    /** One page of segments as drawn this frame. */
    record Ring(List<Segment> segments, double[] hover, double[] arrival, double alpha, double scale, double rotation) {
    }

    /** The centre text while nothing is hovered. */
    record Title(Component title, @Nullable Component subtitle) {
    }

    private final int centreX;
    private final int centreY;
    private final int width;
    private final int height;
    private final double inner;
    private final double outer;
    private final double feather;
    private final double openAlpha;
    private final Ring ring;
    private final @Nullable Ring previous;
    private final int hovered;
    private final double accentAngle;
    private final double accentShown;
    private final Title title;

    WheelPainter(int width, int height, double inner, double outer, double feather, double openAlpha, Ring ring, @Nullable Ring previous,
                 int hovered, double accentAngle, double accentShown, Title title) {
        this.width = width;
        this.height = height;
        this.centreX = width / 2;
        this.centreY = height / 2;
        this.inner = inner;
        this.outer = outer;
        this.feather = feather;
        this.openAlpha = openAlpha;
        this.ring = ring;
        this.previous = previous;
        this.hovered = hovered;
        this.accentAngle = accentAngle;
        this.accentShown = accentShown;
        this.title = title;
    }

    /** @param fade multiplies every alpha; {@code grow} scales the whole wheel around its centre */
    void paint(GuiGraphicsExtractor graphics, Font font, double fade, double grow) {
        double alpha = openAlpha * fade;
        if (alpha <= 0.004) {
            return;
        }
        WheelMesh mesh = new WheelMesh(feather);
        dim(mesh, alpha);
        if (previous != null) {
            bodies(mesh, previous, alpha * previous.alpha(), grow, -1);
        }
        bodies(mesh, ring, alpha * ring.alpha(), grow, hovered);
        centreAndAccent(mesh, alpha * ring.alpha(), ring.scale() * grow);
        WheelMeshRenderState.submit(graphics, mesh);
        icons(graphics, font, alpha * ring.alpha(), grow);
        centreText(graphics, font, alpha * ring.alpha(), ring.scale() * grow);
    }

    private void dim(WheelMesh mesh, double alpha) {
        double reach = Math.hypot(width, height) / 2 + 2;
        double near = outer * 0.6;
        mesh.transform(centreX, centreY, 1, 0);
        mesh.sector(0, near, 0, WheelMesh.TAU, 0, fade(WheelStyle.DIM_CENTRE, alpha), fade(WheelStyle.DIM_CENTRE, alpha), 0);
        mesh.sector(near, reach, 0, WheelMesh.TAU, 0, fade(WheelStyle.DIM_CENTRE, alpha), fade(WheelStyle.DIM_EDGE, alpha), 0);
    }

    private void bodies(WheelMesh mesh, Ring ring, double alpha, double grow, int hovered) {
        List<Segment> segments = ring.segments();
        int count = segments.size();
        double sector = WheelMesh.TAU / count;
        for (int i = 0; i < count; i++) {
            Segment segment = segments.get(i);
            double h = ring.hover()[i];
            double arrived = ring.arrival()[i];
            double a = alpha * arrived * (segment.enabled() ? 1 : 0.55);
            if (a <= 0.004) {
                continue;
            }
            double mid = WheelGeometry.middle(i, count);
            double push = h * WheelAnimation.HOVER_PUSH - (1 - arrived) * 6;
            place(mesh, mid, push, ring.scale() * grow * (0.9 + 0.1 * arrived), ring.rotation());
            double from = mid - sector / 2;
            double to = mid + sector / 2;
            double gap = count > 1 ? GAP : 0;
            mesh.sector(inner, outer, from, to, gap, fade(WheelStyle.BODY_INNER, a), fade(WheelStyle.BODY_OUTER, a));
            if (segment.tinted()) {
                mesh.sector(inner, outer, from, to, gap, fade(WheelStyle.ON_INNER, a), fade(WheelStyle.ON_OUTER, a));
            }
            int accent = WheelStyle.kind(segment.entry()).accent();
            if (h > 0.01 && segment.enabled()) {
                mesh.sector(inner, outer, from, to, gap, fade(WheelStyle.withAlpha(accent, (int) (WheelStyle.HOVER_FILL_INNER * h)), a),
                        fade(WheelStyle.withAlpha(accent, (int) (WheelStyle.HOVER_FILL_OUTER * h)), a));
            }
            int rim = fade(WheelStyle.mix(WheelStyle.RIM, WheelStyle.RIM_HOVERED, i == hovered ? h : h * 0.5), a);
            double line = Math.max(feather, 0.5);
            mesh.sector(outer - line, outer, from, to, gap, rim, rim);
            mesh.sector(inner, inner + line, from, to, gap, fade(WheelStyle.RIM, a), fade(WheelStyle.RIM, a));
            if (segment.current() && segment.entry() != Entry.UNKNOWN_BLOCKS) {
                double dot = outer - 5;
                place(mesh, mid, push + dot, ring.scale() * grow * (0.9 + 0.1 * arrived), ring.rotation());
                mesh.disc(1.8, fade(Kind.NORMAL.accent(), a));
            }
        }
    }

    /** Puts what follows at the ring's centre, moved {@code push} outwards along angle {@code mid}. */
    private void place(WheelMesh mesh, double mid, double push, double scale, double rotation) {
        double dx = Math.sin(mid) * push;
        double dy = -Math.cos(mid) * push;
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        mesh.transform(centreX + (dx * cos - dy * sin) * scale, centreY + (dx * sin + dy * cos) * scale, scale, rotation);
    }

    private void centreAndAccent(WheelMesh mesh, double alpha, double scale) {
        mesh.transform(centreX, centreY, scale, 0);
        double disc = inner - 4;
        mesh.disc(disc, fade(WheelStyle.CENTRE, alpha));
        double line = Math.max(feather, 0.5);
        mesh.sector(disc - line, disc, 0, WheelMesh.TAU, 0, fade(WheelStyle.RIM, alpha), fade(WheelStyle.RIM, alpha));
        if (Double.isNaN(accentAngle) || accentShown <= 0.01 || hovered < 0) {
            return;
        }
        int count = ring.segments().size();
        double half = Math.PI / count;
        double a = alpha * accentShown;
        int accent = WheelStyle.kind(ring.segments().get(hovered).entry()).accent();
        if (!ring.segments().get(hovered).enabled()) {
            accent = Kind.NORMAL.accent();
        }
        double radius = outer + 5 + ring.hover()[hovered] * WheelAnimation.HOVER_PUSH;
        double angle = accentAngle + ring.rotation();
        mesh.arc(radius, 2.2, angle - half * 0.72, angle + half * 0.72, fade(accent, a), 4, fade(WheelStyle.withAlpha(accent, 0x66), a));
        mesh.triangle(disc + 3.5, angle, disc - 1, angle + 0.09, disc - 1, angle - 0.09, fade(accent, a));
    }

    private void icons(GuiGraphicsExtractor graphics, Font font, double alpha, double grow) {
        List<Segment> segments = ring.segments();
        int count = segments.size();
        double middle = (inner + outer) / 2;
        double chord = 2 * middle * Math.sin(Math.PI / Math.max(2, count)) - 6;
        double maxLabel = Math.min(chord, (outer - inner) * 1.6);
        for (int i = 0; i < count; i++) {
            Segment segment = segments.get(i);
            double h = ring.hover()[i];
            double arrived = ring.arrival()[i];
            double a = alpha * arrived;
            if (a <= 0.01) {
                continue;
            }
            double angle = WheelGeometry.middle(i, count) + ring.rotation();
            double scale = ring.scale() * grow;
            double radius = (middle + h * WheelAnimation.HOVER_PUSH) * scale;
            float x = (float) (centreX + Math.sin(angle) * radius);
            float y = (float) (centreY - Math.cos(angle) * radius - ICON_LIFT * scale);
            float iconAlpha = (float) (a * (segment.enabled() ? 0.85 + 0.15 * h : 0.45));
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale((float) (scale * (1 + 0.18 * h) * (0.7 + 0.3 * arrived)));
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(segment), -8, -8, 16, 16, argb(0xFFFFFFFF, iconAlpha));
            if (segment.entry() == Entry.OPACITY_STEP) {
                int filled = (int) Math.round(12 * Math.max(0, Math.min(1, segment.value())));
                if (filled > 0) {
                    graphics.fill(-6, 6 - filled, 6, 6, argb(WheelStyle.GHOST, iconAlpha));
                    graphics.fill(-6, 6 - filled, 6, 7 - filled, argb(WheelStyle.GHOST_LIGHT, iconAlpha));
                }
            }
            graphics.pose().popMatrix();
            Fitted label = fit(font, RadialMenuScreen.name(segment), maxLabel);
            int color = argb(h > 0.5 ? WheelStyle.TEXT : WheelStyle.LABEL, a * (segment.enabled() ? 0.72 + 0.28 * h : 0.45));
            if ((color >>> 24) >= 8) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, (float) (y + LABEL_DROP * scale));
                graphics.pose().scale((float) (scale * label.scale()));
                graphics.centeredText(font, label.text(), 0, -4, color);
                graphics.pose().popMatrix();
            }
        }
    }

    private void centreText(GuiGraphicsExtractor graphics, Font font, double alpha, double scale) {
        int maxWidth = (int) ((inner - 7) * 2);
        graphics.pose().pushMatrix();
        graphics.pose().translate(centreX, centreY);
        graphics.pose().scale((float) scale);
        if (hovered >= 0 && hovered < ring.segments().size()) {
            Segment segment = ring.segments().get(hovered);
            Kind kind = WheelStyle.kind(segment.entry());
            int nameColor = segment.enabled() && kind != Kind.NORMAL ? kind.accent() : WheelStyle.TEXT;
            Component hint = segment.entry() == Entry.OPACITY_STEP
                    ? Component.translatable(WheelText.hintKey(segment), OpacitySteps.percent(segment.value()))
                    : Component.translatable(WheelText.hintKey(segment));
            lines(graphics, font, RadialMenuScreen.name(segment), hint, maxWidth, argb(nameColor, alpha), argb(WheelStyle.HINT, alpha));
        } else {
            lines(graphics, font, title.title(), title.subtitle(), maxWidth, argb(WheelStyle.TEXT, alpha), argb(WheelStyle.SUBTITLE, alpha));
        }
        graphics.pose().popMatrix();
    }

    /** Up to two lines of {@code head} and three smaller lines of {@code detail}, centred on the origin together. */
    private static void lines(GuiGraphicsExtractor graphics, Font font, Component head, @Nullable Component detail, int maxWidth,
                              int headColor, int detailColor) {
        if ((headColor >>> 24) < 8) {
            return;
        }
        List<FormattedCharSequence> heads = font.split(head, maxWidth);
        heads = heads.subList(0, Math.min(2, heads.size()));
        List<FormattedCharSequence> details = detail == null ? List.of() : font.split(detail, (int) (maxWidth / HINT_SCALE));
        details = details.subList(0, Math.min(3, details.size()));
        float headLine = 10;
        float detailLine = 9 * HINT_SCALE;
        float y = -(heads.size() * headLine + details.size() * detailLine) / 2 + 1;
        for (FormattedCharSequence line : heads) {
            graphics.text(font, line, -font.width(line) / 2, (int) Math.floor(y), headColor, false);
            y += headLine;
        }
        for (FormattedCharSequence line : details) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0, y);
            graphics.pose().scale(HINT_SCALE);
            graphics.text(font, line, -font.width(line) / 2, 0, detailColor, false);
            graphics.pose().popMatrix();
            y += detailLine;
        }
    }

    private record Fitted(FormattedCharSequence text, float scale) {
    }

    /** The label shrunk to fit {@code maxWidth}, and cut with an ellipsis when even the smallest size is too wide. */
    private static Fitted fit(Font font, Component text, double maxWidth) {
        int width = font.width(text);
        if (width <= maxWidth) {
            return new Fitted(text.getVisualOrderText(), 1);
        }
        float scale = (float) Math.max(MIN_LABEL_SCALE, maxWidth / width);
        if (width * scale <= maxWidth + 0.01) {
            return new Fitted(text.getVisualOrderText(), scale);
        }
        String ellipsis = "…";
        int room = (int) (maxWidth / MIN_LABEL_SCALE) - font.width(ellipsis);
        String cut = font.plainSubstrByWidth(text.getString(), Math.max(0, room));
        return new Fitted(Component.literal(cut + ellipsis).getVisualOrderText(), MIN_LABEL_SCALE);
    }

    private static Identifier sprite(Segment segment) {
        return Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, WheelIcons.sprite(segment));
    }

    private static int fade(int color, double alpha) {
        return WheelMesh.fade(color, alpha);
    }

    private static int argb(int color, double alpha) {
        return WheelMesh.fade(color, alpha);
    }
}
