package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.CloudException;
import io.github.dirnei.mc.heavymatica.cloud.model.LinkCode;
import io.github.dirnei.mc.heavymatica.cloud.model.LinkCodeStatus;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassDialogScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Shows a link code with its QR code and the time left, and asks the player to confirm once someone redeemed it.
 * Serves the signed-in account (another sign-in joins it) and the pending sign-in (it joins an existing account).
 * Closing the dialog cancels a code that is still open or waiting for confirmation. Codes are only ever shown, never
 * entered.
 */
public final class LinkCodeScreen extends GlassDialogScreen {

    private static final long POLL_MILLIS = 2000;
    private static final int MAX_MODULE = 4;
    private static final int BOX_W = 17;
    private static final int BOX_H = 22;
    private static final int BOX_GAP = 3;
    private static final int GROUP_GAP = 10;
    private static final Duration CODE_LIFE = Duration.ofMinutes(5);

    private final Screen back;
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

    private GlassButton confirm;
    private GlassButton decline;
    private GlassButton newCode;

    /**
     * @param onJoined for a pending sign-in, runs once the join is confirmed and the player is signed in to the joined
     *                 account
     */
    public LinkCodeScreen(Screen parent, CloudAccount account, Runnable onJoined) {
        super(parent, Component.translatable(account.status() == CloudAccount.Status.CHOOSING ? "heavymatica.link.title.join" : "heavymatica.link.title"));
        this.back = parent;
        this.account = account;
        this.joining = account.status() == CloudAccount.Status.CHOOSING;
        this.onJoined = onJoined;
    }

    @Override
    protected int dialogWidth() {
        return 400;
    }

    @Override
    protected int dialogHeight() {
        return 250;
    }

    private Component hint() {
        return Component.translatable(joining ? "heavymatica.link.hint.join" : "heavymatica.link.hint.account");
    }

    private int hintHeight() {
        return Glass.wrap(font, hint(), content().w()).size() * Glass.LINE;
    }

    private Rect qrArea() {
        Rect content = content();
        int top = content.y() - 4 + hintHeight() + 6;
        int side = Math.min(confirmArea().y() - 8 - top, 130);
        return new Rect(content.x(), top, Math.max(0, side), Math.max(0, side));
    }

    private Rect codeArea() {
        Rect content = content();
        Rect qr = qrArea();
        return new Rect(qr.right() + 14, qr.y(), content.right() - qr.right() - 14, qr.h());
    }

    private Rect confirmArea() {
        Rect frame = frame();
        return new Rect(frame.x() + PAD, frame.bottom() - PAD - 40, frame.w() - 2 * PAD, 40);
    }

