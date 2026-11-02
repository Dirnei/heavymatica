package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dirnei.mc.heavymatica.paste.PasteMode;
import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Command;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Label;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Mode;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Opacity;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Outcome;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Page;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Segment;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Selection;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.State;
import io.github.dirnei.mc.heavymatica.targeting.PointerMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class RadialMenuTest {

    private static final Selection FOLLOWING = new Selection(State.FOLLOWING, "Castle Gate", Layers.Mode.OFF, false);
    private static final Selection ANCHORED = new Selection(State.ANCHORED, "Castle Gate", Layers.Mode.OFF, false);
    private static final Selection LOCKED = new Selection(State.LOCKED, "Castle Gate", Layers.Mode.FROM_TO, true);

    private static final Opacity OPACITY = new Opacity(List.of(0.2, 0.4, 0.6, 0.8, 1.0), 0.4F);

    private static RadialMenu menu(Mode mode, Selection selection) {
        return new RadialMenu(mode, selection, OPACITY);
    }

    private static List<Entry> entries(RadialMenu menu) {
        return menu.segments().stream().map(Segment::entry).toList();
    }

    private static int index(RadialMenu menu, Entry entry) {
        return entries(menu).indexOf(entry);
    }

    private static Outcome run(Command command) {
        return new Outcome.Run(command);
    }

    @Test
    void bringBackComesFirstInEveryStateAfterAFinish() {
        assertEquals(List.of(Entry.BRING_BACK, Entry.MATERIALS, Entry.OPACITY, Entry.LIBRARY, Entry.START_CAPTURE),
                entries(new RadialMenu(Mode.HOLD, Selection.NONE, OPACITY, true)));
        assertEquals(List.of(Entry.BRING_BACK, Entry.UNLOCK, Entry.OPTIONS, Entry.LAYERS, Entry.MATERIALS, Entry.OPACITY, Entry.LIBRARY,
                        Entry.START_CAPTURE),
                entries(new RadialMenu(Mode.HOLD, LOCKED, OPACITY, true)));
    }

    @Test
    void pickingBringBackRunsIt() {
        RadialMenu menu = new RadialMenu(Mode.TOGGLE, Selection.NONE, OPACITY, true);
        assertEquals(run(Command.BRING_BACK), menu.click(index(menu, Entry.BRING_BACK)));
    }

    @Test
    void segmentsFollowTheState() {
        assertEquals(List.of(Entry.MATERIALS, Entry.OPACITY, Entry.LIBRARY, Entry.START_CAPTURE), entries(menu(Mode.HOLD, Selection.NONE)));
        assertEquals(List.of(Entry.CANCEL, Entry.MATERIALS, Entry.OPACITY, Entry.LIBRARY), entries(menu(Mode.HOLD, FOLLOWING)));
        assertEquals(List.of(Entry.LOCK, Entry.REMOVE, Entry.MATERIALS, Entry.OPACITY, Entry.LIBRARY, Entry.START_CAPTURE),
                entries(menu(Mode.HOLD, ANCHORED)));
        assertEquals(List.of(Entry.UNLOCK, Entry.OPTIONS, Entry.LAYERS, Entry.MATERIALS, Entry.OPACITY, Entry.LIBRARY, Entry.START_CAPTURE),
                entries(menu(Mode.HOLD, LOCKED)));
    }

    private static Selection withUnknown(Selection selection, boolean shown) {
        return new Selection(selection.state(), selection.name(), selection.layers(), selection.inHand(), true, shown);
    }

    private static RadialMenu options(Selection selection) {
        RadialMenu menu = menu(Mode.TOGGLE, selection);
        menu.click(index(menu, Entry.OPTIONS));
        return menu;
    }

    @Test
    void unknownBlocksPutOptionsAfterOpacityWhenNotLocked() {
        assertEquals(List.of(Entry.CANCEL, Entry.MATERIALS, Entry.OPACITY, Entry.OPTIONS, Entry.LIBRARY),
                entries(menu(Mode.HOLD, withUnknown(FOLLOWING, true))));
        assertEquals(List.of(Entry.LOCK, Entry.REMOVE, Entry.MATERIALS, Entry.OPACITY, Entry.OPTIONS, Entry.LIBRARY, Entry.START_CAPTURE),
                entries(menu(Mode.HOLD, withUnknown(ANCHORED, true))));
        assertEquals(List.of(Entry.UNLOCK, Entry.OPTIONS, Entry.LAYERS, Entry.MATERIALS, Entry.OPACITY, Entry.LIBRARY, Entry.START_CAPTURE),
                entries(menu(Mode.HOLD, withUnknown(LOCKED, false))));
    }

    @Test
    void unknownBlocksAreInOptionsInEveryPlacementState() {
        assertEquals(List.of(Entry.UNKNOWN_BLOCKS), entries(options(withUnknown(FOLLOWING, true))));
        assertEquals(List.of(Entry.UNKNOWN_BLOCKS), entries(options(withUnknown(ANCHORED, true))));
        assertEquals(List.of(Entry.IN_HAND, Entry.EASY_PLACE, Entry.RESTRICTION, Entry.UNKNOWN_BLOCKS, Entry.MISTAKES),
                entries(options(withUnknown(LOCKED, true))));
    }

    @Test
    void noUnknownBlocksSegmentWithoutUnknownBlocksOrSelection() {
        assertEquals(-1, index(menu(Mode.HOLD, ANCHORED), Entry.OPTIONS));
        assertEquals(-1, index(menu(Mode.HOLD, FOLLOWING), Entry.OPTIONS));
        assertEquals(-1, index(menu(Mode.HOLD, Selection.NONE), Entry.OPTIONS));
        assertEquals(-1, index(options(LOCKED), Entry.UNKNOWN_BLOCKS));
        assertEquals(-1, index(menu(Mode.HOLD, withUnknown(ANCHORED, true)), Entry.UNKNOWN_BLOCKS));
    }

    @Test
    void unknownBlocksSegmentMarksWhetherTheyShowAndPickingTogglesIt() {
        RadialMenu shown = options(withUnknown(ANCHORED, true));
        RadialMenu hidden = options(withUnknown(ANCHORED, false));
        assertEquals(true, shown.segments().get(index(shown, Entry.UNKNOWN_BLOCKS)).current());
        assertEquals(false, hidden.segments().get(index(hidden, Entry.UNKNOWN_BLOCKS)).current());
        assertEquals(run(Command.TOGGLE_UNKNOWN_BLOCKS), shown.click(index(shown, Entry.UNKNOWN_BLOCKS)));
    }

    @Test
    void materialsIsEnabledOnlyWithASelection() {
        for (Selection selection : List.of(FOLLOWING, ANCHORED, LOCKED)) {
            RadialMenu menu = menu(Mode.TOGGLE, selection);
            assertEquals(true, menu.segments().get(index(menu, Entry.MATERIALS)).enabled());
            assertEquals(run(Command.OPEN_MATERIALS), menu.click(index(menu, Entry.MATERIALS)));
        }
        RadialMenu none = menu(Mode.TOGGLE, Selection.NONE);
        assertEquals(false, none.segments().get(index(none, Entry.MATERIALS)).enabled());
    }

    @Test
    void greyedOutMaterialsDoesNothing() {
        RadialMenu toggle = menu(Mode.TOGGLE, Selection.NONE);
        assertEquals(Outcome.STAY, toggle.click(index(toggle, Entry.MATERIALS)));
        RadialMenu hold = menu(Mode.HOLD, Selection.NONE);
        assertEquals(Outcome.CLOSE, hold.release(index(hold, Entry.MATERIALS)));
    }

    @Test
    void materialsRunsOnRelease() {
        RadialMenu menu = menu(Mode.HOLD, ANCHORED);
        assertEquals(run(Command.OPEN_MATERIALS), menu.release(index(menu, Entry.MATERIALS)));
    }

    @Test
    void lockedWheelHasNoInHandSegment() {
        RadialMenu menu = menu(Mode.HOLD, LOCKED);
        assertEquals(false, entries(menu).contains(Entry.IN_HAND));
        assertEquals(false, menu.segments().get(index(menu, Entry.OPTIONS)).current());
    }

    @Test
    void optionsOpensTheSubWheelWithTheSettingsMarked() {
        RadialMenu menu = menu(Mode.TOGGLE, new Selection(State.LOCKED, "Castle Gate", Layers.Mode.OFF, false).withBuild(false, true));
        assertEquals(Outcome.STAY, menu.click(index(menu, Entry.OPTIONS)));
        assertEquals(Page.OPTIONS, menu.page());
        assertEquals(new Label("heavymatica.wheel.options", ""), menu.label());
        assertEquals(List.of(new Segment(Entry.IN_HAND, true, false), new Segment(Entry.EASY_PLACE, true, false),
                new Segment(Entry.RESTRICTION, true, true), new Segment(Entry.MISTAKES, true, false)), menu.segments());
        RadialMenu all = menu(Mode.TOGGLE, new Selection(State.LOCKED, "Castle Gate", Layers.Mode.OFF, true).withBuild(true, false));
        all.click(index(all, Entry.OPTIONS));
        assertEquals(List.of(true, true, false, false), all.segments().stream().map(Segment::current).toList());
    }

    @Test
    void optionsPicksToggleTheSetting() {
        for (Entry entry : List.of(Entry.IN_HAND, Entry.EASY_PLACE, Entry.RESTRICTION)) {
            RadialMenu menu = menu(Mode.TOGGLE, LOCKED);
            menu.click(index(menu, Entry.OPTIONS));
            Command expected = switch (entry) {
                case IN_HAND -> Command.TOGGLE_IN_HAND;
                case EASY_PLACE -> Command.TOGGLE_EASY_PLACE;
                default -> Command.TOGGLE_RESTRICTION;
            };
            assertEquals(run(expected), menu.click(index(menu, entry)));
        }
    }

    @Test
    void optionsStaysOpenAfterTheReleaseInHoldMode() {
        RadialMenu menu = menu(Mode.HOLD, LOCKED);
        assertEquals(Outcome.STAY, menu.release(index(menu, Entry.OPTIONS)));
        assertEquals(Page.OPTIONS, menu.page());
        assertEquals(Outcome.STAY, menu.release(index(menu, Entry.EASY_PLACE)));
        assertEquals(run(Command.TOGGLE_EASY_PLACE), menu.click(index(menu, Entry.EASY_PLACE)));
    }

    @Test
    void optionsClosesWithoutAChange() {
        RadialMenu escape = menu(Mode.TOGGLE, LOCKED);
        escape.click(index(escape, Entry.OPTIONS));
        assertEquals(Outcome.CLOSE, escape.escape());
        RadialMenu centre = menu(Mode.TOGGLE, LOCKED);
        centre.click(index(centre, Entry.OPTIONS));
        assertEquals(Outcome.CLOSE, centre.click(-1));
    }

    @Test
    void mistakesComesLastInOptionsAndIsMarkedWhileThePointerIsOn() {
        for (PointerMode pointer : PointerMode.values()) {
            RadialMenu menu = menu(Mode.TOGGLE, LOCKED.with(pointer));
            menu.click(index(menu, Entry.OPTIONS));
            List<Segment> segments = menu.segments();
            assertEquals(new Segment(Entry.MISTAKES, true, pointer != PointerMode.OFF), segments.getLast());
        }
    }

    @Test
    void mistakesOpensItsSubWheelWithTheCurrentModeMarked() {
        for (PointerMode pointer : PointerMode.values()) {
            RadialMenu menu = menu(Mode.TOGGLE, LOCKED.with(pointer));
            menu.click(index(menu, Entry.OPTIONS));
            assertEquals(Outcome.STAY, menu.click(index(menu, Entry.MISTAKES)));
            assertEquals(Page.MISTAKES, menu.page());
            assertEquals(new Label("heavymatica.wheel.mistakes", ""), menu.label());
            assertEquals(List.of(new Segment(Entry.MISTAKES_OFF, true, pointer == PointerMode.OFF),
                    new Segment(Entry.MISTAKES_ONLY, true, pointer == PointerMode.MISTAKES),
                    new Segment(Entry.MISTAKES_AND_MISSING, true, pointer == PointerMode.MISTAKES_AND_MISSING)), menu.segments());
        }
    }

    @Test
    void mistakesPicksSetThePointerAndClose() {
        for (Entry entry : List.of(Entry.MISTAKES_OFF, Entry.MISTAKES_ONLY, Entry.MISTAKES_AND_MISSING)) {
            RadialMenu menu = menu(Mode.TOGGLE, LOCKED.with(PointerMode.MISTAKES));
            menu.click(index(menu, Entry.OPTIONS));
            menu.click(index(menu, Entry.MISTAKES));
            PointerMode expected = switch (entry) {
                case MISTAKES_OFF -> PointerMode.OFF;
                case MISTAKES_ONLY -> PointerMode.MISTAKES;
                default -> PointerMode.MISTAKES_AND_MISSING;
            };
            assertEquals(new Outcome.Run(Command.SET_MISTAKE_POINTER, 0, null, expected), menu.click(index(menu, entry)));
        }
    }

    @Test
    void mistakesStaysOpenAfterTheReleaseInHoldMode() {
        RadialMenu menu = menu(Mode.HOLD, LOCKED);
        menu.release(index(menu, Entry.OPTIONS));
        assertEquals(Outcome.STAY, menu.release(index(menu, Entry.MISTAKES)));
        assertEquals(Page.OPTIONS, menu.page());
        menu.click(index(menu, Entry.MISTAKES));
        assertEquals(Outcome.STAY, menu.release(index(menu, Entry.MISTAKES_AND_MISSING)));
        assertEquals(Page.MISTAKES, menu.page());
        assertEquals(Outcome.Run.pointer(PointerMode.MISTAKES_AND_MISSING), menu.click(index(menu, Entry.MISTAKES_AND_MISSING)));
    }

    @Test
    void mistakesClosesWithoutAChange() {
        RadialMenu escape = menu(Mode.TOGGLE, LOCKED);
        escape.click(index(escape, Entry.OPTIONS));
        escape.click(index(escape, Entry.MISTAKES));
        assertEquals(Outcome.CLOSE, escape.escape());
        RadialMenu centre = menu(Mode.TOGGLE, LOCKED);
        centre.click(index(centre, Entry.OPTIONS));
        centre.click(index(centre, Entry.MISTAKES));
        assertEquals(Outcome.CLOSE, centre.click(-1));
    }

    @Test
    void releaseRunsTheSegmentInHoldMode() {
        RadialMenu menu = menu(Mode.HOLD, ANCHORED);
        assertEquals(run(Command.LOCK), menu.release(index(menu, Entry.LOCK)));
        RadialMenu none = menu(Mode.HOLD, Selection.NONE);
        assertEquals(run(Command.OPEN_LIBRARY), none.release(index(none, Entry.LIBRARY)));
        assertEquals(run(Command.CANCEL), menu(Mode.HOLD, FOLLOWING).release(0));
    }

    @Test
    void releaseInTheCentreCloses() {
        assertEquals(Outcome.CLOSE, menu(Mode.HOLD, ANCHORED).release(-1));
    }

    @Test
    void releaseOnRemoveAsksAndStaysOpen() {
        RadialMenu menu = menu(Mode.HOLD, ANCHORED);
        assertEquals(Outcome.STAY, menu.release(index(menu, Entry.REMOVE)));
        assertEquals(Page.CONFIRM_REMOVE, menu.page());
        assertEquals(List.of(Entry.YES, Entry.NO), entries(menu));
        assertEquals(new Label("heavymatica.wheel.confirm.remove", "Castle Gate"), menu.label());
        assertEquals(Outcome.STAY, menu.release(index(menu, Entry.YES)));
        assertEquals(run(Command.REMOVE), menu.click(index(menu, Entry.YES)));
    }

    @Test
    void cancellingARemoveCloses() {
        RadialMenu menu = menu(Mode.HOLD, ANCHORED);
        menu.release(index(menu, Entry.REMOVE));
        assertEquals(Outcome.CLOSE, menu.click(index(menu, Entry.NO)));
        RadialMenu centre = menu(Mode.TOGGLE, ANCHORED);
        centre.click(index(centre, Entry.REMOVE));
        assertEquals(Outcome.CLOSE, centre.click(-1));
        assertEquals(Outcome.CLOSE, centre.escape());
    }

    @Test
    void unlockAsksToo() {
        RadialMenu menu = menu(Mode.TOGGLE, LOCKED);
        assertEquals(Outcome.STAY, menu.click(index(menu, Entry.UNLOCK)));
        assertEquals(Page.CONFIRM_UNLOCK, menu.page());
        assertEquals(new Label("heavymatica.wheel.confirm.unlock", "Castle Gate"), menu.label());
        assertEquals(run(Command.UNLOCK), menu.click(index(menu, Entry.YES)));
    }

    @Test
    void layersOpensTheSubWheelWithTheCurrentModeMarked() {
        RadialMenu menu = menu(Mode.HOLD, LOCKED);
        assertEquals(Outcome.STAY, menu.release(index(menu, Entry.LAYERS)));
        assertEquals(Page.LAYERS, menu.page());
        assertEquals(List.of(new Segment(Entry.LAYERS_OFF, true, false), new Segment(Entry.LAYERS_FROM_TO, true, true),
                new Segment(Entry.LAYERS_FOLLOW, true, false)), menu.segments());
        assertEquals(Outcome.STAY, menu.release(0));
        assertEquals(run(Command.LAYERS_FOLLOW), menu.click(2));
        assertEquals(Layers.Mode.FOLLOW, Command.LAYERS_FOLLOW.layerMode());
        assertEquals(Layers.Mode.OFF, Command.LAYERS_OFF.layerMode());
        assertEquals(Layers.Mode.FROM_TO, Command.LAYERS_FROM_TO.layerMode());
    }

    @Test
    void layersSubWheelClosesFromTheCentre() {
        RadialMenu menu = menu(Mode.TOGGLE, LOCKED);
        menu.click(index(menu, Entry.LAYERS));
        assertEquals(Outcome.CLOSE, menu.click(-1));
    }

    @Test
    void otherLockedSegmentsRun() {
        RadialMenu menu = menu(Mode.TOGGLE, LOCKED);
        assertEquals(run(Command.OPEN_MATERIALS), menu.click(index(menu, Entry.MATERIALS)));
        assertEquals(run(Command.OPEN_LIBRARY), menu.click(index(menu, Entry.LIBRARY)));
    }

    @Test
    void toggleModeIgnoresReleases() {
        RadialMenu menu = menu(Mode.TOGGLE, ANCHORED);
        assertEquals(Outcome.STAY, menu.release(index(menu, Entry.LOCK)));
        assertEquals(Outcome.STAY, menu.release(-1));
        assertEquals(run(Command.LOCK), menu.click(index(menu, Entry.LOCK)));
    }

    @Test
    void wheelKeyAndEscapeClose() {
        assertEquals(Outcome.CLOSE, menu(Mode.TOGGLE, ANCHORED).wheelKey());
        assertEquals(Outcome.CLOSE, menu(Mode.HOLD, ANCHORED).wheelKey());
        assertEquals(Outcome.CLOSE, menu(Mode.HOLD, ANCHORED).escape());
    }

    @Test
    void clickOutsideTheSegmentsStays() {
        assertEquals(Outcome.STAY, menu(Mode.TOGGLE, ANCHORED).click(7));
    }

    @Test
    void opacityIsEnabledInEveryState() {
        for (Selection selection : List.of(Selection.NONE, FOLLOWING, ANCHORED, LOCKED)) {
            RadialMenu menu = menu(Mode.TOGGLE, selection);
            int opacity = index(menu, Entry.OPACITY);
            assertEquals(index(menu, Entry.LIBRARY) - 1, opacity);
            assertEquals(new Segment(Entry.OPACITY, true, false), menu.segments().get(opacity));
            assertEquals(Outcome.STAY, menu.click(opacity));
            assertEquals(Page.OPACITY, menu.page());
        }
    }

    @Test
    void opacityOpensTheSubWheelWithTheCurrentStepMarked() {
        RadialMenu menu = menu(Mode.HOLD, Selection.NONE);
        assertEquals(Outcome.STAY, menu.release(index(menu, Entry.OPACITY)));
        assertEquals(Page.OPACITY, menu.page());
        assertEquals(List.of(new Segment(Entry.OPACITY_STEP, true, false, 0.2), new Segment(Entry.OPACITY_STEP, true, true, 0.4),
                new Segment(Entry.OPACITY_STEP, true, false, 0.6), new Segment(Entry.OPACITY_STEP, true, false, 0.8),
                new Segment(Entry.OPACITY_STEP, true, false, 1.0)), menu.segments());
        assertEquals(new Label("heavymatica.wheel.opacity", ""), menu.label());
    }

    @Test
    void noStepIsMarkedWithoutAMatch() {
        RadialMenu menu = new RadialMenu(Mode.TOGGLE, ANCHORED, new Opacity(List.of(0.2, 0.4, 0.6, 0.8, 1.0), 0.45F));
        menu.click(index(menu, Entry.OPACITY));
        assertEquals(List.of(false, false, false, false, false), menu.segments().stream().map(Segment::current).toList());
    }

    @Test
    void pickingAStepSetsTheOpacity() {
        RadialMenu menu = menu(Mode.HOLD, LOCKED);
        menu.release(index(menu, Entry.OPACITY));
        assertEquals(Outcome.STAY, menu.release(3));
        assertEquals(new Outcome.Run(Command.SET_OPACITY, 0.8), menu.click(3));
    }

    @Test
    void opacitySubWheelClosesFromTheCentreAndEscape() {
        RadialMenu menu = menu(Mode.TOGGLE, FOLLOWING);
        menu.click(index(menu, Entry.OPACITY));
        assertEquals(Outcome.CLOSE, menu.click(-1));
        RadialMenu escape = menu(Mode.HOLD, FOLLOWING);
        escape.release(index(escape, Entry.OPACITY));
        assertEquals(Outcome.CLOSE, escape.escape());
    }

    @Test
    void runsWithoutAValueCarryZero() {
        assertEquals(new Outcome.Run(Command.LOCK, 0), new Outcome.Run(Command.LOCK));
    }

    @Test
    void labels() {
        assertEquals(new Label("heavymatica.wheel.none", ""), menu(Mode.HOLD, Selection.NONE).label());
        assertEquals(new Label("heavymatica.wheel.title", "Castle Gate"), menu(Mode.HOLD, ANCHORED).label());
    }

    private static RadialMenu capturing(Mode mode, int corners) {
        return new RadialMenu(mode, ANCHORED, OPACITY, true, new RadialMenu.Capture(true, corners));
    }

    @Test
    void captureStartsCaptureModeWithoutAFollowingPlacement() {
        RadialMenu menu = menu(Mode.TOGGLE, ANCHORED);
        assertEquals(run(Command.START_CAPTURE), menu.click(index(menu, Entry.START_CAPTURE)));
        assertEquals(-1, index(menu(Mode.HOLD, FOLLOWING), Entry.START_CAPTURE));
    }

    @Test
    void captureWheelShowsOnlyTheCaptureSegments() {
        RadialMenu none = capturing(Mode.HOLD, 0);
        assertEquals(List.of(Entry.CAPTURE, Entry.SWAP_CORNER, Entry.RESET_CORNERS, Entry.CANCEL_CAPTURE), entries(none));
        assertEquals(List.of(false, false, false, true), none.segments().stream().map(Segment::enabled).toList());
        assertEquals(List.of(false, false, true, true), capturing(Mode.HOLD, 1).segments().stream().map(Segment::enabled).toList());
        assertEquals(List.of(true, true, true, true), capturing(Mode.HOLD, 2).segments().stream().map(Segment::enabled).toList());
    }

    @Test
    void greyedOutCaptureSegmentsDoNothing() {
        RadialMenu toggle = capturing(Mode.TOGGLE, 0);
        assertEquals(Outcome.STAY, toggle.click(index(toggle, Entry.CAPTURE)));
        assertEquals(Outcome.STAY, toggle.click(index(toggle, Entry.SWAP_CORNER)));
        assertEquals(Outcome.STAY, toggle.click(index(toggle, Entry.RESET_CORNERS)));
    }

    @Test
    void captureSegmentsRun() {
        RadialMenu menu = capturing(Mode.TOGGLE, 2);
        assertEquals(run(Command.CAPTURE), menu.click(index(menu, Entry.CAPTURE)));
        assertEquals(run(Command.SWAP_CORNER), menu.click(index(menu, Entry.SWAP_CORNER)));
        assertEquals(run(Command.RESET_CORNERS), menu.click(index(menu, Entry.RESET_CORNERS)));
        assertEquals(run(Command.CANCEL_CAPTURE), menu.click(index(menu, Entry.CANCEL_CAPTURE)));
        assertEquals(run(Command.CANCEL_CAPTURE), capturing(Mode.HOLD, 0).release(3));
    }

    @Test
    void captureWheelHasItsOwnLabel() {
        assertEquals(new Label("heavymatica.wheel.capture", ""), capturing(Mode.HOLD, 1).label());
    }

    private static final RadialMenu.Paste CREATIVE = new RadialMenu.Paste(true, false, false);
    private static final RadialMenu.Paste UNDOABLE = new RadialMenu.Paste(true, false, true);
    private static final RadialMenu.Paste RUNNING = new RadialMenu.Paste(true, true, true);

    @Test
    void pasteComesAfterMaterialsOnLockedPlacementsInCreative() {
        assertEquals(List.of(Entry.UNLOCK, Entry.OPTIONS, Entry.LAYERS, Entry.MATERIALS, Entry.PASTE, Entry.OPACITY, Entry.LIBRARY, Entry.START_CAPTURE),
                entries(menu(Mode.HOLD, LOCKED.with(CREATIVE))));
    }

    @Test
    void noPasteWithoutAvailabilityOrOnFollowingAndAnchoredPlacements() {
        assertEquals(-1, index(menu(Mode.HOLD, LOCKED), Entry.PASTE));
        assertEquals(-1, index(menu(Mode.HOLD, FOLLOWING.with(CREATIVE)), Entry.PASTE));
        assertEquals(-1, index(menu(Mode.HOLD, ANCHORED.with(CREATIVE)), Entry.PASTE));
        assertEquals(-1, index(menu(Mode.HOLD, Selection.NONE.with(CREATIVE)), Entry.PASTE));
        assertEquals(-1, index(menu(Mode.HOLD, LOCKED.with(new RadialMenu.Paste(false, false, true))), Entry.UNDO_PASTE));
    }

    @Test
    void pasteOpensTheModeSubWheel() {
        RadialMenu menu = menu(Mode.TOGGLE, LOCKED.with(CREATIVE));
        assertEquals(Outcome.STAY, menu.click(index(menu, Entry.PASTE)));
        assertEquals(Page.PASTE, menu.page());
        assertEquals(List.of(Entry.PASTE_REPLACE_ALL, Entry.PASTE_ONLY_AIR, Entry.PASTE_SKIP_AIR), entries(menu));
        assertEquals(new Label("heavymatica.wheel.paste", "Castle Gate"), menu.label());
        assertEquals(Outcome.Run.paste(PasteMode.ONLY_INTO_AIR), menu.click(1));
        assertEquals(new Outcome.Run(Command.PASTE, 0, PasteMode.REPLACE_ALL, null), menu.click(0));
        assertEquals(Outcome.Run.paste(PasteMode.SKIP_AIR), menu.click(2));
    }

    @Test
    void holdReleaseOnPasteLeavesTheSubWheelOpen() {
        RadialMenu menu = menu(Mode.HOLD, LOCKED.with(CREATIVE));
        assertEquals(Outcome.STAY, menu.release(index(menu, Entry.PASTE)));
        assertEquals(Page.PASTE, menu.page());
        assertEquals(Outcome.STAY, menu.release(0));
        assertEquals(Outcome.Run.paste(PasteMode.REPLACE_ALL), menu.click(0));
    }

    @Test
    void pasteSubWheelClosesFromEscapeAndTheCentre() {
        RadialMenu escape = menu(Mode.TOGGLE, LOCKED.with(CREATIVE));
        escape.click(index(escape, Entry.PASTE));
        assertEquals(Outcome.CLOSE, escape.escape());
        RadialMenu centre = menu(Mode.TOGGLE, LOCKED.with(CREATIVE));
        centre.click(index(centre, Entry.PASTE));
        assertEquals(Outcome.CLOSE, centre.click(-1));
    }

    @Test
    void undoPasteIsLastInEveryStateWhileUndoable() {
        for (Selection selection : List.of(Selection.NONE, FOLLOWING, ANCHORED, LOCKED)) {
            List<Entry> entries = entries(menu(Mode.HOLD, selection.with(UNDOABLE)));
            assertEquals(Entry.UNDO_PASTE, entries.getLast());
            assertEquals(-1, index(menu(Mode.HOLD, selection.with(CREATIVE)), Entry.UNDO_PASTE));
        }
        assertEquals(Entry.UNDO_PASTE, entries(new RadialMenu(Mode.HOLD, Selection.NONE.with(UNDOABLE), OPACITY, true)).getLast());
        RadialMenu menu = menu(Mode.TOGGLE, Selection.NONE.with(UNDOABLE));
        assertEquals(run(Command.UNDO_PASTE), menu.click(index(menu, Entry.UNDO_PASTE)));
        RadialMenu hold = menu(Mode.HOLD, ANCHORED.with(UNDOABLE));
        assertEquals(run(Command.UNDO_PASTE), hold.release(index(hold, Entry.UNDO_PASTE)));
    }

    @Test
    void pasteAndUndoAreGreyedOutWhileAJobRuns() {
        RadialMenu toggle = menu(Mode.TOGGLE, LOCKED.with(RUNNING));
        Segment paste = toggle.segments().get(index(toggle, Entry.PASTE));
        Segment undo = toggle.segments().get(index(toggle, Entry.UNDO_PASTE));
        assertEquals(false, paste.enabled());
        assertEquals(false, undo.enabled());
        assertEquals(Outcome.STAY, toggle.click(index(toggle, Entry.PASTE)));
        assertEquals(Page.MAIN, toggle.page());
        assertEquals(Outcome.STAY, toggle.click(index(toggle, Entry.UNDO_PASTE)));
        RadialMenu hold = menu(Mode.HOLD, LOCKED.with(RUNNING));
        assertEquals(Outcome.STAY, hold.release(index(hold, Entry.PASTE)));
        assertEquals(Page.MAIN, hold.page());
    }

    @Test
    void captureWheelHasNoPasteSegments() {
        RadialMenu menu = new RadialMenu(Mode.HOLD, ANCHORED.with(UNDOABLE), OPACITY, false, new RadialMenu.Capture(true, 2));
        assertEquals(List.of(Entry.CAPTURE, Entry.SWAP_CORNER, Entry.RESET_CORNERS, Entry.CANCEL_CAPTURE), entries(menu));
    }
}
