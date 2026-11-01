package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.CloudException;
import io.github.dirnei.mc.heavymatica.cloud.model.LinkCode;
import io.github.dirnei.mc.heavymatica.cloud.model.LinkCodeStatus;
import java.time.Instant;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Shows a link code with its QR code and countdown, and asks the player to confirm once someone redeemed it. Serves the
 * signed-in account (another sign-in joins it) and the pending sign-in (it joins an existing account). Closing the
 * screen cancels a code that is still open or waiting for confirmation. Codes are only ever shown, never entered.
 */
public final class LinkCodeScreen extends Screen {

    private static final long POLL_MILLIS = 2000;
    private static final int CODE_SCALE = 3;
    private static final int MAX_MODULE = 4;

    private final Screen parent;
    private final CloudAccount account;
    private final boolean joining;
    private final Runnable onJoined;

    private @Nullable LinkCode issued;
    private @Nullable LinkCode current;
    private @Nullable QrView qr;
    private LinkCodeText.@Nullable Text failure;
    private boolean busy;
    private boolean polling;
    private long lastPoll;

    private Button confirm;
    private Button decline;
    private Button newCode;

    /**
     * @param onJoined for a pending sign-in, runs once the join is confirmed and the player is signed in to the joined
     *                 account
     */
    public LinkCodeScreen(Screen parent, CloudAccount account, Runnable onJoined) {
        super(Component.translatable("heavymatica.link.title"));
        this.parent = parent;
        this.account = account;
        this.joining = account.status() == CloudAccount.Status.CHOOSING;
        this.onJoined = onJoined;
    }

    @Override
    protected void init() {
        int buttonY = height - 28;
        confirm = addRenderableWidget(Button.builder(Component.translatable("heavymatica.link.confirm"), button -> confirm())
                .bounds(width / 2 - 154, buttonY, 100, 20).build());
        decline = addRenderableWidget(Button.builder(Component.translatable("heavymatica.link.decline"), button -> decline())
                .bounds(width / 2 - 50, buttonY, 100, 20).build());
        newCode = addRenderableWidget(Button.builder(Component.translatable("heavymatica.link.new_code"), button -> issue())
                .bounds(width / 2 - 50, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("heavymatica.link.close"), button -> onClose())
                .bounds(width / 2 + 54, buttonY, 100, 20).build());
        updateButtons();
        if (issued == null && !busy) {
            issue();
        }
    }

    private void issue() {
        busy = true;
        failure = null;
        updateButtons();
        account.issueLinkCode().whenComplete((code, error) -> minecraft.execute(() -> {
            busy = false;
            if (error != null) {
                Heavymatica.LOGGER.info("Issuing a link code failed: {}", error.toString());
                failure = LinkCodeText.problem(CloudException.from(error, account.client().json()));
            } else {
                issued = code;
                current = code;
                qr = code.getLinkUrl() == null ? null : new QrView(QrMatrix.encode(code.getLinkUrl().toString()));
                lastPoll = System.currentTimeMillis();
            }
            updateButtons();
        }));
    }

    @Override
    public void tick() {
        updateButtons();
        LinkCode shown = current;
        if (shown == null || polling || busy || !view().live() || System.currentTimeMillis() - lastPoll < POLL_MILLIS) {
            return;
        }
        poll(shown.getId());
    }

    private void poll(String codeId) {
        polling = true;
        lastPoll = System.currentTimeMillis();
        account.pollLinkCode(codeId).whenComplete((code, error) -> minecraft.execute(() -> {
            polling = false;
            if (error == null && current != null && current.getId().equals(code.getId())) {
                current = code;
            }
            updateButtons();
        }));
    }

    private void confirm() {
        LinkCode shown = current;
        if (shown == null || busy) {
            return;
        }
        busy = true;
        updateButtons();
        account.confirmLinkCode(shown.getId()).whenComplete((code, error) -> minecraft.execute(() -> {
            if (error != null) {
                busy = false;
                failure = LinkCodeText.problem(CloudException.from(error, account.client().json()));
                poll(shown.getId());
                return;
            }
            current = code;
            if (!joining) {
                busy = false;
                account.refreshAccount().exceptionally(ignored -> null);
                updateButtons();
                return;
            }
            account.completeJoin().whenComplete((ignored, joinError) -> minecraft.execute(() -> {
                busy = false;
                if (joinError != null) {
                    failure = LinkCodeText.problem(CloudException.from(joinError, account.client().json()));
                    updateButtons();
                } else {
                    onJoined.run();
                }
            }));
        }));
    }

    private void decline() {
        LinkCode shown = current;
        if (shown == null || busy) {
            return;
        }
        busy = true;
        updateButtons();
        account.cancelLinkCode(shown.getId()).whenComplete((ignored, error) -> minecraft.execute(() -> {
            busy = false;
            if (error != null) {
                failure = LinkCodeText.problem(CloudException.from(error, account.client().json()));
            }
            poll(shown.getId());
        }));
    }

    private LinkCodeText.View view() {
        return LinkCodeText.of(current, Instant.now(), joining);
    }

    private void updateButtons() {
        if (confirm == null) {
            return;
        }
        LinkCodeText.View view = view();
        confirm.visible = view.canConfirm();
        decline.visible = view.canConfirm();
        newCode.visible = view.offerNewCode() || current == null && failure != null;
        confirm.active = !busy;
        decline.active = !busy;
        newCode.active = !busy;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        LinkCode shown = current;
        LinkCodeStatus status = shown == null ? null : LinkCodeText.effective(shown, Instant.now());
        if (shown != null && (status == LinkCodeStatus.OPEN || status == LinkCodeStatus.REDEEMED) && !busy) {
            account.cancelLinkCode(shown.getId()).exceptionally(ignored -> null);
        }
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable(joining ? "heavymatica.link.hint.join" : "heavymatica.link.hint.account"),
                width / 2, 22, 0xFFC0C0C0);
        LinkCodeText.View view = view();
        int y = 36;
        if (view.showCode() && issued != null && issued.getCode() != null) {
            String code = issued.getCode();
            graphics.pose().pushMatrix();
            graphics.pose().translate(width / 2f - font.width(code) * CODE_SCALE / 2f, y);
            graphics.pose().scale(CODE_SCALE, CODE_SCALE);
            graphics.text(font, code, 0, 0, 0xFFFFFFFF, false);
            graphics.pose().popMatrix();
            y += font.lineHeight * CODE_SCALE + 6;
            if (qr != null) {
                int module = Math.min(MAX_MODULE, QrMatrix.moduleSize(qr.modules(), height - y - 64));
                int side = qr.side(module);
                qr.draw(graphics, width / 2 - side / 2, y, module);
                y += side + 6;
            }
        } else {
            y += 20;
        }
        Component status = component(failure != null ? failure : view.status());
        graphics.textWithWordWrap(font, status, width / 2 - 150, y, 300, failure != null ? 0xFFFF8080 : 0xFFE0E0E0);
    }

    static Component component(LinkCodeText.Text text) {
        Object[] args = text.args().stream().map(arg -> arg instanceof LinkCodeText.Text nested ? component(nested) : arg).toArray();
        return Component.translatable(text.key(), args);
    }

    /** Development aid for {@link DevScreens}: the code as issued and the latest status. */
    Optional<LinkCode> issuedForDevelopment() {
        return Optional.ofNullable(issued);
    }

    Optional<LinkCode> currentForDevelopment() {
        return Optional.ofNullable(current);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
