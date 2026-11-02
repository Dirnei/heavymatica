package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.CloudAvailability;
import io.github.dirnei.mc.heavymatica.account.SignInFailure;
import io.github.dirnei.mc.heavymatica.cloud.model.OrganizeRequest;
import io.github.dirnei.mc.heavymatica.cloud.model.SchematicDetails;
import io.github.dirnei.mc.heavymatica.cloud.model.Version;
import io.github.dirnei.mc.heavymatica.formats.SchematicFormat;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.formats.SchematicReader;
import io.github.dirnei.mc.heavymatica.library.Exporter;
import io.github.dirnei.mc.heavymatica.library.Library;
import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import io.github.dirnei.mc.heavymatica.library.LibraryItem;
import io.github.dirnei.mc.heavymatica.library.LocalLibrary;
import io.github.dirnei.mc.heavymatica.placement.PlacementSource;
import io.github.dirnei.mc.heavymatica.ui.glass.GearButton;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassConfirmScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassEditBox;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The schematic library: a header with search, import, account and settings; folder path, filter and tag chips over a
 * grid of folders and entries; and the selected entry's details with its actions beside the grid. Everything loads in
 * the background; the screen shows loading, offline and error states instead of waiting.
 */
public class LibraryScreen extends GlassScreen {

    /** Starts a placement of a loaded version; called on the game thread. */
    @FunctionalInterface
    public interface Placer {
        void place(PlacementSource source, String name, SchematicModel model);
    }

    /** Which entries the grid shows besides folders. */
    private enum Kind {
        ALL,
        CLOUD,
        LOCAL,
    }

    private final CloudAccount account;
    private final CloudAvailability availability;
    private final Library library;
    private final Placer placer;
    private final ThumbnailTextures thumbnails;
    private final EntryFacts facts;
    private final List<Runnable> subscriptions = new ArrayList<>();

    private LibraryLayout layout;
    private EditBox search;
    private ChipRow chips;
    private CardGrid grid;
    private DetailsPanel details;
    private @Nullable AccountChip accountChip;
    private @Nullable GlassButton refreshButton;
    private GlassButton allDetailsButton;
    private GlassButton placeButton;
    private GlassButton exportButton;
    private GlassButton renameButton;
    private GlassButton deleteButton;
    private GlassButton moveButton;
    private GlassButton tagsButton;
    private GlassButton deleteVersionButton;
    private @Nullable GlassButton uploadButton;
    private boolean exporting;
    private boolean placing;
    private String query = "";
    private String folder = "";
    private boolean all;
    private Kind kind = Kind.ALL;
    private @Nullable String tag;
    private @Nullable LibraryEntry selected;
    private @Nullable SchematicDetails selectedDetails;
    private int selectedVersion;
    private @Nullable Component detailsStatus;
    private @Nullable Component message;
    private boolean messageIsError;

    public LibraryScreen(CloudAccount account, CloudAvailability availability, Library library, Placer placer) {
        super(Component.translatable("heavymatica.library.title"));
        this.account = account;
        this.availability = availability;
        this.library = library;
        this.placer = placer;
        this.thumbnails = new ThumbnailTextures(library);
        this.facts = new EntryFacts(library, this::updateDetails);
    }

    /**
     * Nothing takes focus on open: the library opens on a key (its own or the wheel's), and the vanilla default would
     * focus the search box after keyboard input, typing that key into it.
     */
    @Override
    protected void setInitialFocus() {
    }

