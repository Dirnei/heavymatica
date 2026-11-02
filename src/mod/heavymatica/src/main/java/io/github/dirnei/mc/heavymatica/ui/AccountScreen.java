package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.CloudException;
import io.github.dirnei.mc.heavymatica.cloud.model.Account;
import io.github.dirnei.mc.heavymatica.cloud.model.Identity;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassConfirmScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The signed-in account: name, storage against quota and sign out beside the linked sign-ins with the one in use
 * marked, a link code for another sign-in when the cloud offers linking, and unlinking any sign-in but the last.
 */
public final class AccountScreen extends GlassScreen {

    private final Screen parent;
    private final CloudAccount account;
    private @Nullable Runnable subscription;
    private @Nullable Component message;
    private boolean messageIsError;
    private AccountLayout layout;
    private final List<GlassButton> unlinkButtons = new ArrayList<>();
    private int scroll;

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
        layout = AccountLayout.of(width, height);
        Rect back = layout.back();
        addRenderableWidget(new GlassButton(back.x(), back.y(), back.w(), back.h(), Component.translatable("heavymatica.details.back"),
                GlassButton.Kind.SECONDARY, button -> onClose()));
        Rect link = layout.linkAnother();
        GlassButton linkButton = addRenderableWidget(new GlassButton(link.x(), link.y(), link.w(), link.h(),
                Component.translatable("heavymatica.account.link_another"), GlassButton.Kind.ACCENT,
                button -> minecraft.setScreen(new LinkCodeScreen(this, account, () -> { }))));
        linkButton.visible = account.account().map(Account::getLinkingAvailable).orElse(false);
        Rect profile = layout.profile().inset(12);
        addRenderableWidget(new GlassButton(profile.x(), profile.bottom() - 18 - signOutNoteHeight(profile.w()) - 4, profile.w(), 18,
                Component.translatable("heavymatica.account.sign_out"), GlassButton.Kind.DANGER, button -> {
                    account.signOut();
                    onClose();
                }));
        unlinkButtons.clear();
        List<Identity> identities = identities();
        scroll = Math.clamp(scroll, 0, Math.max(0, identities.size() - layout.visibleRows()));
        for (Identity identity : identities) {
            GlassButton unlink = new GlassButton(0, 0, 58, 16, Component.translatable("heavymatica.account.unlink"), GlassButton.Kind.DANGER,
                    button -> confirmUnlink(identity));
            unlinkButtons.add(addRenderableWidget(unlink));
        }
        placeRows();
    }

    private int signOutNoteHeight(int width) {
        return Glass.wrap(font, Component.translatable("heavymatica.account.sign_out.note"), width).size() * Glass.LINE;
    }

    /** Puts each row's unlink button beside its row, hiding the rows scrolled out of the list. */
    private void placeRows() {
        Rect list = layout.list();
        int count = unlinkButtons.size();
        for (int i = 0; i < count; i++) {
            GlassButton button = unlinkButtons.get(i);
            int row = i - scroll;
            boolean shown = row >= 0 && row < layout.visibleRows();
            button.visible = shown && count > 1;
            button.setPosition(list.right() - button.getWidth() - 6, list.y() + row * AccountLayout.ROW + (AccountLayout.ROW - 4 - button.getHeight()) / 2);
        }
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
        minecraft.setScreen(new GlassConfirmScreen(this, Component.translatable("heavymatica.account.unlink.title", who), warning,
                Component.translatable("heavymatica.account.unlink"), Component.translatable("gui.cancel"), true, confirmed -> {
                    if (confirmed) {
                        unlink(identity);
                    }
                }));
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout.list().contains(mouseX, mouseY)) {
            int max = Math.max(0, identities().size() - layout.visibleRows());
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, max);
            placeRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        Glass.text(graphics, font, title.getString(), layout.back().right() + 10, layout.back().y() + 5, GlassStyle.HINT, width);
        profile(graphics);
        signIns(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partial);
    }

    private void profile(GuiGraphicsExtractor graphics) {
        Rect panel = layout.profile();
        Glass.panel(graphics, panel.x(), panel.y(), panel.w(), panel.h());
        Rect inner = panel.inset(12);
        Glass.fill(graphics, inner.x(), inner.y(), 30, 30, 4, 0xFFC68A5C);
        graphics.fill(inner.x(), inner.y() + 20, inner.x() + 30, inner.y() + 30, 0xFF5A3A28);
        String name = account.name().orElse("?");
        int nameX = inner.x() + 38;
        if (font.width(name) * 2 <= inner.right() - nameX) {
            Glass.bigText(graphics, font, name, nameX, inner.y(), GlassStyle.TEXT, 2);
        } else {
            Glass.text(graphics, font, name, nameX, inner.y() + 4, GlassStyle.TEXT, inner.right() - nameX);
        }
        Glass.fill(graphics, nameX, inner.y() + 22, 5, 5, 2.5, GlassStyle.CONFIRM);
        Glass.text(graphics, font, Component.translatable("heavymatica.account.signed_in").getString(), nameX + 9, inner.y() + 21,
                GlassStyle.CONFIRM, inner.right() - nameX - 9);

        int y = inner.y() + 44;
        Account info = account.account().orElse(null);
        Glass.text(graphics, font, Component.translatable("heavymatica.account.storage").getString(), inner.x(), y, GlassStyle.HINT, inner.w() / 2);
        if (info != null) {
            long used = info.getUsedBytes() == null ? 0 : info.getUsedBytes();
            long quota = info.getQuotaBytes() == null ? 0 : info.getQuotaBytes();
            Glass.textRight(graphics, font, Component.translatable("heavymatica.account.usage", mebibytes(used), mebibytes(quota)).getString(),
                    inner.right(), y, GlassStyle.TEXT);
            Glass.bar(graphics, inner.x(), y + 13, inner.w(), 6, new long[] {used}, Math.max(quota, 1), new int[] {GlassStyle.ACCENT});
        }
        int noteHeight = signOutNoteHeight(inner.w());
        Glass.paragraph(graphics, font, Component.translatable("heavymatica.account.sign_out.note"), inner.x(), inner.bottom() - noteHeight,
                inner.w(), GlassStyle.SUBTITLE);
    }

    private void signIns(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Rect panel = layout.signIns();
        Glass.panel(graphics, panel.x(), panel.y(), panel.w(), panel.h());
        Rect inner = panel.inset(10);
        int titleWidth = layout.linkAnother().x() - inner.x() - 8;
        Glass.text(graphics, font, Component.translatable("heavymatica.account.sign_ins").getString(), inner.x(), inner.y(), GlassStyle.TEXT, titleWidth);
        Glass.text(graphics, font, Component.translatable("heavymatica.account.sign_ins.hint").getString(), inner.x(), inner.y() + 12,
                GlassStyle.HINT, titleWidth);

        Rect list = layout.list();
        List<Identity> identities = identities();
        int rows = Math.min(layout.visibleRows(), identities.size() - scroll);
        for (int row = 0; row < rows; row++) {
            Identity identity = identities.get(row + scroll);
            int y = list.y() + row * AccountLayout.ROW;
            boolean current = Boolean.TRUE.equals(identity.getCurrent());
            Glass.box(graphics, list.x(), y, list.w(), AccountLayout.ROW - 4, 6, current ? 0x0F7EE28F : 0x08FFFFFF, current ? 0x4D7EE28F : 0x10FFFFFF);
            int iconX = list.x() + 6;
            int iconY = y + 4;
            if ("minecraft".equals(identity.getProvider())) {
                Glass.fill(graphics, iconX, iconY, 16, 16, 3, 0xFF8A6A45);
                graphics.fill(iconX, iconY, iconX + 16, iconY + 6, 0xFF6FA048);
            } else {
                Glass.box(graphics, iconX, iconY, 16, 16, 8, 0x47000000, 0x80C9D2DA);
                Glass.textCentred(graphics, font, LinkCodeText.providerName(identity.getProvider()).substring(0, 1), iconX + 8, iconY + 4,
                        GlassStyle.LABEL, 14);
            }
            int textX = iconX + 22;
            int right = list.right() - (identities.size() > 1 ? 64 : 6) - 6;
            int chipWidth = current ? Glass.chipWidth(font, Component.translatable("heavymatica.account.in_use").getString()) + 6 : 0;
            Glass.text(graphics, font, identity.getName() == null ? "?" : identity.getName(), textX, y + 3, GlassStyle.TEXT, right - chipWidth - textX);
            Glass.text(graphics, font, LinkCodeText.providerName(identity.getProvider()), textX, y + 13, GlassStyle.HINT, right - chipWidth - textX);
            if (current) {
                Glass.chip(graphics, font, Component.translatable("heavymatica.account.in_use").getString(), right - chipWidth + 6, y + 5,
                        0x1F7EE28F, GlassStyle.CONFIRM, 0x807EE28F);
            }
        }
        if (identities.size() > layout.visibleRows()) {
            int view = layout.visibleRows() * AccountLayout.ROW;
            int thumb = Math.max(12, view * layout.visibleRows() / identities.size());
            int top = list.y() + (view - thumb) * scroll / Math.max(1, identities.size() - layout.visibleRows());
            Glass.scrollBar(graphics, list.right() + 2, list.y(), view, top, thumb, list.contains(mouseX, mouseY), null);
        }
        Component foot = message != null ? message : Component.translatable("heavymatica.account.sign_ins.note");
        int footColor = message == null ? GlassStyle.SUBTITLE : messageIsError ? GlassStyle.DANGER_TEXT : GlassStyle.CONFIRM;
        List<net.minecraft.util.FormattedCharSequence> lines = Glass.wrap(font, foot, inner.w());
        int shown = Math.min(2, lines.size());
        for (int i = 0; i < shown; i++) {
            graphics.text(font, lines.get(i), inner.x(), inner.bottom() - (shown - i) * Glass.LINE, footColor, false);
        }
    }

    private static String mebibytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / 1024.0 / 1024.0);
    }
}
