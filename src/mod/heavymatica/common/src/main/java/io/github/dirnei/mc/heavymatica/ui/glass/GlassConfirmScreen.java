package io.github.dirnei.mc.heavymatica.ui.glass;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Asks a yes or no question over the screen it came from. Esc answers no. */
public final class GlassConfirmScreen extends GlassDialogScreen {

    private final Component message;
    private final Component yes;
    private final Component no;
    private final boolean danger;
    private final Consumer<Boolean> answer;
    private boolean answered;

    /**
     * @param danger whether yes destroys something; it is then drawn in the danger colour
     * @param answer called once, after the parent screen is back
     */
    public GlassConfirmScreen(@Nullable Screen parent, Component title, Component message, Component yes, Component no, boolean danger,
                              Consumer<Boolean> answer) {
        super(parent, title);
        this.message = message;
        this.yes = yes;
        this.no = no;
        this.danger = danger;
        this.answer = answer;
    }

    public GlassConfirmScreen(@Nullable Screen parent, Component title, Component message, boolean danger, Consumer<Boolean> answer) {
        this(parent, title, message, CommonComponents.GUI_YES, CommonComponents.GUI_NO, danger, answer);
    }

    @Override
    protected int dialogWidth() {
        return 280;
    }

    @Override
    protected int dialogHeight() {
        return 54 + Glass.wrap(font, message, Math.min(280, width - 16) - 2 * PAD).size() * Glass.LINE + 26;
    }

    @Override
    protected void init() {
        super.init();
        Rect frame = frame();
        int buttonWidth = Math.min(90, (frame.w() - 2 * PAD - 6) / 2);
        int y = frame.bottom() - PAD - 18;
        addRenderableWidget(new GlassButton(frame.right() - PAD - buttonWidth, y, buttonWidth, 18, yes,
                danger ? GlassButton.Kind.DANGER : GlassButton.Kind.PRIMARY, button -> respond(true)));
        addRenderableWidget(new GlassButton(frame.right() - PAD - 2 * buttonWidth - 6, y, buttonWidth, 18, no, GlassButton.Kind.SECONDARY,
                button -> respond(false)));
    }

    private void respond(boolean value) {
        if (answered) {
            return;
        }
        answered = true;
        minecraft.setScreen(parent);
        answer.accept(value);
    }

    @Override
    public void onClose() {
        respond(false);
    }

    @Override
    protected void extractDialog(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractDialog(graphics, mouseX, mouseY, a);
        Rect frame = frame();
        Glass.paragraph(graphics, font, message, frame.x() + PAD, frame.y() + PAD + 18, frame.w() - 2 * PAD, GlassStyle.LABEL);
    }
}
