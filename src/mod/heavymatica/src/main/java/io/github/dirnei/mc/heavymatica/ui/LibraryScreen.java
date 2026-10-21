package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.account.CloudAccount;
import io.github.dirnei.mc.heavymatica.account.SignInFailure;
import io.github.dirnei.mc.heavymatica.cloud.model.Account;
import io.github.dirnei.mc.heavymatica.cloud.model.OrganizeRequest;
import io.github.dirnei.mc.heavymatica.cloud.model.SchematicDetails;
import io.github.dirnei.mc.heavymatica.cloud.model.Version;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.formats.SchematicReader;
import io.github.dirnei.mc.heavymatica.library.Library;
import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import io.github.dirnei.mc.heavymatica.library.LocalLibrary;
import io.github.dirnei.mc.heavymatica.placement.PlacementSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The schematic library: search, folder and tag filters, a card grid of cloud and local entries, details of the
 * selected entry, and organizing actions. Everything loads in the background; the screen shows loading, offline and
 * error states instead of waiting.
 */
public class LibraryScreen extends Screen {

    /** Starts a placement of a loaded version; called on the game thread. */
    @FunctionalInterface
    public interface Placer {
        void place(PlacementSource source, String name, SchematicModel model);
    }

    private static final int MARGIN = 8;
    private static final int SIDEBAR = 104;
    private static final int ACTION_STEP = 72;

    private final CloudAccount account;
    private final Library library;
    private final Placer placer;
    private final ThumbnailTextures thumbnails;
    private final List<Button> cloudActions = new ArrayList<>();
    private @Nullable Button renameButton;
    private @Nullable Button deleteButton;
    private @Nullable Button placeButton;
    private boolean placing;
    private final List<Runnable> subscriptions = new ArrayList<>();

    private EditBox search;
    private FilterSidebar sidebar;
    private CardGrid grid;
    private DetailsPanel details;
    private Button signInOut;
    private @Nullable String folder;
    private @Nullable String tag;
    private @Nullable LibraryEntry selected;
    private @Nullable SchematicDetails selectedDetails;
    private int selectedVersion;
    private @Nullable Component detailsStatus;
    private @Nullable Component message;
    private boolean messageIsError;

    public LibraryScreen(CloudAccount account, Library library, Placer placer) {
        super(Component.translatable("heavymatica.library.title"));
        this.account = account;
        this.library = library;
        this.placer = placer;
        this.thumbnails = new ThumbnailTextures(library);
    }

    @Override
    protected void init() {
        subscriptions.forEach(Runnable::run);
        subscriptions.clear();
        subscriptions.add(library.onChange(changed -> refreshEntries()));
        subscriptions.add(account.onChange(changed -> minecraft.execute(this::refreshAccountWidgets)));
        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, MARGIN, MARGIN, 150, 18, Component.translatable("heavymatica.library.search"));
        search.setHint(Component.translatable("heavymatica.library.search"));
        search.setValue(query);
        search.setResponder(text -> refreshEntries());
        addRenderableWidget(search);

        addRenderableWidget(Button.builder(Component.translatable("heavymatica.library.refresh"), button -> refreshCloud())
                .bounds(MARGIN + 156, MARGIN - 1, 60, 20).build());
        signInOut = addRenderableWidget(Button.builder(Component.empty(), button -> toggleSignIn())
                .bounds(MARGIN + 220, MARGIN - 1, 70, 20).build());
        addExtraTopButtons(MARGIN + 294);

        int top = MARGIN + 26;
        int contentWidth = width - SIDEBAR - MARGIN * 3;
        int gridHeight = Math.max(CardGrid.CARD_HEIGHT + 4, (int) ((height - top - MARGIN) * 0.58));
        sidebar = addRenderableWidget(new FilterSidebar(MARGIN, top, SIDEBAR, height - top - MARGIN, (newFolder, newTag) -> {
            folder = newFolder;
            tag = newTag;
            refreshEntries();
        }));
        grid = addRenderableWidget(new CardGrid(SIDEBAR + MARGIN * 2, top, contentWidth, gridHeight, thumbnails, this::available, this::select));
        int detailsTop = top + gridHeight + 6;
        int buttonRow = height - MARGIN - 20;
        details = addRenderableOnly(new DetailsPanel(SIDEBAR + MARGIN * 2, detailsTop, contentWidth, buttonRow - detailsTop - 4, this::chooseVersion));
        // Place sits on the details panel: drawn after it, and asked for clicks before it.
        placeButton = addRenderableWidget(Button.builder(Component.translatable("heavymatica.library.place"), button -> place())
                .bounds(SIDEBAR + MARGIN * 2 + contentWidth - DetailsPanel.PLACE_WIDTH - 2, detailsTop + 2, DetailsPanel.PLACE_WIDTH, 20).build());
        addWidget(details);

