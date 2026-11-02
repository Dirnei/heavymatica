package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassDialogScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassEditBox;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Uploads a local entry as a new schematic, or as a new version of a cloud schematic picked from a searchable list,
 * over the library. A line under the choices says what the upload becomes.
 */
final class UploadTargetScreen extends GlassDialogScreen {

    private static final int ROW = 18;
    private static final int CARD_GAP = 8;

    private final Screen back;
    private final String entryName;
    private final List<LibraryEntry> targets;
    private final Consumer<@Nullable LibraryEntry> onChoose;
    private boolean asVersion;
    private @Nullable LibraryEntry target;
    private String query = "";
    private int scroll;
    private ChoiceCard asNewCard;
    private ChoiceCard asVersionCard;
    private @Nullable GlassEditBox search;
    private GlassButton upload;

    UploadTargetScreen(Screen parent, String entryName, List<LibraryEntry> targets, Consumer<@Nullable LibraryEntry> onChoose) {
        super(parent, Component.translatable("heavymatica.upload.title", entryName));
        this.back = parent;
        this.entryName = entryName;
        this.targets = targets;
        this.onChoose = onChoose;
    }

    @Override
    protected int dialogWidth() {
        return 380;
    }

    @Override
    protected int dialogHeight() {
        return asVersion ? 300 : PAD + 18 + 14 + cardHeight() + 16 + 18 + PAD;
    }

    private int cardWidth() {
        return (content().w() - CARD_GAP) / 2;
    }

    private int cardHeight() {
        int w = (Math.min(dialogWidth(), width - 16) - 2 * PAD - CARD_GAP) / 2;
        return Math.max(ChoiceCard.heightFor(font, Component.translatable("heavymatica.upload.as_new.hint"), w),
                ChoiceCard.heightFor(font, Component.translatable("heavymatica.upload.as_version.hint"), w));
    }

    private Rect listArea() {
        Rect content = content();
        int top = content.y() + 14 + cardHeight() + 10 + 16 + 6;
        return new Rect(content.x(), top, content.w(), Math.max(0, frame().bottom() - PAD - 18 - 10 - 12 - top));
    }

    private List<LibraryEntry> shown() {
        String text = query.trim().toLowerCase(Locale.ROOT);
        return targets.stream().filter(entry -> text.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(text)).toList();
    }

    @Override
    protected void init() {
        super.init();
        Rect content = content();
        int y = content.y() + 14;
        asNewCard = addRenderableWidget(new ChoiceCard(content.x(), y, cardWidth(), cardHeight(), Component.translatable("heavymatica.upload.as_new"),
                Component.translatable("heavymatica.upload.as_new.hint"), button -> mode(false)));
        asVersionCard = addRenderableWidget(new ChoiceCard(content.x() + cardWidth() + CARD_GAP, y, cardWidth(), cardHeight(),
                Component.translatable("heavymatica.upload.as_version"), Component.translatable("heavymatica.upload.as_version.hint"),
                button -> mode(true)));
        asVersionCard.enable(!targets.isEmpty(), Component.translatable("heavymatica.upload.no_targets"));
        asNewCard.choose(!asVersion);
        asVersionCard.choose(asVersion);
        search = null;
        if (asVersion) {
            int searchY = y + cardHeight() + 10;
            search = addRenderableWidget(new GlassEditBox(font, content.x(), searchY, content.w(), 16, Component.translatable("heavymatica.upload.search"),
                    true));
            search.setValue(query);
            search.setResponder(text -> {
                query = text;
                scroll = 0;
            });
        }
        Rect frame = frame();
        int buttonY = frame.bottom() - PAD - 18;
        upload = addRenderableWidget(new GlassButton(frame.right() - PAD - 76, buttonY, 76, 18, Component.translatable("heavymatica.upload.button"),
                GlassButton.Kind.PRIMARY, button -> choose()));
        addRenderableWidget(new GlassButton(frame.right() - PAD - 76 - 6 - 70, buttonY, 70, 18, Component.translatable("gui.cancel"),
                GlassButton.Kind.SECONDARY, button -> onClose()));
        updateUpload();
    }