    @Override
    protected void init() {
        subscriptions.forEach(Runnable::run);
        subscriptions.clear();
        subscriptions.add(library.onChange(changed -> refreshEntries()));
        subscriptions.add(account.onChange(changed -> minecraft.execute(this::refreshAccountWidgets)));
        subscriptions.add(availability.onChange(changed -> minecraft.execute(this::availabilityChanged)));
        layout = LibraryLayout.of(width, height, cloudShown());

        Rect s = layout.search();
        search = new GlassEditBox(font, s.x(), s.y(), s.w(), s.h(), Component.translatable("heavymatica.library.search"), true);
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            grid.scrollToTop();
            refreshEntries();
        });
        addRenderableWidget(search);
        addExtraTopButtons(layout.importButton());
        Rect a = layout.account();
        accountChip = addRenderableWidget(new AccountChip(a.x(), a.y(), a.w(), a.h(), account,
                () -> library.cloudState() == Library.CloudState.OFFLINE, button -> accountPressed()));
        Rect g = layout.settings();
        addRenderableWidget(new GearButton(g.x(), g.y(), g.w(), Component.translatable("heavymatica.settings.open"),
                button -> minecraft.setScreen(new SettingsScreen(this))));

        Rect c = layout.chips();
        int refreshWidth = font.width(Component.translatable("heavymatica.library.refresh")) + 12;
        refreshButton = addRenderableWidget(new GlassButton(c.right() - refreshWidth, c.y(), refreshWidth, c.h(),
                Component.translatable("heavymatica.library.refresh"), GlassButton.Kind.QUIET, button -> {
                    availability.refresh();
                    refreshCloud();
                }));
        chips = addRenderableWidget(new ChipRow(c.x(), c.y(), c.w() - refreshWidth - 6, c.h()));
        Rect cards = layout.cards();
        grid = addRenderableWidget(new CardGrid(cards.x(), cards.y(), cards.w(), cards.h(), layout.columns(), layout.cardWidth(), thumbnails,
                this::available, this::select, this::openFolder));

        details = addRenderableWidget(new DetailsPanel(layout.details(), thumbnails, this::chooseVersion));
        allDetailsButton = addRenderableWidget(new GlassButton(0, 0, 10, 14, Component.translatable("heavymatica.library.all_details"),
                GlassButton.Kind.QUIET, button -> openDetailsPage()));
        placeButton = addRenderableWidget(new GlassButton(0, 0, 10, 20, Component.translatable("heavymatica.library.place"),
                GlassButton.Kind.PRIMARY, button -> place()).radius(7));
        exportButton = action("heavymatica.library.export", GlassButton.Kind.SECONDARY, this::export);
        renameButton = action("heavymatica.library.rename", GlassButton.Kind.SECONDARY, this::rename);
        deleteButton = action("heavymatica.library.delete", GlassButton.Kind.DANGER, this::deleteSchematic);
        moveButton = action("heavymatica.library.move", GlassButton.Kind.SECONDARY, this::move);
        tagsButton = action("heavymatica.library.tag", GlassButton.Kind.SECONDARY, this::editTags);
        deleteVersionButton = action("heavymatica.library.delete_version", GlassButton.Kind.DANGER, this::deleteVersion);
        uploadButton = createUploadButton();
        if (uploadButton != null) {
            addRenderableWidget(uploadButton);
        }

        refreshAccountWidgets();
        refreshEntries();
        availability.libraryOpened();
        if (library.cloudState() == Library.CloudState.NONE || library.cloudState() == Library.CloudState.OFFLINE) {
            refreshCloud();
        }
    }

    private GlassButton action(String key, GlassButton.Kind style, Runnable onPress) {
        return addRenderableWidget(new GlassButton(0, 0, 10, 18, Component.translatable(key), style, button -> onPress.run()));
    }

    /** The cloud's status arrived or changed: an outdated mod drops back to the stored listing. */
    private void availabilityChanged() {
        if (availability.tooOld() || !availability.mayContact()) {
            library.loadStored();
        }
        if (cloudShown() != (layout.account().w() > 0)) {
            rebuildWidgets();
            return;
        }
        refreshAccountWidgets();
    }

    /** Whether the cloud parts of the library show at all. */
    protected boolean cloudShown() {
        return availability.showCloud(account.hasSession());
    }

    /** Called when the selected entry changes. */
    protected void onSelectionChanged() {
    }

    /** Room for the import button. */
    protected void addExtraTopButtons(Rect importButton) {
    }

    /** The upload button of the details panel, or null when this library cannot upload. */
    protected @Nullable GlassButton createUploadButton() {
        return null;
    }

    /** Whether the details panel offers upload for a local entry. */
    protected boolean uploadOffered() {
        return false;
    }

    protected Library library() {
        return library;
    }

    protected CloudAccount account() {
        return account;
    }

    protected CloudAvailability availability() {
        return availability;
    }

    protected @Nullable LibraryEntry selected() {
        return selected;
    }

    protected void showMessage(@Nullable Component text) {
        showMessage(text, false);
    }

    protected void showMessage(@Nullable Component text, boolean error) {
        message = text;
        messageIsError = error;
    }

    private void refreshCloud() {
        message = null;
        library.refresh();
        if (account.signedIn() && availability.mayContact() && !availability.tooOld()) {
            account.refreshAccount().exceptionally(error -> null);
        }
    }

    protected void refreshEntries() {
        if (grid == null) {
            return;
        }
        boolean cloud = cloudShown();
        Kind shownKind = cloud ? kind : Kind.ALL;
        List<LibraryItem> items = library.view(folder, all, query, tag).stream()
                .filter(item -> !(item instanceof LibraryItem.Entry entry) || shownKind == Kind.ALL || entry.entry().cloud() == (shownKind == Kind.CLOUD))
                .toList();
        grid.setItems(items);
        grid.setMarks(cloud);
        chips.setChips(chipList(cloud));
        if (selected != null) {
            String id = selected.id();
            selected = library.entries().stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
            if (selected == null) {
                selectedDetails = null;
            }
        }
        grid.setSelected(selected == null ? null : selected.id());
        updateDetails();
        refreshAccountWidgets();
    }

    /** The folder path, the flat view of everything, the cloud and local filters and the tags. */
    private List<ChipRow.Chip> chipList(boolean cloud) {
        List<ChipRow.Chip> list = new ArrayList<>();
        list.add(ChipRow.Chip.path(Component.translatable("heavymatica.library.top").getString(), !all && folder.isEmpty(), () -> openFolder("")));
        if (!all && !folder.isEmpty()) {
            String[] parts = folder.split("/");
            for (int i = 0; i < parts.length; i++) {
                String path = String.join("/", Arrays.copyOf(parts, i + 1));
                list.add(ChipRow.Chip.arrow());
                list.add(ChipRow.Chip.path(parts[i], i == parts.length - 1, () -> openFolder(path)));
            }
        }
        list.add(ChipRow.Chip.divider());
        list.add(ChipRow.Chip.filter(Component.translatable("heavymatica.library.all").getString(), all, () -> {
            all = !all;
            grid.scrollToTop();
            refreshEntries();
        }));
        if (cloud) {
            list.add(ChipRow.Chip.filter(Component.translatable("heavymatica.library.filter.cloud").getString(), kind == Kind.CLOUD, () -> filterKind(Kind.CLOUD)));
            list.add(ChipRow.Chip.filter(Component.translatable("heavymatica.library.filter.local").getString(), kind == Kind.LOCAL, () -> filterKind(Kind.LOCAL)));
        }
        List<String> tags = library.tags();
        if (!tags.isEmpty()) {
            list.add(ChipRow.Chip.divider());
            for (String candidate : tags) {
                list.add(ChipRow.Chip.filter("#" + candidate, candidate.equals(tag), () -> {
                    tag = candidate.equals(tag) ? null : candidate;
                    grid.scrollToTop();
                    refreshEntries();
                }));
            }
        }
        return list;
    }

    private void filterKind(Kind chosen) {
        kind = kind == chosen ? Kind.ALL : chosen;
        grid.scrollToTop();
        refreshEntries();
    }

    private void accountPressed() {
        if (account.signedIn()) {
            minecraft.setScreen(new AccountScreen(this, account));
        } else if (account.status() == CloudAccount.Status.CHOOSING) {
            minecraft.setScreen(new AccountChoiceScreen(this, account, this::refreshCloud));
        } else {
            signIn();
        }
    }

    private void refreshAccountWidgets() {
        if (accountChip == null) {
            return;
        }
        boolean shown = cloudShown();
        accountChip.update();
        accountChip.visible = shown && !availability.tooOld() && (account.hasSession() || availability.offerSignIn(false));
        if (refreshButton != null) {
            refreshButton.visible = shown && availability.mayContact();
        }
        layoutDetailButtons();
        onSelectionChanged();
    }

    /** Puts the panel's buttons into its slots and enables them for the selected entry. */
    private void layoutDetailButtons() {
        if (placeButton == null) {
            return;
        }
        boolean has = selected != null;
        boolean cloud = has && selected.cloud();
        boolean local = has && !cloud;
        List<GlassButton> actions = cloud ? List.of(exportButton, renameButton, moveButton, tagsButton, deleteVersionButton, deleteButton)
                : List.of(exportButton, renameButton, deleteButton);
        int widest = actions.stream().mapToInt(button -> font.width(button.getMessage())).max().orElse(0);
        details.show(selected, selectedDetails, selectedVersion, detailsStatus, factsOfSelection(), local && uploadOffered(), has ? actions.size() : 0,
                widest);
        DetailsPanel.Slots slots = details.slots();
        place(allDetailsButton, slots.allDetails(), has);
        place(placeButton, slots.place(), has);
        for (GlassButton button : List.of(exportButton, renameButton, deleteButton, moveButton, tagsButton, deleteVersionButton)) {
            button.visible = false;
        }
        for (int i = 0; i < Math.min(actions.size(), slots.actions().size()); i++) {
            place(actions.get(i), slots.actions().get(i), has);
        }
        if (uploadButton != null) {
            uploadButton.visible = slots.upload() != null;
            if (slots.upload() != null) {
                place(uploadButton, slots.upload(), true);
            }
        }
        boolean canOrganize = account.signedIn() && library.cloudState() != Library.CloudState.OFFLINE && cloud;
        Component signInNeeded = Component.translatable("heavymatica.library.sign_in_needed");
        for (GlassButton button : List.of(moveButton, tagsButton, deleteVersionButton)) {
            button.enable(canOrganize, signInNeeded);
        }
        // Local entries can always be renamed and deleted; they live on this machine only.
        renameButton.enable(local || canOrganize, signInNeeded);
        deleteButton.enable(local || canOrganize, signInNeeded);
        updatePlaceButton();
    }

    private static void place(GlassButton button, Rect rect, boolean visible) {
        button.setX(rect.x());
        button.setY(rect.y());
        button.setWidth(rect.w());
        button.setHeight(rect.h());
        button.visible = visible;
    }

    private EntryFacts.@Nullable Facts factsOfSelection() {
        String sha256 = chosenSha256();
        if (selected == null || sha256 == null) {
            return null;
        }
        Version version = details == null ? null : details.shownVersion();
        if (version == null && selectedDetails != null) {
            version = selectedDetails.getVersions().stream().filter(candidate -> candidate.getNumber() == selectedVersion).findFirst().orElse(null);
        }
        return facts.get(selected, sha256, version).orElse(null);
    }

    private boolean available(LibraryEntry entry) {
        // Only an offline library makes uncached cloud entries unavailable; while loading they stay usable.
        return !entry.cloud() || library.cloudState() != Library.CloudState.OFFLINE || library.cached(entry.sha256());
    }

    private void select(LibraryEntry entry) {
        selected = entry;
        selectedDetails = null;
        selectedVersion = entry.version();
        grid.setSelected(entry.id());
        if (entry.cloud() && account.signedIn() && library.cloudState() == Library.CloudState.CURRENT) {
            detailsStatus = null;
            // Keep the chosen schematic usable offline: its file goes into the cache in the background.
            library.file(entry.id(), entry.version(), entry.sha256()).exceptionally(error -> null);
            library.details(entry.id()).whenComplete((loaded, failure) -> minecraft.execute(() -> {
                if (selected != null && selected.id().equals(entry.id())) {
                    selectedDetails = loaded;
                    detailsStatus = failure == null ? null : Component.translatable("heavymatica.library.details.failed");
                    updateDetails();
                }
            }));
        } else if (entry.cloud() && !available(entry)) {
            detailsStatus = Component.translatable("heavymatica.library.needs_connection");
        } else {
            detailsStatus = null;
        }
        updateDetails();
        onSelectionChanged();
    }

    /** Development aid for {@link DevScreens}: the centre of a chip with this label. */
    double[] sidebarPointForDevelopment(String label) {
        return chips.pointOf(label);
    }

    /** Development aid for {@link DevScreens}: the centre of the card at this index. */
    double[] cardPointForDevelopment(int index) {
        grid.reveal(index);
        return grid.pointOf(index);
    }

    /** Development aid for {@link DevScreens}: the centre of the version row at this index. */
    double[] versionPointForDevelopment(int index) {
        return details.versionPoint(index);
    }

    /** Development aid for {@link DevScreens}: the search box, to focus it. */
    EditBox searchForDevelopment() {
        return search;
    }

    /** Development aid for {@link DevScreens}: selects the first schematic card. */
    void selectFirstForDevelopment() {
        grid.items().stream()
                .filter(LibraryItem.Entry.class::isInstance)
                .findFirst()
                .ifPresent(item -> select(((LibraryItem.Entry) item).entry()));
    }

    /** Development aid for {@link DevScreens}: opens a folder, {@code ""} for the top level. */
    void openFolderForDevelopment(String path) {
        openFolder(path);
    }

    /** Development aid for {@link DevScreens}: opens the details page of the selection. */
    void openDetailsForDevelopment() {
        openDetailsPage();
    }

    /** Opens a folder of the explorer, which also leaves the flat view of everything. */
    private void openFolder(String path) {
        folder = path;
        all = false;
        grid.scrollToTop();
        refreshEntries();
    }

    private void chooseVersion(int version) {
        selectedVersion = version;
        updateDetails();
    }

    private void updateDetails() {
        if (details == null) {
            return;
        }
        layoutDetailButtons();
    }

    /** The chosen version's content hash; for an older cloud version only once its details are loaded. */
    private @Nullable String chosenSha256() {
        if (selected == null) {
            return null;
        }
        if (!selected.cloud() || selectedVersion == selected.version()) {
            return selected.sha256();
        }
        if (selectedDetails == null) {
            return null;
        }
        return selectedDetails.getVersions().stream().filter(version -> version.getNumber() == selectedVersion).map(Version::getSha256)
                .findFirst().orElse(null);
    }

    private void updatePlaceButton() {
        String sha256 = chosenSha256();
        boolean offlineUncached = selected != null && selected.cloud() && sha256 != null && library.cloudState() == Library.CloudState.OFFLINE
                && !library.cached(sha256);
        Component needsConnection = Component.translatable("heavymatica.library.needs_connection");
        placeButton.enable(sha256 != null && !offlineUncached && !placing, offlineUncached ? needsConnection : null);
        exportButton.enable(sha256 != null && !offlineUncached && !exporting, offlineUncached ? needsConnection : null);
    }

    private void openDetailsPage() {
        LibraryEntry entry = selected;
        String sha256 = chosenSha256();
        if (entry == null || sha256 == null) {
            return;
        }
        Version version = selectedDetails == null ? null
                : selectedDetails.getVersions().stream().filter(candidate -> candidate.getNumber() == selectedVersion).findFirst().orElse(null);
        minecraft.setScreen(new EntryDetailsScreen(this, entry, selectedVersion, sha256, version, facts, thumbnails, new EntryDetailsScreen.Actions(
                this::place, this::export, this::rename, this::deleteSchematic, placeButton::isActive, exportButton::isActive,
                renameButton::isActive, deleteButton::isActive)));
    }

    /** The chosen version's file: from the local library, or from the cache, downloading it first. */
    private CompletableFuture<Path> chosenFile(LibraryEntry entry, int version, String sha256) {
        if (entry.cloud()) {
            return library.file(entry.id(), version, sha256);
        }
        LocalLibrary locals = library.local();
        return locals.find(sha256).map(locals::file).map(CompletableFuture::completedFuture)
                .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("The local entry is gone")));
    }

    /** The extension of the chosen version's file: the local entry's own, or the one its format's tools use. */
    private String chosenExtension(LibraryEntry entry, String sha256) {
        if (!entry.cloud()) {
            return library.local().find(sha256).map(LocalLibrary.LocalEntry::extension).orElse("bin");
        }
        String format = selectedDetails == null ? entry.format() : selectedDetails.getVersions().stream()
                .filter(candidate -> candidate.getNumber() == selectedVersion).map(candidate -> candidate.getFormat().getValue())
                .findFirst().orElse(entry.format());
        return SchematicFormat.ofWireName(format).map(SchematicFormat::extension).orElse("bin");
    }

    /** Writes the chosen version's file into {@code schematics/}, asking first when a file of that name exists. */
    private void export() {
        LibraryEntry entry = selected;
        String sha256 = chosenSha256();
        if (entry == null || sha256 == null || exporting) {
            return;
        }
        String extension = chosenExtension(entry, sha256);
        Exporter exporter = Exporter.schematicsFolder(minecraft.gameDirectory.toPath());
        exporting = true;
        updatePlaceButton();
        showMessage(Component.translatable("heavymatica.library.loading"));
        chosenFile(entry, selectedVersion, sha256).whenComplete((file, failure) -> minecraft.execute(() -> {
            if (failure != null) {
                exporting = false;
                Heavymatica.LOGGER.warn("Exporting {} failed", entry.name(), failure);
                showMessage(Component.translatable("heavymatica.library.action_failed"), true);
                updatePlaceButton();
                return;
            }
            if (!exporter.exists(entry.name(), extension)) {
                writeExport(exporter, file, entry.name(), extension, Exporter.Conflict.OVERWRITE);
                return;
            }
            showMessage(null);
            minecraft.setScreen(new ExportConflictScreen(this, exporter.target(entry.name(), extension).getFileName().toString(),
                    conflict -> writeExport(exporter, file, entry.name(), extension, conflict), () -> {
                        exporting = false;
                        updatePlaceButton();
                    }));
        }));
    }

    private void writeExport(Exporter exporter, Path file, String name, String extension, Exporter.Conflict conflict) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return exporter.export(file, name, extension, conflict);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, Util.ioPool()).whenComplete((written, failure) -> minecraft.execute(() -> {
            exporting = false;
            if (failure != null) {
                Heavymatica.LOGGER.warn("Writing the export of {} failed", name, failure);
                showMessage(Component.translatable("heavymatica.library.export.failed"), true);
            } else {
                showMessage(Component.translatable("heavymatica.library.export.done", "schematics/" + written.getFileName()));
            }
            updatePlaceButton();
        }));
    }

    /** Loads the chosen version (cache or download), reads it off the game thread, then closes and starts placing. */
    private void place() {
        LibraryEntry entry = selected;
        String sha256 = chosenSha256();
        if (entry == null || sha256 == null || placing) {
            return;
        }
        int version = selectedVersion;
        CompletableFuture<Path> file = chosenFile(entry, version, sha256);
        PlacementSource source = entry.cloud() ? new PlacementSource.Cloud(entry.id(), version, sha256) : new PlacementSource.Local(sha256);
        placing = true;
        updatePlaceButton();
        showMessage(Component.translatable("heavymatica.library.loading"));
        file.thenApplyAsync(SchematicReader::read, Util.backgroundExecutor()).whenComplete((model, failure) -> minecraft.execute(() -> {
            placing = false;
            if (failure != null) {
                Heavymatica.LOGGER.warn("Placing {} version {} failed", entry.name(), version, failure);
                showMessage(Component.translatable("heavymatica.library.action_failed"), true);
                updatePlaceButton();
                return;
            }
            minecraft.setScreen(null);
            placer.place(source, entry.name(), model);
        }));
    }

    private void signIn() {
        message = null;
        account.signIn().whenComplete((ignored, failure) -> minecraft.execute(() -> {
            if (failure != null) {
                Component reason = Component.translatable(failureOf(failure).translationKey());
                if (minecraft.screen != this) {
                    showMessage(reason, true);
                    return;
                }
                minecraft.setScreen(new GlassConfirmScreen(this, Component.translatable("heavymatica.account.failed.title"), reason,
                        Component.translatable("heavymatica.account.retry"), Component.translatable("gui.done"), false, retry -> {
                            if (retry) {
                                signIn();
                            }
                        }));
            } else if (account.status() == CloudAccount.Status.CHOOSING) {
                minecraft.setScreen(new AccountChoiceScreen(this, account, this::refreshCloud));
            } else {
                refreshCloud();
            }
        }));
    }

    private static SignInFailure failureOf(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        return cause instanceof SignInFailure.SignInException signIn ? signIn.failure() : SignInFailure.UNEXPECTED;
    }

    private void rename() {
        LibraryEntry entry = selected;
        if (entry == null) {
            return;
        }
        minecraft.setScreen(new TextPromptScreen(this, Component.translatable("heavymatica.library.rename.title"),
                Component.translatable("heavymatica.library.rename.hint"), entry.name(), name -> {
                    String trimmed = name.trim();
                    if (trimmed.isEmpty()) {
                        return;
                    }
                    if (entry.cloud()) {
                        organize(entry, new OrganizeRequest().name(trimmed));
                    } else {
                        library.renameLocal(entry.sha256(), trimmed);
                    }
                }));
    }

    private void move() {
        LibraryEntry entry = selected;
        if (entry == null) {
            return;
        }
        minecraft.setScreen(new TextPromptScreen(this, Component.translatable("heavymatica.library.move.title"),
                Component.translatable("heavymatica.library.move.hint"), entry.folder() == null ? "" : entry.folder(),
                folderPath -> organize(entry, new OrganizeRequest().folder(folderPath.trim()))));
    }

    private void editTags() {
        LibraryEntry entry = selected;
        if (entry == null) {
            return;
        }
        minecraft.setScreen(new TextPromptScreen(this, Component.translatable("heavymatica.library.tag.title"),
                Component.translatable("heavymatica.library.tag.hint"), String.join(", ", entry.tags()),
                text -> organize(entry, new OrganizeRequest().tags(Arrays.stream(text.split(","))
                        .map(value -> value.trim().toLowerCase(Locale.ROOT)).filter(value -> !value.isEmpty()).distinct().toList()))));
    }

    private void organize(LibraryEntry entry, OrganizeRequest request) {
        library.organize(entry.id(), request).whenComplete((ignored, failure) -> minecraft.execute(() ->
                showMessage(failure == null ? null : Component.translatable("heavymatica.library.action_failed"), true)));
    }

    private void deleteSchematic() {
        LibraryEntry entry = selected;
        if (entry == null) {
            return;
        }
        if (!entry.cloud()) {
            minecraft.setScreen(new GlassConfirmScreen(this, Component.translatable("heavymatica.library.delete_local.title"),
                    Component.translatable("heavymatica.library.delete_local.message", entry.name()), Component.translatable("heavymatica.library.delete"),
                    Component.translatable("gui.cancel"), true, confirmed -> {
                        if (confirmed) {
                            library.deleteLocal(entry.sha256());
                        }
                    }));
            return;
        }
        minecraft.setScreen(new GlassConfirmScreen(this, Component.translatable("heavymatica.library.delete.title"),
                Component.translatable("heavymatica.library.delete.message", entry.name()), Component.translatable("heavymatica.library.delete"),
                Component.translatable("gui.cancel"), true, confirmed -> {
                    if (confirmed) {
                        library.deleteSchematic(entry.id()).whenComplete((ignored, failure) -> minecraft.execute(() -> {
                            showMessage(failure == null ? null : Component.translatable("heavymatica.library.action_failed"), true);
                            account.refreshAccount().exceptionally(error -> null);
                        }));
                    }
                }));
    }

    private void deleteVersion() {
        LibraryEntry entry = selected;
        if (entry == null || selectedDetails == null) {
            return;
        }
        int version = details.version();
        minecraft.setScreen(new GlassConfirmScreen(this, Component.translatable("heavymatica.library.delete_version.title"),
                Component.translatable("heavymatica.library.delete_version.message", version, entry.name()),
                Component.translatable("heavymatica.library.delete"), Component.translatable("gui.cancel"), true, confirmed -> {
                    if (confirmed) {
                        library.deleteVersion(entry.id(), version).whenComplete((ignored, failure) -> minecraft.execute(() -> {
                            showMessage(failure == null ? null : Component.translatable("heavymatica.library.delete_version.failed"), true);
                            if (failure == null) {
                                select(entry);
                            }
                        }));
                    }
                }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        Rect heading = layout.title();
        Glass.text(graphics, font, title.getString(), heading.x(), heading.y() + 2, GlassStyle.TEXT, heading.w());
        Glass.text(graphics, font, subtitle(), heading.x(), heading.y() + 13, GlassStyle.HINT, heading.w());
        Rect panel = layout.gridPanel();
        Glass.panel(graphics, panel.x(), panel.y(), panel.w(), panel.h());
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        drawStatus(graphics);
        if (grid.items().isEmpty() && library.cloudState() != Library.CloudState.LOADING) {
            Rect cards = layout.cards();
            Component empty = Component.translatable(library.entries().isEmpty() ? "heavymatica.library.empty" : "heavymatica.library.no_match");
            Glass.paragraph(graphics, font, empty, cards.x() + 10, cards.y() + 20, cards.w() - 20, GlassStyle.HINT);
        }
    }

    /** "14 schematics · 3 local", or how many are shown while filtering. */
    private String subtitle() {
        long local = library.entries().stream().filter(entry -> !entry.cloud()).count();
        int total = library.entries().size();
        if (!cloudShown() || local == total) {
            return Component.translatable("heavymatica.library.subtitle.local", total).getString();
        }
        return Component.translatable("heavymatica.library.subtitle.mixed", total, total - local, local).getString();
    }

    /** The cloud listing's state, too-old hint, or the last message. */
    private void drawStatus(GuiGraphicsExtractor graphics) {
        Rect status = layout.status();
        Component state = availability.tooOld() ? Component.translatable("heavymatica.cloud.too_old") : switch (library.cloudState()) {
            case LOADING -> Component.translatable("heavymatica.library.loading");
            case OFFLINE -> Component.translatable("heavymatica.library.offline");
            default -> null;
        };
        if (message != null) {
            Glass.text(graphics, font, message.getString(), status.x(), status.y(), messageIsError ? GlassStyle.DANGER_TEXT : GlassStyle.CONFIRM, status.w());
        } else if (state != null) {
            Glass.text(graphics, font, state.getString(), status.x(), status.y(), GlassStyle.WARNING, status.w());
        }
    }

    @Override
    public void removed() {
        subscriptions.forEach(Runnable::run);
        subscriptions.clear();
        thumbnails.close();
        super.removed();
    }
}
