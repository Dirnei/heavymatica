package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.importing.ImportSources;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import io.github.dirnei.mc.heavymatica.ui.glass.TextFit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Lists schematic files in {@code schematics/} (Litematica's folder) and {@code heavymatica/import/}, including
 * subfolders, with their format and size, and imports the ones the player ticks. Files dropped anywhere on it are
 * imported too. For an empty library it is also the first thing shown, with a word on capturing.
 */
final class ImportScreen extends GlassScreen {

    private static final int ROW = 18;

    private final Screen parent;
    private final Path gameDirectory;
    private final Consumer<List<Path>> onImport;
    private final boolean firstRun;
    private final Set<Path> chosen = new HashSet<>();
    private final Map<Path, Long> sizes = new HashMap<>();
    private List<ImportSources.Candidate> candidates = List.of();
    private ImportLayout layout;
    private GlassButton importButton;
    private double scroll;

    ImportScreen(Screen parent, Path gameDirectory, Consumer<List<Path>> onImport, boolean firstRun) {
        super(Component.translatable("heavymatica.import.title"));
        this.parent = parent;
        this.gameDirectory = gameDirectory;
        this.onImport = onImport;
        this.firstRun = firstRun;
    }

    @Override
    protected void init() {
        candidates = ImportSources.scan(gameDirectory);
        for (ImportSources.Candidate candidate : candidates) {
            sizes.computeIfAbsent(candidate.file(), ImportScreen::size);
        }
        layout = ImportLayout.of(width, height, firstRun);
        Rect back = layout.back();
        addRenderableWidget(new GlassButton(back.x(), back.y(), back.w(), back.h(), Component.translatable("heavymatica.import.back"),
                GlassButton.Kind.SECONDARY, button -> onClose()));
        Rect actions = layout.actions();
        int importWidth = Math.min(130, actions.w() / 2);
        importButton = addRenderableWidget(new GlassButton(actions.x(), actions.y(), importWidth, actions.h(), Component.empty(),
                GlassButton.Kind.PRIMARY, button -> importChosen()).radius(7));
        int folderWidth = font.width(Component.translatable("heavymatica.import.open_folder")) + 16;
        addRenderableWidget(new GlassButton(actions.x() + importWidth + 6, actions.y(), folderWidth, actions.h(),
                Component.translatable("heavymatica.import.open_folder"), GlassButton.Kind.SECONDARY, button -> openFolder()));
        updateImportButton();
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1;
        }
    }

    private void updateImportButton() {
        importButton.setMessage(Component.translatable("heavymatica.import.import_count", chosen.size()));
        importButton.enable(!chosen.isEmpty(), Component.translatable("heavymatica.import.tick_first"));
    }

    private void importChosen() {
        List<Path> files = candidates.stream().map(ImportSources.Candidate::file).filter(chosen::contains).toList();
        minecraft.setScreen(parent);
        if (!files.isEmpty()) {
            onImport.accept(files);
        }
    }

    private void openFolder() {
        Path folder = gameDirectory.resolve("schematics");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            Heavymatica.LOGGER.warn("Could not create {}", folder, e);
        }
        Util.getPlatform().openPath(folder);
    }

    /** Ticks every listed file; used by the dev screen script. */
    void chooseAll() {
        candidates.forEach(candidate -> chosen.add(candidate.file()));
        updateImportButton();
    }

    /** Imports the ticked files; used by the dev screen script. */
    void confirmForDevelopment() {
        importChosen();
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        minecraft.setScreen(parent);
        onImport.accept(files);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        Rect header = layout.header();
        Glass.text(graphics, font, title.getString(), header.x(), header.y() + 2, GlassStyle.TEXT, header.w() - layout.back().w() - 8);
        Glass.text(graphics, font, Component.translatable(firstRun ? "heavymatica.import.subtitle.empty" : "heavymatica.import.subtitle").getString(),
                header.x(), header.y() + 13, GlassStyle.HINT, header.w() - layout.back().w() - 8);
        Rect main = layout.main();
        Glass.panel(graphics, main.x(), main.y(), main.w(), main.h());
        if (layout.capture() != null) {
            capturePanel(graphics, layout.capture());
        }
        Rect intro = layout.intro();
        if (intro.h() > 0) {
            Glass.text(graphics, font, Component.translatable("heavymatica.import.first.title").getString(), intro.x(), intro.y(), GlassStyle.TEXT, intro.w());
            Glass.paragraph(graphics, font, Component.translatable("heavymatica.import.first.text"), intro.x(), intro.y() + 13, intro.w(), GlassStyle.LABEL);
        }
        Rect list = layout.list();
        Glass.text(graphics, font, Component.translatable("heavymatica.import.found", candidates.size()).getString().toUpperCase(Locale.ROOT),
                list.x(), list.y() - 12, GlassStyle.HINT, list.w());
        Rect actions = layout.actions();
        int hintX = actions.x() + Math.min(130, actions.w() / 2) + 6 + font.width(Component.translatable("heavymatica.import.open_folder")) + 16 + 8;
        Glass.text(graphics, font, Component.translatable("heavymatica.import.drop_hint").getString(), hintX, actions.y() + (actions.h() - 8) / 2,
                GlassStyle.HINT, actions.right() - hintX);
        checklist(graphics, list, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partial);
    }

    private void checklist(GuiGraphicsExtractor graphics, Rect list, int mouseX, int mouseY) {
        if (candidates.isEmpty()) {
            Glass.box(graphics, list.x(), list.y(), list.w(), Math.min(list.h(), 40), 7, 0x0D7FD8FF, 0x737FD8FF);
            Glass.paragraph(graphics, font, Component.translatable("heavymatica.import.none"), list.x() + 8, list.y() + 8, list.w() - 16, GlassStyle.LABEL);
            return;
        }
        int content = candidates.size() * ROW;
        scroll = Math.clamp(scroll, 0, Math.max(0, content - list.h()));
        Glass.box(graphics, list.x(), list.y(), list.w(), Math.min(list.h(), content), 7, 0x08FFFFFF, 0x14FFFFFF);
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        for (int i = 0; i < candidates.size(); i++) {
            ImportSources.Candidate candidate = candidates.get(i);
            int y = list.y() + i * ROW - (int) scroll;
            if (y + ROW < list.y() || y > list.bottom()) {
                continue;
            }
            boolean ticked = chosen.contains(candidate.file());
            boolean hovered = list.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW;
            if (ticked || hovered) {
                graphics.fill(list.x() + 1, y, list.right() - 1, y + ROW, ticked ? 0x147FD8FF : 0x0AFFFFFF);
            }
            if (i > 0) {
                graphics.fill(list.x() + 6, y, list.right() - 6, y + 1, 0x0FFFFFFF);
            }
            int box = 10;
            int bx = list.x() + 7;
            int by = y + (ROW - box) / 2;
            if (ticked) {
                Glass.fill(graphics, bx, by, box, box, 2.5, GlassStyle.ACCENT);
                graphics.text(font, "✔", bx + 1, by + 1, GlassStyle.ON_ACCENT, false);
            } else {
                Glass.box(graphics, bx, by, box, box, 2.5, 0x33000000, 0x66FFFFFF);
            }
            String detail = format(candidate) + " · " + size(sizes.getOrDefault(candidate.file(), -1L));
            int detailWidth = font.width(detail);
            Glass.textRight(graphics, font, detail, list.right() - 8, y + 5, GlassStyle.HINT);
            String name = candidate.folder() + "/" + candidate.relativePath();
            TextFit fit = Glass.text(graphics, font, name, bx + box + 7, y + 5, ticked ? GlassStyle.TEXT : GlassStyle.LABEL,
                    list.w() - box - 30 - detailWidth - 8);
            Glass.tooltipIfShortened(graphics, font, fit, list.x(), y, list.w(), ROW, mouseX, mouseY);
        }
        graphics.disableScissor();
        if (content > list.h()) {
            int thumb = Math.max(12, list.h() * list.h() / content);
            int thumbTop = list.y() + (int) Math.round(scroll / (content - list.h()) * (list.h() - thumb));
            Glass.scrollBar(graphics, list.right() + 3, list.y(), list.h(), thumbTop, thumb, false, true);
        }
    }

    private void capturePanel(GuiGraphicsExtractor graphics, Rect panel) {
        Glass.panel(graphics, panel.x(), panel.y(), panel.w(), panel.h(), Glass.RADIUS, 0xDB1C232C, 0xD60E1218, 0x1AFFFFFF);
        int x = panel.x() + 12;
        int w = panel.w() - 24;
        int y = panel.y() + 12;
        Glass.text(graphics, font, Component.translatable("heavymatica.import.capture.label").getString().toUpperCase(Locale.ROOT), x, y, GlassStyle.HINT, w);
        Glass.text(graphics, font, Component.translatable("heavymatica.import.capture.title").getString(), x, y + 13, GlassStyle.TEXT, w);
        Glass.paragraph(graphics, font, Component.translatable("heavymatica.import.capture.text"), x, y + 28, w, GlassStyle.LABEL);
    }

    /** The format name for a file's extension. */
    private static String format(ImportSources.Candidate candidate) {
        String name = candidate.file().getFileName().toString().toLowerCase(Locale.ROOT);
        String key = name.endsWith(".litematic") ? "litematica" : name.endsWith(".nbt") ? "structure" : "sponge";
        return Component.translatable("heavymatica.format." + key).getString();
    }

    private static String size(long bytes) {
        if (bytes < 0) {
            return "?";
        }
        if (bytes < 1024 * 1024) {
            return Math.max(1, Math.round(bytes / 1024.0)) + " KiB";
        }
        return String.format(Locale.ROOT, "%.1f MiB", bytes / 1024.0 / 1024.0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        Rect list = layout.list();
        int index = (int) ((event.y() - list.y() + scroll) / ROW);
        if (list.contains(event.x(), event.y()) && index >= 0 && index < candidates.size()) {
            Path file = candidates.get(index).file();
            if (!chosen.remove(file)) {
                chosen.add(file);
            }
            updateImportButton();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * ROW;
        return true;
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(title, Component.translatable("heavymatica.import.found", candidates.size()));
    }
}