        cloudActions.clear();
        int x = SIDEBAR + MARGIN * 2;
        renameButton = action(x, buttonRow, "heavymatica.library.rename", this::rename);
        cloudActions.add(action(x + ACTION_STEP, buttonRow, "heavymatica.library.move", this::move));
        cloudActions.add(action(x + ACTION_STEP * 2, buttonRow, "heavymatica.library.tag", this::editTags));
        cloudActions.add(action(x + ACTION_STEP * 3, buttonRow, "heavymatica.library.delete_version", this::deleteVersion));
        deleteButton = action(x + ACTION_STEP * 4, buttonRow, "heavymatica.library.delete", this::deleteSchematic);
        // Local entries show their own actions in the slot of Move (see refreshAccountWidgets), so the row always fits.
        addExtraActions(x + ACTION_STEP, buttonRow);

        refreshAccountWidgets();
        refreshEntries();
        if (library.cloudState() == Library.CloudState.NONE || library.cloudState() == Library.CloudState.OFFLINE) {
            refreshCloud();
        }
    }

    /** Called when the selected entry changes. */
    protected void onSelectionChanged() {
    }

    /** Room for more top-bar buttons (import). */
    protected void addExtraTopButtons(int x) {
    }

    /** Room for more entry actions (upload). */
    protected void addExtraActions(int x, int y) {
    }

    protected Library library() {
        return library;
    }

    protected CloudAccount account() {
        return account;
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

    private Button action(int x, int y, String key, Runnable onPress) {
        return addRenderableWidget(Button.builder(Component.translatable(key), button -> onPress.run()).bounds(x, y, ACTION_STEP - 4, 20).build());
    }

    private void refreshCloud() {
        message = null;
        library.refresh();
        if (account.signedIn()) {
            account.refreshAccount().exceptionally(error -> null);
        }
    }

    protected void refreshEntries() {
        if (grid == null) {
            return;
        }
        grid.setEntries(library.filtered(search.getValue(), folder, tag));
        sidebar.setOptions(library.folders(), library.tags());
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

    private void refreshAccountWidgets() {
        if (signInOut == null) {
            return;
        }
        signInOut.setMessage(Component.translatable(switch (account.status()) {
            case SIGNED_IN -> "heavymatica.account.sign_out";
            case SIGNING_IN -> "heavymatica.account.signing_in";
            default -> "heavymatica.account.sign_in";
        }));
        signInOut.active = account.status() != CloudAccount.Status.SIGNING_IN;
        boolean local = selected != null && !selected.cloud();
        boolean canOrganize = account.signedIn() && library.cloudState() != Library.CloudState.OFFLINE && selected != null && selected.cloud();
        net.minecraft.client.gui.components.Tooltip signInNeeded = account.signedIn() ? null
                : net.minecraft.client.gui.components.Tooltip.create(Component.translatable("heavymatica.library.sign_in_needed"));
        cloudActions.forEach(button -> {
            button.visible = !local;
            button.active = canOrganize;
            button.setTooltip(signInNeeded);
        });
        // Local entries can always be renamed and deleted; they live on this machine only.
        for (Button button : new Button[] {renameButton, deleteButton}) {
            if (button != null) {
                button.active = local || canOrganize;
                button.setTooltip(local ? null : signInNeeded);
            }
        }
        onSelectionChanged();
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
        refreshAccountWidgets();
        onSelectionChanged();
        if (entry.cloud() && account.signedIn() && library.cloudState() == Library.CloudState.CURRENT) {
            detailsStatus = Component.translatable("heavymatica.library.loading");
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
            detailsStatus = entry.cloud() ? null : Component.translatable("heavymatica.library.local_entry");
        }
        updateDetails();
    }

    /** Development aid for {@link DevScreens}: the centre of a sidebar row with this label. */
    double[] sidebarPointForDevelopment(String label) {
        return sidebar.pointOf(label);
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

    /** Development aid for {@link DevScreens}: selects the first card. */
    void selectFirstForDevelopment() {
        List<LibraryEntry> entries = library.filtered(search.getValue(), folder, tag);
        if (!entries.isEmpty()) {
            select(entries.getFirst());
        }
    }

    private void chooseVersion(int version) {
        selectedVersion = version;
        updateDetails();
    }

    private void updateDetails() {
        details.show(selected, selectedDetails, selectedVersion, detailsStatus);
        updatePlaceButton();
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
        if (placeButton == null) {
            return;
        }
        String sha256 = chosenSha256();
        placeButton.visible = selected != null;
        boolean offlineUncached = selected != null && selected.cloud() && sha256 != null && library.cloudState() == Library.CloudState.OFFLINE
                && !library.cached(sha256);
        placeButton.active = sha256 != null && !offlineUncached && !placing;
        placeButton.setTooltip(offlineUncached ? Tooltip.create(Component.translatable("heavymatica.library.needs_connection")) : null);
    }

    /** Loads the chosen version (cache or download), reads it off the game thread, then closes and starts placing. */
    private void place() {
        LibraryEntry entry = selected;
        String sha256 = chosenSha256();
        if (entry == null || sha256 == null || placing) {
            return;
        }
        int version = selectedVersion;
        CompletableFuture<Path> file;
        PlacementSource source;
        if (entry.cloud()) {
            file = library.file(entry.id(), version, sha256);
            source = new PlacementSource.Cloud(entry.id(), version, sha256);
        } else {
            LocalLibrary locals = library.local();
            file = locals.find(sha256).map(locals::file).map(CompletableFuture::completedFuture)
                    .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("The local entry is gone")));
            source = new PlacementSource.Local(sha256);
        }
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

    private void toggleSignIn() {
        if (account.signedIn()) {
            account.signOut();
            message = Component.translatable("heavymatica.account.signed_out");
            return;
        }
        message = null;
        account.signIn().whenComplete((ignored, failure) -> minecraft.execute(() -> {
            if (failure != null) {
                showMessage(Component.translatable(failureOf(failure).translationKey()), true);
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
            minecraft.setScreen(new ConfirmScreen(confirmed -> {
                minecraft.setScreen(this);
                if (confirmed) {
                    library.deleteLocal(entry.sha256());
                }
            }, Component.translatable("heavymatica.library.delete_local.title"), Component.translatable("heavymatica.library.delete_local.message", entry.name())));
            return;
        }
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) {
                library.deleteSchematic(entry.id()).whenComplete((ignored, failure) -> minecraft.execute(() -> {
                    showMessage(failure == null ? null : Component.translatable("heavymatica.library.action_failed"), true);
                    account.refreshAccount().exceptionally(error -> null);
                }));
            }
        }, Component.translatable("heavymatica.library.delete.title"), Component.translatable("heavymatica.library.delete.message", entry.name())));
    }

    private void deleteVersion() {
        LibraryEntry entry = selected;
        if (entry == null || selectedDetails == null) {
            return;
        }
        int version = details.version();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) {
                library.deleteVersion(entry.id(), version).whenComplete((ignored, failure) -> minecraft.execute(() -> {
                    showMessage(failure == null ? null : Component.translatable("heavymatica.library.delete_version.failed"), true);
                    if (failure == null) {
                        select(entry);
                    }
                }));
            }
        }, Component.translatable("heavymatica.library.delete_version.title"),
                Component.translatable("heavymatica.library.delete_version.message", version, entry.name())));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        int right = width - MARGIN;
        Optional<Account> current = account.account();
        String name = account.name().orElse("");
        int dot = switch (account.status()) {
            case SIGNED_IN -> library.cloudState() == Library.CloudState.OFFLINE ? 0xFFE0C040 : 0xFF40D060;
            case SIGNING_IN -> 0xFF60A0FF;
            default -> 0xFF808080;
        };
        graphics.fill(right - 8, MARGIN + 5, right, MARGIN + 13, dot);
        String status = switch (account.status()) {
            case SIGNED_IN -> current.map(info -> name + "  " + mebibytes(info.getUsedBytes()) + "/" + mebibytes(info.getQuotaBytes()) + " MiB").orElse(name);
            case EXPIRED -> Component.translatable("heavymatica.account.expired").getString();
            case SIGNING_IN -> Component.translatable("heavymatica.account.signing_in").getString();
            case SIGNED_OUT -> Component.translatable("heavymatica.account.signed_out").getString();
        };
        graphics.text(font, status, right - 12 - font.width(status), MARGIN + 5, 0xFFFFFFFF, false);

        Component state = switch (library.cloudState()) {
            case LOADING -> Component.translatable("heavymatica.library.loading");
            case OFFLINE -> Component.translatable("heavymatica.library.offline");
            default -> null;
        };
        int infoY = MARGIN + 26 - 10;
        if (state != null) {
            graphics.text(font, state, SIDEBAR + MARGIN * 2, infoY, 0xFFE0C040, false);
        }
        if (message != null) {
            graphics.text(font, message, right - font.width(message), infoY, messageIsError ? 0xFFFF8080 : 0xFFA0E0A0, false);
        }
        if (grid != null && library.entries().isEmpty() && library.cloudState() != Library.CloudState.LOADING) {
            graphics.centeredText(font, Component.translatable("heavymatica.library.empty"), grid.getX() + grid.getWidth() / 2, grid.getY() + 30, 0xFF909090);
        }
    }

    private static String mebibytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / 1024.0 / 1024.0);
    }

    @Override
    public void removed() {
        subscriptions.forEach(Runnable::run);
        subscriptions.clear();
        thumbnails.close();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
