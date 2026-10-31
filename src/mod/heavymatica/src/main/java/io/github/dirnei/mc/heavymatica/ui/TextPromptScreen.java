package io.github.dirnei.mc.heavymatica.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Asks for one line of text (a new name, folder or tags) and returns to the previous screen, or to the game. */
public final class TextPromptScreen extends Screen {

    private final @Nullable Screen parent;
    private final Component hint;
    private final String initial;
    private final @Nullable Component note;
    private final Consumer<String> onSave;
    private final Runnable onCancel;
    private EditBox input;

    TextPromptScreen(Screen parent, Component title, Component hint, String initial, Consumer<String> onSave) {
        this(parent, title, hint, initial, null, onSave, () -> { });
    }

    /** @param note a line shown under the input, such as a warning */
    public TextPromptScreen(@Nullable Screen parent, Component title, Component hint, String initial, @Nullable Component note,
                            Consumer<String> onSave, Runnable onCancel) {
        super(title);
        this.parent = parent;
        this.hint = hint;
        this.initial = initial;
        this.note = note;
        this.onSave = onSave;
        this.onCancel = onCancel;
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
        minecraft.setScreen(parent);
        onSave.accept(input.getValue());
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
        if (note != null) {
            graphics.centeredText(font, note, width / 2, height / 2 + 46, 0xFFE0C040);
        }
    }
}
