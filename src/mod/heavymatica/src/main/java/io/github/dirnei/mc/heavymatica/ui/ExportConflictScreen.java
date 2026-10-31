package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.library.Exporter;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Asks what to do when an exported file already exists: overwrite it, keep both, or cancel. */
final class ExportConflictScreen extends Screen {

    private final Screen parent;
    private final String fileName;
    private final Consumer<Exporter.Conflict> onChoice;
    private final Runnable onCancel;

    ExportConflictScreen(Screen parent, String fileName, Consumer<Exporter.Conflict> onChoice, Runnable onCancel) {
        super(Component.translatable("heavymatica.library.export.exists.title"));
        this.parent = parent;
        this.fileName = fileName;
        this.onChoice = onChoice;
        this.onCancel = onCancel;
    }

    @Override
    protected void init() {
        int y = height / 2 + 10;
        addRenderableWidget(Button.builder(Component.translatable("heavymatica.library.export.overwrite"), button -> choose(Exporter.Conflict.OVERWRITE))
                .bounds(width / 2 - 154, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("heavymatica.library.export.keep_both"), button -> choose(Exporter.Conflict.KEEP_BOTH))
                .bounds(width / 2 - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).bounds(width / 2 + 54, y, 100, 20).build());
    }

    private void choose(Exporter.Conflict conflict) {
        minecraft.setScreen(parent);
        onChoice.accept(conflict);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
        onCancel.run();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        graphics.centeredText(font, title, width / 2, height / 2 - 30, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("heavymatica.library.export.exists.message", fileName), width / 2, height / 2 - 14, 0xFFC0C0C0);
    }
}
