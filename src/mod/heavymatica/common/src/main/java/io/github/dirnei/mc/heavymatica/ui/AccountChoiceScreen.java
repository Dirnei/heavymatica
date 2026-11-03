package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.CloudException;
import io.github.dirnei.mc.heavymatica.cloud.model.PendingSignIn;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassDialogScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The first sign-in of a Minecraft account without a cloud account, over the library: create a new account, join an
 * existing one with a link code (only when the cloud offers it), or leave without creating anything.
 */
public final class AccountChoiceScreen extends GlassDialogScreen {

    private static final int GAP = 8;

    private final Screen back;
    private final CloudAccount account;
    private final Runnable onSignedIn;
    private @Nullable Component failure;
    private boolean busy;
    private ChoiceCard create;

    /** @param onSignedIn runs on the parent screen once the player is signed in, by either choice */
    public AccountChoiceScreen(Screen parent, CloudAccount account, Runnable onSignedIn) {
        super(parent, Component.translatable("heavymatica.account.choice.title"));
        this.back = parent;
        this.account = account;
        this.onSignedIn = onSignedIn;
    }

    private boolean joinAvailable() {
        return account.pending().map(PendingSignIn::getJoinAvailable).orElse(false);
    }

    private Component message() {
        return Component.translatable("heavymatica.account.choice.message", account.name().orElse("?"));
    }

    private int innerWidth() {
        return Math.min(dialogWidth(), width - 16) - 2 * PAD;
    }

    private boolean sideBySide() {
        return joinAvailable() && innerWidth() >= 300;
    }

    private int cardWidth() {
        return sideBySide() ? (innerWidth() - GAP) / 2 : innerWidth();
    }

    private int cardHeight() {
        int w = cardWidth();
        int h = ChoiceCard.heightFor(font, Component.translatable("heavymatica.account.choice.create.hint"), w);
        if (joinAvailable()) {
            h = Math.max(h, ChoiceCard.heightFor(font, Component.translatable("heavymatica.account.choice.join.hint"), w));
        }
        return h;
    }

    @Override
    protected int dialogWidth() {
        return 380;
    }

    @Override
    protected int dialogHeight() {
        int message = Glass.wrap(font, message(), innerWidth()).size() * Glass.LINE;
        int cards = sideBySide() || !joinAvailable() ? cardHeight() : 2 * cardHeight() + GAP;
        int foot = Glass.wrap(font, Component.translatable("heavymatica.account.choice.privacy"), innerWidth() - 90).size() * Glass.LINE;
        return PAD + 18 + message + 10 + cards + 12 + Math.max(18, foot) + PAD + (failure == null ? 0 : 14);
    }

    @Override
    protected void init() {
        super.init();
        Rect content = content();
        int y = content.y() + Glass.wrap(font, message(), content.w()).size() * Glass.LINE + 10;
        int w = cardWidth();
        int h = cardHeight();
        create = addRenderableWidget(new ChoiceCard(content.x(), y, w, h, Component.translatable("heavymatica.account.choice.create"),
                Component.translatable("heavymatica.account.choice.create.hint"), button -> create()));
        create.choose(true);
        if (joinAvailable()) {
            int x = sideBySide() ? content.x() + w + GAP : content.x();
            int joinY = sideBySide() ? y : y + h + GAP;
            addRenderableWidget(new ChoiceCard(x, joinY, w, h, Component.translatable("heavymatica.account.choice.join"),
                    Component.translatable("heavymatica.account.choice.join.hint"),
                    button -> minecraft.setScreen(new LinkCodeScreen(this, account, this::signedIn))));
        }
        Rect frame = frame();
        addRenderableWidget(new GlassButton(frame.right() - PAD - 80, frame.bottom() - PAD - 18, 80, 18,
                Component.translatable("heavymatica.account.choice.not_now"), GlassButton.Kind.SECONDARY, button -> onClose()));
        create.active = !busy;
    }

    private void create() {
        busy = true;
        create.active = false;
        failure = null;
        account.createAccount().whenComplete((ignored, error) -> minecraft.execute(() -> {
            busy = false;
            if (error != null) {
                Heavymatica.LOGGER.info("Creating a cloud account failed: {}", error.toString());
                failure = LinkCodeScreen.component(LinkCodeText.problem(CloudException.from(error, account.client().json())));
                rebuildWidgets();
            } else {
                signedIn();
            }
        }));
    }

    private void signedIn() {
        minecraft.setScreen(back);
        onSignedIn.run();
    }

    @Override
    public void onClose() {
        account.cancelChoice();
        minecraft.setScreen(back);
    }

    @Override
    protected void extractDialog(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Rect frame = frame();
        Glass.text(graphics, font, title.getString(), frame.x() + PAD, frame.y() + PAD, GlassStyle.TEXT, frame.w() - 2 * PAD);
        Rect content = content();
        Glass.paragraph(graphics, font, message(), content.x(), content.y(), content.w(), GlassStyle.LABEL);
        int footHeight = Glass.wrap(font, Component.translatable("heavymatica.account.choice.privacy"), content.w() - 90).size() * Glass.LINE;
        int footY = frame.bottom() - PAD - Math.max(18, footHeight);
        if (failure != null) {
            Glass.text(graphics, font, failure.getString(), content.x(), footY - 14, GlassStyle.DANGER_TEXT, content.w());
        }
        Glass.paragraph(graphics, font, Component.translatable("heavymatica.account.choice.privacy"), content.x(), footY,
                content.w() - 90, GlassStyle.SUBTITLE);
    }
}
