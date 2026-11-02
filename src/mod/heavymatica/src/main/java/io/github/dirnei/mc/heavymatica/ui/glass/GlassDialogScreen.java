package io.github.dirnei.mc.heavymatica.ui.glass;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A dialog over the screen it was opened from: that screen stays visible, dimmed and without hover, and closing the
 * dialog returns to that same screen object, so its selection and scrolling stay as they were.
 */
public abstract class GlassDialogScreen extends GlassScreen {

    protected static final int PAD = 14;

    protected final @Nullable Screen parent;
    private Rect frame = new Rect(0, 0, 0, 0);

    protected GlassDialogScreen(@Nullable Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    /** The dialog's size for a screen of this size. */
    protected abstract int dialogWidth();

    protected abstract int dialogHeight();

    /** The dialog's frame on screen, as of the last layout. */
    protected Rect frame() {
        return frame;
    }

    /** The area inside the frame's padding, below the title. */
    protected Rect content() {
        return new Rect(frame.x() + PAD, frame.y() + PAD + 18, frame.w() - 2 * PAD, frame.h() - 2 * PAD - 18);
    }

    @Override
    protected void init() {
        int w = Math.min(dialogWidth(), width - 16);
        int h = Math.min(dialogHeight(), height - 16);
        frame = new Rect((width - w) / 2, (height - h) / 2, w, h);
        if (parent != null && (parent.width != width || parent.height != height)) {
            parent.resize(width, height);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (parent != null) {
            parent.extractBackground(graphics, -1, -1, a);
            parent.extractRenderState(graphics, -1, -1, a);
            graphics.nextStratum();
            Glass.dim(graphics, width, height, GlassStyle.DIM);
        } else {
            super.extractBackground(graphics, mouseX, mouseY, a);
        }
        Glass.panel(graphics, frame.x(), frame.y(), frame.w(), frame.h(), 10, GlassStyle.DIALOG_TOP, GlassStyle.DIALOG_BOTTOM, 0x1FFFFFFF);
        extractDialog(graphics, mouseX, mouseY, a);
    }

    /** Draws the title; subclasses add what sits on the dialog under the widgets. */
    protected void extractDialog(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Glass.text(graphics, font, title.getString(), frame.x() + PAD, frame.y() + PAD, GlassStyle.TEXT, frame.w() - 2 * PAD);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