    private void mode(boolean version) {
        if (asVersion == version) {
            return;
        }
        asVersion = version;
        rebuildWidgets();
    }

    private void updateUpload() {
        boolean ready = !asVersion || target != null;
        upload.enable(ready, Component.translatable("heavymatica.upload.summary.pick"));
    }

    private void choose() {
        LibraryEntry chosen = asVersion ? target : null;
        if (asVersion && chosen == null) {
            return;
        }
        minecraft.setScreen(back);
        onChoose.accept(chosen);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(back);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (asVersion) {
            Rect list = listArea();
            if (list.contains(event.x(), event.y())) {
                int index = scroll + (int) ((event.y() - list.y()) / ROW);
                List<LibraryEntry> shown = shown();
                if (index >= 0 && index < shown.size()) {
                    target = shown.get(index);
                    updateUpload();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (asVersion && listArea().contains(mouseX, mouseY)) {
            int max = Math.max(0, shown().size() - listArea().h() / ROW);
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void extractDialog(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Rect frame = frame();
        Glass.text(graphics, font, title.getString(), frame.x() + PAD, frame.y() + PAD, GlassStyle.TEXT, frame.w() - 2 * PAD);
        Rect content = content();
        Glass.text(graphics, font, Component.translatable("heavymatica.upload.hint").getString(), content.x(), content.y() - 2, GlassStyle.HINT,
                content.w());
        if (asVersion) {
            rows(graphics, mouseX, mouseY);
        }
        LinkCodeText.Text summary = UploadChoice.summary(entryName, asVersion, target == null ? null : target.name(),
                target == null ? 0 : target.version());
        Glass.text(graphics, font, LinkCodeScreen.component(summary).getString(), content.x(), frame.bottom() - PAD - 13, GlassStyle.LABEL,
                frame.w() - 2 * PAD - 76 - 6 - 70 - 8);
    }

    private void rows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Rect list = listArea();
        List<LibraryEntry> shown = shown();
        int visible = list.h() / ROW;
        if (shown.isEmpty()) {
            Glass.text(graphics, font, Component.translatable("heavymatica.upload.no_match").getString(), list.x() + 4, list.y() + 4, GlassStyle.HINT,
                    list.w());
            return;
        }
        for (int row = 0; row < visible && row + scroll < shown.size(); row++) {
            LibraryEntry entry = shown.get(row + scroll);
            int y = list.y() + row * ROW;
            boolean selected = target != null && target.id().equals(entry.id());
            boolean hovered = mouseX >= list.x() && mouseX < list.right() && mouseY >= y && mouseY < y + ROW - 2;
            int fill = selected ? 0x1F7FD8FF : hovered ? 0x14FFFFFF : 0x08FFFFFF;
            int rim = selected ? 0x997FD8FF : 0;
            Glass.box(graphics, list.x(), y, list.w() - 6, ROW - 2, 5, fill, rim);
            String version = UploadChoice.versionLabel(entry.version(), selected);
            int versionWidth = font.width(version);
            String folder = entry.folder() == null ? "" : entry.folder();
            int folderWidth = Math.min(font.width(folder), (list.w() - 6) / 3);
            int nameWidth = list.w() - 6 - 12 - versionWidth - folderWidth - 16;
            Glass.text(graphics, font, entry.name(), list.x() + 6, y + 4, GlassStyle.TEXT, nameWidth);
            Glass.text(graphics, font, folder, list.right() - 6 - 6 - versionWidth - 8 - folderWidth, y + 4, GlassStyle.HINT, folderWidth);
            Glass.textRight(graphics, font, version, list.right() - 6 - 6, y + 4, selected ? GlassStyle.ACCENT : GlassStyle.HINT);
        }
        if (shown.size() > visible) {
            int view = visible * ROW;
            int thumb = Math.max(12, view * visible / shown.size());
            int top = list.y() + (view - thumb) * scroll / Math.max(1, shown.size() - visible);
            Glass.scrollBar(graphics, list.right() - 3, list.y(), view, top, thumb, list.contains(mouseX, mouseY), null);
        }
    }
}
