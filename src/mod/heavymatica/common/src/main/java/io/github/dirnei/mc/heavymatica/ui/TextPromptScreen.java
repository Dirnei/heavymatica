package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassDialogScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassEditBox;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Asks for one line of text (a new name, folder or tags) over the previous screen, or over the game. */
public final class TextPromptScreen extends GlassDialogScreen {

    private final Component hint;
    private final String initial;
    private final @Nullable Component note;
    private final Consumer<String> onSave;
    private final Runnable onCancel;
    private @Nullable EditBox input;
    private boolean answered;

    TextPromptScreen(Screen parent, Component title, Component hint, String initial, Consumer<String> onSave) {
        this(parent, title, hint, initial, null, onSave, () -> { });
    }

    /** @param note a line shown under the input, such as a warning */
    public TextPromptScreen(@Nullable Screen parent, Component title, Component hint, String initial, @Nullable Component note,
                            Consumer<String> onSave, Runnable onCancel) {
        super(parent, title);
        this.hint = hint;
        this.initial = initial;
        this.note = note;
        this.onSave = onSave;
        this.onCancel = onCancel;
    }

    @Override
    protected int dialogWidth() {
        return 300;
    }

    @Override
    protected int dialogHeight() {
        return note == null ? 92 : 106;
    }

    @Override
    protected void init() {
        super.init();
        Rect frame = frame();
        String value = input == null ? initial : input.getValue();
        input = new GlassEditBox(font, frame.x() + PAD, frame.y() + PAD + 18, frame.w() - 2 * PAD, GlassEditBox.HEIGHT + 2, hint, false);
        input.setMaxLength(200);
        input.setValue(value);
        addRenderableWidget(input);
        setInitialFocus(input);
        int buttonWidth = 80;
        int y = frame.bottom() - PAD - 18;
        addRenderableWidget(new GlassButton(frame.right() - PAD - buttonWidth, y, buttonWidth, 18, CommonComponents.GUI_DONE, GlassButton.Kind.PRIMARY,
                button -> save()));
        addRenderableWidget(new GlassButton(frame.right() - PAD - 2 * buttonWidth - 6, y, buttonWidth, 18, CommonComponents.GUI_CANCEL,
                GlassButton.Kind.SECONDARY, button -> onClose()));
    }

    private void save() {
        if (answered) {
            return;
        }
        answered = true;
        minecraft.setScreen(parent);
        onSave.accept(input.getValue());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(event);
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
        if (note != null) {
            Rect frame = frame();
            Glass.text(graphics, font, note.getString(), frame.x() + PAD, frame.y() + PAD + 44, GlassStyle.WARNING, frame.w() - 2 * PAD);
        }
    }
}
