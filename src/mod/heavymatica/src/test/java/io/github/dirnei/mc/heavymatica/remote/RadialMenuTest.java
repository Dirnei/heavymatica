package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(List.of(Entry.BRING_BACK, Entry.UNLOCK, Entry.IN_HAND, Entry.LAYERS, Entry.MATERIALS, Entry.OPACITY, Entry.LIBRARY,
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
        assertEquals(List.of(Entry.UNLOCK, Entry.IN_HAND, Entry.LAYERS, Entry.MATERIALS, Entry.OPACITY, Entry.LIBRARY, Entry.START_CAPTURE),
                entries(menu(Mode.HOLD, LOCKED)));
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
    void inHandIsMarkedWhenOn() {
        RadialMenu menu = menu(Mode.HOLD, LOCKED);
        assertEquals(true, menu.segments().get(index(menu, Entry.IN_HAND)).current());
        assertEquals(false, menu.segments().get(index(menu, Entry.UNLOCK)).current());
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
        assertEquals(run(Command.TOGGLE_IN_HAND), menu.click(index(menu, Entry.IN_HAND)));
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
}
