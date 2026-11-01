package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.CloudException;
import io.github.dirnei.mc.heavymatica.cloud.model.Account;
import io.github.dirnei.mc.heavymatica.cloud.model.Identity;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The signed-in account: its linked sign-ins with the one in use marked, its storage, a link code for another sign-in
 * when the cloud offers linking, and unlinking any sign-in but the last.
 */
public final class AccountScreen extends Screen {

    private static final int ROW = 24;
    private static final int TOP = 56;

    private final Screen parent;
    private final CloudAccount account;
    private @Nullable Runnable subscription;
    private @Nullable Component message;
    private boolean messageIsError;

    public AccountScreen(Screen parent, CloudAccount account) {
        super(Component.translatable("heavymatica.account.title"));
        this.parent = parent;
        this.account = account;
    }

    @Override
    protected void init() {
        if (subscription == null) {
            subscription = account.onChange(changed -> minecraft.execute(this::accountChanged));
            account.refreshAccount().whenComplete((fresh, error) -> minecraft.execute(() -> {
                if (error != null) {
                    show(LinkCodeText.problem(CloudException.from(error, account.client().json())), true);
                }
            }));
        }
        List<Identity> identities = identities();
        for (int i = 0; i < identities.size(); i++) {
            Identity identity = identities.get(i);
            Button unlink = addRenderableWidget(Button.builder(Component.translatable("heavymatica.account.unlink"), button -> confirmUnlink(identity))
                    .bounds(width / 2 + 90, TOP + i * ROW, 70, 20).build());
            unlink.visible = identities.size() > 1;
        }
        int bottom = height - 28;
        Button link = addRenderableWidget(Button.builder(Component.translatable("heavymatica.account.link_another"),
                        button -> minecraft.setScreen(new LinkCodeScreen(this, account, () -> { })))
                .bounds(width / 2 - 154, bottom, 150, 20).build());
        link.visible = account.account().map(Account::getLinkingAvailable).orElse(false);
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose()).bounds(width / 2 + 4, bottom, 150, 20).build());
    }

    private List<Identity> identities() {
        return account.account().map(Account::getIdentities).orElse(List.of());
    }

    private void accountChanged() {
        // The unlink confirmation and the link code screen sit on top of this one meanwhile.
        if (minecraft.screen != this) {
            return;
        }
        if (!account.signedIn()) {
            onClose();
            return;
        }
        rebuildWidgets();
    }

    private void confirmUnlink(Identity identity) {
        Component who = Component.literal(label(identity));
        Component warning = Boolean.TRUE.equals(identity.getCurrent())
                ? Component.translatable("heavymatica.account.unlink.current", who)
                : Component.translatable("heavymatica.account.unlink.message", who);
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) {
                unlink(identity);
            }
        }, Component.translatable("heavymatica.account.unlink.title", who), warning));
    }

    private void unlink(Identity identity) {
        account.unlink(identity.getId()).whenComplete((ignored, error) -> minecraft.execute(() -> {
            if (error != null) {
                show(LinkCodeText.problem(CloudException.from(error, account.client().json())), true);
            } else {
                message = Component.translatable("heavymatica.account.unlinked", label(identity));
                messageIsError = false;
            }
        }));
    }

    private void show(LinkCodeText.Text text, boolean error) {
        message = LinkCodeScreen.component(text);
        messageIsError = error;
    }

    private static String label(Identity identity) {
        return (identity.getName() == null ? "?" : identity.getName()) + " (" + LinkCodeText.providerName(identity.getProvider()) + ")";
    }

    @Override
    public void onClose() {
        if (subscription != null) {
            subscription.run();
            subscription = null;
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        account.account().ifPresent(info -> graphics.centeredText(font, Component.translatable("heavymatica.account.usage",
                mebibytes(info.getUsedBytes()), mebibytes(info.getQuotaBytes())), width / 2, 24, 0xFFC0C0C0));
        graphics.text(font, Component.translatable("heavymatica.account.sign_ins"), width / 2 - 160, TOP - 14, 0xFFFFD060, false);
        List<Identity> identities = identities();
        for (int i = 0; i < identities.size(); i++) {
            Identity identity = identities.get(i);
            int y = TOP + i * ROW + 6;
            graphics.text(font, label(identity), width / 2 - 160, y, 0xFFFFFFFF, false);
            if (Boolean.TRUE.equals(identity.getCurrent())) {
                graphics.text(font, Component.translatable("heavymatica.account.in_use"), width / 2 + 10, y, 0xFFA0E0A0, false);
            }
        }
        if (message != null) {
            graphics.centeredText(font, message, width / 2, height - 44, messageIsError ? 0xFFFF8080 : 0xFFA0E0A0);
        }
    }

    private static String mebibytes(@Nullable Long bytes) {
        return String.format(Locale.ROOT, "%.1f", (bytes == null ? 0 : bytes) / 1024.0 / 1024.0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
