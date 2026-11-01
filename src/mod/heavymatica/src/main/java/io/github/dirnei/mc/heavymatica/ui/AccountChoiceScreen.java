package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.CloudException;
import io.github.dirnei.mc.heavymatica.cloud.model.PendingSignIn;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The first sign-in of a Minecraft account without a cloud account: create a new account, join an existing one with a
 * link code (only when the cloud offers it), or leave without creating anything.
 */
public final class AccountChoiceScreen extends Screen {

    private final Screen parent;
    private final CloudAccount account;
    private final Runnable onSignedIn;
    private @Nullable Component failure;
    private boolean busy;
    private Button create;

    /** @param onSignedIn runs on the parent screen once the player is signed in, by either choice */
    public AccountChoiceScreen(Screen parent, CloudAccount account, Runnable onSignedIn) {
        super(Component.translatable("heavymatica.account.choice.title"));
        this.parent = parent;
        this.account = account;
        this.onSignedIn = onSignedIn;
    }

    @Override
    protected void init() {
        boolean joinAvailable = account.pending().map(PendingSignIn::getJoinAvailable).orElse(false);
        int y = height / 2;
        create = addRenderableWidget(Button.builder(Component.translatable("heavymatica.account.choice.create"), button -> create())
                .bounds(width / 2 - 100, y, 200, 20).build());
        if (joinAvailable) {
            y += 24;
            addRenderableWidget(Button.builder(Component.translatable("heavymatica.account.choice.join"),
                            button -> minecraft.setScreen(new LinkCodeScreen(this, account, this::signedIn)))
                    .bounds(width / 2 - 100, y, 200, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).bounds(width / 2 - 100, y + 24, 200, 20).build());
        create.active = !busy;
    }

    private void create() {
        busy = true;
        create.active = false;
        failure = null;
        account.createAccount().whenComplete((ignored, error) -> minecraft.execute(() -> {
            busy = false;
            create.active = true;
            if (error != null) {
                Heavymatica.LOGGER.info("Creating a cloud account failed: {}", error.toString());
                failure = LinkCodeScreen.component(LinkCodeText.problem(CloudException.from(error, account.client().json())));
            } else {
                signedIn();
            }
        }));
    }

    private void signedIn() {
        minecraft.setScreen(parent);
        onSignedIn.run();
    }

    @Override
    public void onClose() {
        account.cancelChoice();
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        String name = account.name().orElse("?");
        graphics.centeredText(font, title, width / 2, height / 2 - 60, 0xFFFFFFFF);
        graphics.textWithWordWrap(font, Component.translatable("heavymatica.account.choice.message", name), width / 2 - 150, height / 2 - 44, 300,
                0xFFC0C0C0);
        if (failure != null) {
            graphics.centeredText(font, failure, width / 2, height / 2 - 14, 0xFFFF8080);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
