package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.importing.ImportSources;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Lists schematic files in {@code schematics/} (Litematica's folder) and {@code heavymatica/import/}, including
 * subfolders, and imports the ones the player ticks.
 */
final class ImportScreen extends Screen {

    private static final int ROW = 12;

    private final Screen parent;
    private final Path gameDirectory;
    private final Consumer<List<Path>> onImport;
    private final Set<Path> chosen = new HashSet<>();
    private List<ImportSources.Candidate> candidates = List.of();
    private double scroll;

    ImportScreen(Screen parent, Path gameDirectory, Consumer<List<Path>> onImport) {
        super(Component.translatable("heavymatica.import.title"));
        this.parent = parent;
        this.gameDirectory = gameDirectory;
        this.onImport = onImport;
    }

    @Override
    protected void init() {
        candidates = ImportSources.scan(gameDirectory);
        addRenderableWidget(Button.builder(Component.translatable("heavymatica.import.selected"), button -> importChosen())
                .bounds(width / 2 - 122, height - 28, 120, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).bounds(width / 2 + 2, height - 28, 120, 20).build());
    }

    private void importChosen() {
        List<Path> files = candidates.stream().map(ImportSources.Candidate::file).filter(chosen::contains).toList();
        minecraft.setScreen(parent);
        if (!files.isEmpty()) {
            onImport.accept(files);
        }
    }

    /** Ticks or unticks every listed file; used by the dev screen script. */
    void chooseAll() {
        candidates.forEach(candidate -> chosen.add(candidate.file()));
    }

    /** Imports the ticked files; used by the dev screen script. */
    void confirmForDevelopment() {
        importChosen();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        int top = 28;
        int bottom = height - 36;
        if (candidates.isEmpty()) {
            graphics.centeredText(font, Component.translatable("heavymatica.import.none"), width / 2, top + 20, 0xFF909090);
            return;
        }
        graphics.enableScissor(20, top, width - 20, bottom);
        for (int i = 0; i < candidates.size(); i++) {
            ImportSources.Candidate candidate = candidates.get(i);
            int y = top + i * ROW - (int) scroll;
            boolean ticked = chosen.contains(candidate.file());
            graphics.fill(24, y, 32, y + 8, ticked ? 0xFF60D060 : 0xFF404040);
            graphics.text(font, candidate.folder() + "/" + candidate.relativePath(), 38, y, ticked ? 0xFFFFFFFF : 0xFFC0C0C0, false);
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int index = (int) ((event.y() - 28 + scroll) / ROW);
        if (event.x() >= 20 && event.x() < width - 20 && event.y() >= 28 && event.y() < height - 36 && index >= 0 && index < candidates.size()) {
            Path file = candidates.get(index).file();
            if (!chosen.remove(file)) {
                chosen.add(file);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(Math.max(0, candidates.size() * ROW - (height - 64)), scroll - scrollY * ROW));
        return true;
    }
}
