package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.cloud.model.Account;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The library's account chip: name, a status dot and the quota bar while signed in, otherwise a sign-in button or the
 * sign-in in progress.
 */
final class AccountChip extends GlassButton {

    private final CloudAccount account;
    private final BooleanSupplier offline;

    AccountChip(int x, int y, int width, int height, CloudAccount account, BooleanSupplier offline, Consumer<GlassButton> onPress) {
        super(x, y, width, height, Component.translatable("heavymatica.account.button"), Kind.SECONDARY, onPress);
        this.account = account;
        this.offline = offline;
    }

    /** Keeps the message in step with the state, so the dev scripts and narration name what the chip does. */
    void update() {
        setMessage(Component.translatable(switch (account.status()) {
            case SIGNED_IN -> "heavymatica.account.button";
            case SIGNING_IN -> "heavymatica.account.signing_in";
            case CHOOSING -> "heavymatica.account.choosing";
            default -> "heavymatica.account.sign_in";
        }));
        active = account.status() != CloudAccount.Status.SIGNING_IN;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (account.status() != CloudAccount.Status.SIGNED_IN) {
            super.extractContents(graphics, mouseX, mouseY, a);
            return;
        }
        Font font = Minecraft.getInstance().font;
        boolean hot = isHovered() || isFocused();
        Glass.box(graphics, getX(), getY(), width, height, Glass.SMALL_RADIUS, hot ? 0x1FFFFFFF : 0x0FFFFFFF, hot ? 0x47FFFFFF : 0x29FFFFFF);
        Glass.fill(graphics, getX() + 3, getY() + 3, height - 6, height - 6, 2, 0xFFC68A5C);
        graphics.fill(getX() + 3, getY() + height - 7, getX() + height - 3, getY() + height - 3, 0xFF5A3A28);
        int textX = getX() + height + 1;
        String name = account.name().orElse("");
        Glass.text(graphics, font, name, textX, getY() + 2, GlassStyle.TEXT, width - height - 14);
        Glass.fill(graphics, getX() + width - 9, getY() + 4, 5, 5, 2.5, offline.getAsBoolean() ? GlassStyle.WARNING : GlassStyle.CONFIRM);
        Optional<Account> info = account.account();
        if (info.isPresent() && info.get().getQuotaBytes() > 0) {
            long used = info.get().getUsedBytes();
            long quota = info.get().getQuotaBytes();
            int barWidth = Math.max(10, width - height - 6 - 4 - font.width("000/000") );
            Glass.bar(graphics, textX, getY() + height - 6, Math.min(barWidth, 34), 3, new long[] {used}, quota, new int[] {GlassStyle.ACCENT});
            String text = mebibytes(used) + "/" + mebibytes(quota);
            Glass.text(graphics, font, text, textX + Math.min(barWidth, 34) + 3, getY() + height - 9, GlassStyle.HINT, width - height - 40);
        }
    }

    private static String mebibytes(long bytes) {
        double mib = bytes / 1024.0 / 1024.0;
        return mib >= 10 ? Long.toString(Math.round(mib)) : String.format(Locale.ROOT, "%.1f", mib);
    }
}