    @Override
    protected void init() {
        super.init();
        Rect frame = frame();
        addRenderableWidget(new GlassButton(frame.right() - PAD - 16, frame.y() + PAD - 4, 16, 16, Component.literal("×"), GlassButton.Kind.QUIET,
                button -> onClose()));
        Rect confirmArea = confirmArea();
        int y = confirmArea.y() + 11;
        confirm = addRenderableWidget(new GlassButton(confirmArea.right() - 8 - 64, y, 64, 18, Component.translatable("heavymatica.link.confirm"),
                GlassButton.Kind.PRIMARY, button -> confirm()));
        decline = addRenderableWidget(new GlassButton(confirmArea.right() - 8 - 64 - 6 - 64, y, 64, 18, Component.translatable("heavymatica.link.decline"),
                GlassButton.Kind.SECONDARY, button -> decline()));
        Rect code = codeArea();
        newCode = addRenderableWidget(new GlassButton(code.right() - 70, code.y() + 56, 70, 18, Component.translatable("heavymatica.link.new_code"),
                GlassButton.Kind.SECONDARY, button -> issue()));
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
        minecraft.setScreen(back);
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
    protected void extractDialog(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Rect frame = frame();
        Glass.text(graphics, font, title.getString(), frame.x() + PAD, frame.y() + PAD, GlassStyle.TEXT, frame.w() - 2 * PAD - 20);
        Rect content = content();
        Glass.paragraph(graphics, font, hint(), content.x(), content.y() - 4, content.w(), GlassStyle.HINT);
        LinkCodeText.View view = view();
        boolean codeShown = (view.showCode() || view.canConfirm()) && issued != null && issued.getCode() != null;
        Rect qrArea = qrArea();
        Rect codeArea = codeArea();
        if (codeShown) {
            drawQr(graphics, qrArea, view.canConfirm());
            drawCode(graphics, codeArea, issued.getCode(), view.canConfirm());
        } else {
            Glass.box(graphics, qrArea.x(), qrArea.y(), qrArea.w(), qrArea.h(), 8, 0x0AFFFFFF, 0x14FFFFFF);
        }
        if (view.canConfirm()) {
            drawConfirmation(graphics, view);
        } else if (failure != null || !view.showCode()) {
            Component status = component(failure != null ? failure : view.status());
            int textY = codeShown ? codeArea.y() + 76 : codeArea.y() + 8;
            Glass.paragraph(graphics, font, status, codeArea.x(), textY, codeArea.w(), failure != null ? GlassStyle.DANGER_TEXT : GlassStyle.LABEL);
        }
    }

    private void drawQr(GuiGraphicsExtractor graphics, Rect area, boolean faded) {
        Glass.fill(graphics, area.x(), area.y(), area.w(), area.h(), 8, faded ? 0x80F1EFE6 : GlassStyle.GHOST_LIGHT);
        if (qr == null) {
            return;
        }
        int module = Math.min(MAX_MODULE, QrMatrix.moduleSize(qr.modules(), area.w() - 8));
        int side = qr.side(module);
        qr.draw(graphics, area.x() + (area.w() - side) / 2, area.y() + (area.h() - side) / 2, module, faded);
    }

    private void drawCode(GuiGraphicsExtractor graphics, Rect area, String code, boolean faded) {
        Glass.text(graphics, font, Component.translatable("heavymatica.link.code").getString(), area.x(), area.y(), GlassStyle.HINT, area.w());
        List<String> groups = LinkCodeText.groups(code);
        int x = area.x();
        int y = area.y() + 12;
        int boxRim = faded ? 0x337FD8FF : 0x737FD8FF;
        int text = faded ? GlassStyle.SUBTITLE : GlassStyle.TEXT;
        for (String group : groups) {
            for (int i = 0; i < group.length(); i++) {
                Glass.box(graphics, x, y, BOX_W, BOX_H, 4, GlassStyle.FIELD, boxRim);
                String letter = group.substring(i, i + 1);
                Glass.bigText(graphics, font, letter, x + (BOX_W - font.width(letter) * 2) / 2 + 1, y + 4, text, 2);
                x += BOX_W + BOX_GAP;
            }
            x += GROUP_GAP - BOX_GAP;
        }
        if (faded) {
            return;
        }
        LinkCode shown = current;
        Instant now = Instant.now();
        double left = shown == null ? 0 : LinkCodeText.fractionLeft(shown.getExpiresAt(), now, CODE_LIFE);
        int barY = y + BOX_H + 8;
        String remaining = shown == null ? "" : LinkCodeText.countdown(shown.getExpiresAt() == null ? Duration.ZERO
                : Duration.between(now, shown.getExpiresAt().toInstant()));
        int barWidth = Math.max(20, Math.min(area.w() - 80, 4 * (BOX_W + BOX_GAP) * 2));
        Glass.bar(graphics, area.x(), barY + 2, barWidth, 4, new long[] {Math.round(left * 1000)}, 1000, new int[] {GlassStyle.ACCENT});
        Glass.text(graphics, font, Component.translatable("heavymatica.link.left", remaining).getString(), area.x() + barWidth + 6, barY,
                GlassStyle.LABEL, area.w() - barWidth - 6);
        Glass.text(graphics, font, Component.translatable("heavymatica.link.waiting").getString(), area.x(), barY + 14, GlassStyle.HINT, area.w());
    }

    private void drawConfirmation(GuiGraphicsExtractor graphics, LinkCodeText.View view) {
        Rect area = confirmArea();
        Glass.box(graphics, area.x(), area.y(), area.w(), area.h(), 8, 0x177FD8FF, 0x737FD8FF);
        int textWidth = area.w() - 16 - 2 * 64 - 6 - 8;
        Component status = component(failure != null ? failure : view.status());
        List<net.minecraft.util.FormattedCharSequence> lines = Glass.wrap(font, status, textWidth);
        int y = area.y() + (area.h() - Math.min(3, lines.size()) * Glass.LINE) / 2 + 1;
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            graphics.text(font, lines.get(i), area.x() + 8, y + i * Glass.LINE, failure != null ? GlassStyle.DANGER_TEXT : GlassStyle.TEXT, false);
        }
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
}
