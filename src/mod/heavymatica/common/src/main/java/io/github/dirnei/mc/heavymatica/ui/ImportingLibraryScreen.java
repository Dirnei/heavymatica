package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.CloudAvailability;
import io.github.dirnei.mc.heavymatica.account.CloudException;
import io.github.dirnei.mc.heavymatica.cloud.model.ProblemCode;
import io.github.dirnei.mc.heavymatica.formats.SchematicReader;
import io.github.dirnei.mc.heavymatica.importing.Importer;
import io.github.dirnei.mc.heavymatica.importing.Uploader;
import io.github.dirnei.mc.heavymatica.library.Library;
import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import io.github.dirnei.mc.heavymatica.library.LocalLibrary;
import io.github.dirnei.mc.heavymatica.thumbnail.ThumbnailRenderer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassConfirmScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The library screen with importing and uploading: files dropped on the screen or picked
 * from the schematic folders become local entries; local entries upload as a new schematic or a new version.
 */
public final class ImportingLibraryScreen extends LibraryScreen {

    private final Importer importer;
    private final Uploader uploader;
    private @Nullable GlassButton upload;
    private boolean firstRunChecked;

    public ImportingLibraryScreen(CloudAccount account, CloudAvailability availability, Library library, Placer placer) {
        super(account, availability, library, placer);
        this.importer = new Importer(library.local(), library::entries);
        this.uploader = new Uploader(account.client(), library.local(), ImportingLibraryScreen::renderThumbnail);
    }

    @Override
    protected void addExtraTopButtons(Rect area) {
        addRenderableWidget(new GlassButton(area.x(), area.y(), area.w(), area.h(), Component.translatable("heavymatica.import.button"),
                GlassButton.Kind.SECONDARY, button -> openImport(false)));
    }

    @Override
    protected void init() {
        super.init();
        firstRunCheck();
    }

    @Override
    protected void refreshEntries() {
        super.refreshEntries();
        firstRunCheck();
    }

    /** An empty library opens on the import screen once its listing has loaded; only on the first look per opening. */
    private void firstRunCheck() {
        if (firstRunChecked || minecraft == null || minecraft.screen != this) {
            return;
        }
        LibraryStart start = LibraryStart.decide(library().entries().size(), library().cloudState() == Library.CloudState.LOADING);
        if (start == LibraryStart.WAIT) {
            return;
        }
        firstRunChecked = true;
        if (start == LibraryStart.IMPORT) {
            minecraft.execute(() -> {
                if (minecraft.screen == this) {
                    openImport(true);
                }
            });
        }
    }

    private void openImport(boolean firstRun) {
        minecraft.setScreen(new ImportScreen(this, minecraft.gameDirectory.toPath(), this::importFiles, firstRun));
    }

    @Override
    protected @Nullable GlassButton createUploadButton() {
        upload = new GlassButton(0, 0, 10, 16, Component.translatable("heavymatica.upload.button"), GlassButton.Kind.ACCENT, button -> chooseUploadTarget());
        upload.visible = false;
        return upload;
    }

    @Override
    protected boolean uploadOffered() {
        return cloudShown() && !availability().tooOld();
    }

    @Override
    protected void onSelectionChanged() {
        if (upload != null) {
            LibraryEntry entry = selected();
            boolean local = entry != null && !entry.cloud();
            upload.enable(local && account().signedIn(), Component.translatable("heavymatica.upload.sign_in_needed"));
        }
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        importFiles(files);
    }

    /** Imports each file and reports per file; duplicates are confirmed one by one. */
    void importFiles(List<Path> files) {
        List<Importer.Result> results = importer.importAll(files);
        List<String> rejected = new ArrayList<>();
        int imported = 0;
        for (Importer.Result result : results) {
            switch (result.outcome()) {
                case IMPORTED -> imported++;
                case REJECTED -> rejected.add(result.file().getFileName() + ": " + Component.translatable(
                        "heavymatica.import.reason." + result.reason().wireName()).getString());
                case DUPLICATE -> {
                }
            }
        }
        showMessage(rejected.isEmpty()
                ? Component.translatable("heavymatica.import.done", imported)
                : Component.translatable("heavymatica.import.done_with_rejections", imported, String.join(", ", rejected)), !rejected.isEmpty());
        results.stream().filter(result -> result.outcome() == Importer.Outcome.DUPLICATE).findFirst().ifPresent(this::confirmDuplicate);
        refreshEntries();
    }

    private void confirmDuplicate(Importer.Result duplicate) {
        LibraryEntry match = duplicate.match();
        Component where = match.cloud()
                ? Component.translatable("heavymatica.import.duplicate.cloud", match.name(), match.version())
                : Component.translatable("heavymatica.import.duplicate.local", match.name());
        minecraft.setScreen(new GlassConfirmScreen(this, Component.translatable("heavymatica.import.duplicate.title", duplicate.file().getFileName().toString()),
                where, Component.translatable("heavymatica.import.duplicate.anyway"), Component.translatable("heavymatica.import.duplicate.skip"), false,
                anyway -> {
                    if (anyway) {
                        importer.importFile(duplicate.file(), true);
                        refreshEntries();
                    }
                }));
    }

    private void chooseUploadTarget() {
        LibraryEntry entry = selected();
        if (entry == null || entry.cloud()) {
            return;
        }
        List<LibraryEntry> targets = library().entries().stream().filter(LibraryEntry::cloud).toList();
        minecraft.setScreen(new UploadTargetScreen(this, entry.name(), targets, target -> uploadSelected(entry, target)));
    }

    /** Uploads the local entry, as a new schematic when {@code target} is null. */
    void uploadSelected(LibraryEntry entry, @Nullable LibraryEntry target) {
        LocalLibrary.LocalEntry local = library().local().find(entry.sha256()).orElse(null);
        if (local == null) {
            return;
        }
        showMessage(Component.translatable("heavymatica.upload.uploading", entry.name()));
        uploader.upload(local, target == null ? null : target.id()).whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
            if (failure == null) {
                showMessage(Component.translatable("heavymatica.upload.done", entry.name(), result.getVersion().getNumber()));
                library().refresh();
                account().refreshAccount().exceptionally(error -> null);
            } else {
                CloudException error = CloudException.from(failure, account().client().json());
                boolean overQuota = error.code().filter(code -> code == ProblemCode.OVER_QUOTA).isPresent();
                showMessage(Component.translatable(overQuota ? "heavymatica.upload.over_quota" : "heavymatica.upload.failed", entry.name()), true);
            }
            refreshEntries();
        }));
    }

    /** Renders on the render thread; the PNG arrives once the GPU readback is done. */
    private static CompletableFuture<byte[]> renderThumbnail(Path file) {
        CompletableFuture<byte[]> png = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                ThumbnailRenderer.renderPng(SchematicReader.read(file)).whenComplete((bytes, failure) -> {
                    if (failure != null) {
                        png.completeExceptionally(failure);
                    } else {
                        png.complete(bytes);
                    }
                });
            } catch (RuntimeException e) {
                png.completeExceptionally(e);
            }
        });
        return png;
    }
}
