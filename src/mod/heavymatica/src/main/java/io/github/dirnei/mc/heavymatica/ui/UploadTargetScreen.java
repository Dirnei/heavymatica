package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Uploads a local entry as a new schematic, or as a new version of a chosen cloud schematic. */
final class UploadTargetScreen extends Screen {

    private static final int ROW = 12;

    private final Screen parent;
    private final String entryName;
    private final List<LibraryEntry> targets;
    private final Consumer<@Nullable LibraryEntry> onChoose;

    UploadTargetScreen(Screen parent, String entryName, List<LibraryEntry> targets, Consumer<@Nullable LibraryEntry> onChoose) {
        super(Component.translatable("heavymatica.upload.title", entryName));
        this.parent = parent;
        this.entryName = entryName;
        this.targets = targets;
        this.onChoose = onChoose;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("heavymatica.upload.as_new"), button -> choose(null))
                .bounds(width / 2 - 122, height - 28, 120, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).bounds(width / 2 + 2, height - 28, 120, 20).build());
    }

    private void choose(@Nullable LibraryEntry target) {
        minecraft.setScreen(parent);
        onChoose.accept(target);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("heavymatica.upload.as_version"), width / 2, 24, 0xFFC0C0C0);
        for (int i = 0; i < targets.size(); i++) {
            int y = 40 + i * ROW;
            boolean hovered = mouseY >= y - 2 && mouseY < y + ROW - 2 && mouseX >= width / 2 - 120 && mouseX < width / 2 + 120;
            if (hovered) {
                graphics.fill(width / 2 - 120, y - 2, width / 2 + 120, y + ROW - 2, 0xA0305080);
            }
            graphics.centeredText(font, targets.get(i).name(), width / 2, y, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int index = (int) ((event.y() - 38) / ROW);
        if (event.x() >= width / 2.0 - 120 && event.x() < width / 2.0 + 120 && index >= 0 && index < targets.size()) {
            choose(targets.get(index));
            return true;
        }
        return false;
    }
}
