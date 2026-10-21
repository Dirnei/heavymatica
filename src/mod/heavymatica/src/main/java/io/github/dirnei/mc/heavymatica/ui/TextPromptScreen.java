package io.github.dirnei.mc.heavymatica.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Asks for one line of text (a new name, folder or tags) and returns to the previous screen. */
final class TextPromptScreen extends Screen {

    private final Screen parent;
    private final Component hint;
    private final String initial;
    private final Consumer<String> onSave;
    private EditBox input;

    TextPromptScreen(Screen parent, Component title, Component hint, String initial, Consumer<String> onSave) {
        super(title);
        this.parent = parent;
        this.hint = hint;
        this.initial = initial;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        input = new EditBox(font, width / 2 - 120, height / 2 - 10, 240, 20, title);
        input.setMaxLength(200);
        input.setValue(initial);
        input.setHint(hint);
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save()).bounds(width / 2 - 122, height / 2 + 18, 120, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).bounds(width / 2 + 2, height / 2 + 18, 120, 20).build());
    }

    private void save() {
        onSave.accept(input.getValue());
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        graphics.centeredText(font, title, width / 2, height / 2 - 30, 0xFFFFFFFF);
    }
}
