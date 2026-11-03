package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.library.Exporter;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassDialogScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Asks what to do when an exported file already exists: overwrite it, keep both, or cancel. */
final class ExportConflictScreen extends GlassDialogScreen {

    private final String fileName;
    private final Consumer<Exporter.Conflict> onChoice;
    private final Runnable onCancel;
    private boolean answered;

    ExportConflictScreen(Screen parent, String fileName, Consumer<Exporter.Conflict> onChoice, Runnable onCancel) {
        super(parent, Component.translatable("heavymatica.library.export.exists.title"));
        this.fileName = fileName;
        this.onChoice = onChoice;
        this.onCancel = onCancel;
    }

    @Override
    protected int dialogWidth() {
        return 320;
    }

    @Override
    protected int dialogHeight() {
        return 96;
    }

    @Override
    protected void init() {
        super.init();
        Rect frame = frame();
        int y = frame.bottom() - PAD - 18;
        int w = (frame.w() - 2 * PAD - 12) / 3;
        addRenderableWidget(new GlassButton(frame.right() - PAD - w, y, w, 18, Component.translatable("heavymatica.library.export.keep_both"),
                GlassButton.Kind.PRIMARY, button -> choose(Exporter.Conflict.KEEP_BOTH)));
        addRenderableWidget(new GlassButton(frame.right() - PAD - 2 * w - 6, y, w, 18, Component.translatable("heavymatica.library.export.overwrite"),
                GlassButton.Kind.DANGER, button -> choose(Exporter.Conflict.OVERWRITE)));
        addRenderableWidget(new GlassButton(frame.x() + PAD, y, w, 18, CommonComponents.GUI_CANCEL, GlassButton.Kind.SECONDARY, button -> onClose()));
    }

    private void choose(Exporter.Conflict conflict) {
        if (answered) {
            return;
        }
        answered = true;
        minecraft.setScreen(parent);
        onChoice.accept(conflict);
    }

    @Override
    public void onClose() {
        if (answered) {
            return;
        }
        answered = true;
        minecraft.setScreen(parent);
        onCancel.run();
    }

    @Override
    protected void extractDialog(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractDialog(graphics, mouseX, mouseY, a);
        Rect frame = frame();
        Glass.paragraph(graphics, font, Component.translatable("heavymatica.library.export.exists.message", fileName), frame.x() + PAD, frame.y() + PAD + 18,
                frame.w() - 2 * PAD, GlassStyle.LABEL);
    }
}
