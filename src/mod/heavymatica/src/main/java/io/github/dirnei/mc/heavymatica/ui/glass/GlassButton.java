package io.github.dirnei.mc.heavymatica.ui.glass;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** A glass button. A disabled button keeps its reason as a hover tooltip. */
public class GlassButton extends AbstractButton {

    public enum Kind {
        /** The one main action: filled with the accent. */
        PRIMARY,
        SECONDARY,
        DANGER,
        /** An accent outline, for a second action that still leads somewhere new. */
        ACCENT,
        /** No body until hovered, for small icon-like buttons. */
        QUIET,
    }

    private final Kind kind;
    private final Consumer<GlassButton> onPress;
    private int radius = Glass.SMALL_RADIUS;
    private @Nullable Component disabledReason;

    public GlassButton(int x, int y, int width, int height, Component message, Kind kind, Consumer<GlassButton> onPress) {
        super(x, y, width, height, message);
        this.kind = kind;
        this.onPress = onPress;
    }

    public GlassButton radius(int radius) {
        this.radius = radius;
        return this;
    }

    public Kind kind() {
        return kind;
    }

    /** Enables the button, or disables it with a reason shown on hover. */
    public void enable(boolean enabled, @Nullable Component reason) {
        active = enabled;
        disabledReason = enabled ? null : reason;
        setTooltip(disabledReason == null ? null : Tooltip.create(disabledReason));
    }

    public @Nullable Component disabledReason() {
        return disabledReason;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.accept(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Font font = Minecraft.getInstance().font;
        boolean hot = active && (isHovered() || isFocused());
        int fill;
        int rim;
        int text;
        switch (kind) {
            case PRIMARY -> {
                fill = active ? (hot ? 0xFFA6E5FF : GlassStyle.ACCENT) : 0x4D7FD8FF;
                rim = 0;
                text = active ? GlassStyle.ON_ACCENT : 0xB30E1218;
            }
            case DANGER -> {
                fill = hot ? 0x29FF6B6B : 0x14FF6B6B;
                rim = active ? 0x8CFF6B6B : 0x40FF6B6B;
                text = active ? GlassStyle.DANGER_TEXT : 0x80FF8A8A;
            }
            case ACCENT -> {
                fill = hot ? 0x337FD8FF : 0x1F7FD8FF;
                rim = active ? 0x8C7FD8FF : 0x407FD8FF;
                text = active ? GlassStyle.ACCENT : 0x807FD8FF;
            }
            case QUIET -> {
                fill = hot ? 0x1FFFFFFF : 0;
                rim = hot ? 0x29FFFFFF : 0;
                text = active ? GlassStyle.LABEL : GlassStyle.SUBTITLE;
            }
            default -> {
                fill = hot ? 0x1FFFFFFF : 0x0FFFFFFF;
                rim = hot ? 0x47FFFFFF : 0x29FFFFFF;
                text = active ? GlassStyle.TEXT : GlassStyle.SUBTITLE;
            }
        }
        if ((fill >>> 24) != 0 || (rim >>> 24) != 0) {
            Glass.box(graphics, getX(), getY(), width, height, radius, fill, rim);
        }
        if (isFocused() && kind == Kind.PRIMARY) {
            Glass.rim(graphics, getX() - 1, getY() - 1, width + 2, height + 2, radius + 1, 1, 0xCCFFFFFF);
        }
        extractLabel(graphics, font, text);
    }

    /** The label, centred and shortened to fit. */
    protected void extractLabel(GuiGraphicsExtractor graphics, Font font, int color) {
        TextFit fit = Glass.textCentred(graphics, font, getMessage().getString(), getX() + width / 2, getY() + (height - 8) / 2, color, width - 8);
        if (fit.shortened() && isHovered() && disabledReason == null) {
            graphics.setTooltipForNextFrame(font, getMessage(), getX(), getY() + height + 2);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
