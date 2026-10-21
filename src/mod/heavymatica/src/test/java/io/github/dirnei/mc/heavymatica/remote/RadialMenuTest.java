package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Command;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Label;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Mode;
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
    void segmentsFollowTheState() {
        assertEquals(List.of(Entry.LIBRARY), entries(new RadialMenu(Mode.HOLD, Selection.NONE)));
        assertEquals(List.of(Entry.CANCEL, Entry.LIBRARY), entries(new RadialMenu(Mode.HOLD, FOLLOWING)));
        assertEquals(List.of(Entry.LOCK, Entry.REMOVE, Entry.LIBRARY), entries(new RadialMenu(Mode.HOLD, ANCHORED)));
        assertEquals(List.of(Entry.UNLOCK, Entry.IN_HAND, Entry.LAYERS, Entry.MATERIAL_LIST, Entry.LIBRARY), entries(new RadialMenu(Mode.HOLD, LOCKED)));
    }

    @Test
    void inHandIsMarkedWhenOn() {
        RadialMenu menu = new RadialMenu(Mode.HOLD, LOCKED);
        assertEquals(true, menu.segments().get(index(menu, Entry.IN_HAND)).current());
        assertEquals(false, menu.segments().get(index(menu, Entry.UNLOCK)).current());
    }

    @Test
    void releaseRunsTheSegmentInHoldMode() {
        RadialMenu menu = new RadialMenu(Mode.HOLD, ANCHORED);
        assertEquals(run(Command.LOCK), menu.release(index(menu, Entry.LOCK)));
        assertEquals(run(Command.OPEN_LIBRARY), new RadialMenu(Mode.HOLD, Selection.NONE).release(0));
        assertEquals(run(Command.CANCEL), new RadialMenu(Mode.HOLD, FOLLOWING).release(0));
    }

    @Test
    void releaseInTheCentreCloses() {
        assertEquals(Outcome.CLOSE, new RadialMenu(Mode.HOLD, ANCHORED).release(-1));
    }

    @Test
    void releaseOnRemoveAsksAndStaysOpen() {
        RadialMenu menu = new RadialMenu(Mode.HOLD, ANCHORED);
        assertEquals(Outcome.STAY, menu.release(index(menu, Entry.REMOVE)));
        assertEquals(Page.CONFIRM_REMOVE, menu.page());
        assertEquals(List.of(Entry.YES, Entry.NO), entries(menu));
        assertEquals(new Label("heavymatica.wheel.confirm.remove", "Castle Gate"), menu.label());
        assertEquals(Outcome.STAY, menu.release(index(menu, Entry.YES)));
        assertEquals(run(Command.REMOVE), menu.click(index(menu, Entry.YES)));
    }

    @Test
    void cancellingARemoveCloses() {
        RadialMenu menu = new RadialMenu(Mode.HOLD, ANCHORED);
        menu.release(index(menu, Entry.REMOVE));
        assertEquals(Outcome.CLOSE, menu.click(index(menu, Entry.NO)));
        RadialMenu centre = new RadialMenu(Mode.TOGGLE, ANCHORED);
        centre.click(index(centre, Entry.REMOVE));
        assertEquals(Outcome.CLOSE, centre.click(-1));
        assertEquals(Outcome.CLOSE, centre.escape());
    }

    @Test
    void unlockAsksToo() {
        RadialMenu menu = new RadialMenu(Mode.TOGGLE, LOCKED);
        assertEquals(Outcome.STAY, menu.click(index(menu, Entry.UNLOCK)));
        assertEquals(Page.CONFIRM_UNLOCK, menu.page());
        assertEquals(new Label("heavymatica.wheel.confirm.unlock", "Castle Gate"), menu.label());
        assertEquals(run(Command.UNLOCK), menu.click(index(menu, Entry.YES)));
    }

    @Test
    void layersOpensTheSubWheelWithTheCurrentModeMarked() {
        RadialMenu menu = new RadialMenu(Mode.HOLD, LOCKED);
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
        RadialMenu menu = new RadialMenu(Mode.TOGGLE, LOCKED);
        menu.click(index(menu, Entry.LAYERS));
        assertEquals(Outcome.CLOSE, menu.click(-1));
    }

    @Test
    void otherLockedSegmentsRun() {
        RadialMenu menu = new RadialMenu(Mode.TOGGLE, LOCKED);
        assertEquals(run(Command.TOGGLE_IN_HAND), menu.click(index(menu, Entry.IN_HAND)));
        assertEquals(run(Command.OPEN_MATERIAL_LIST), menu.click(index(menu, Entry.MATERIAL_LIST)));
        assertEquals(run(Command.OPEN_LIBRARY), menu.click(index(menu, Entry.LIBRARY)));
    }

    @Test
    void toggleModeIgnoresReleases() {
        RadialMenu menu = new RadialMenu(Mode.TOGGLE, ANCHORED);
        assertEquals(Outcome.STAY, menu.release(index(menu, Entry.LOCK)));
        assertEquals(Outcome.STAY, menu.release(-1));
        assertEquals(run(Command.LOCK), menu.click(index(menu, Entry.LOCK)));
    }

    @Test
    void wheelKeyAndEscapeClose() {
        assertEquals(Outcome.CLOSE, new RadialMenu(Mode.TOGGLE, ANCHORED).wheelKey());
        assertEquals(Outcome.CLOSE, new RadialMenu(Mode.HOLD, ANCHORED).wheelKey());
        assertEquals(Outcome.CLOSE, new RadialMenu(Mode.HOLD, ANCHORED).escape());
    }

    @Test
    void clickOutsideTheSegmentsStays() {
        assertEquals(Outcome.STAY, new RadialMenu(Mode.TOGGLE, ANCHORED).click(7));
    }

    @Test
    void labels() {
        assertEquals(new Label("heavymatica.wheel.none", ""), new RadialMenu(Mode.HOLD, Selection.NONE).label());
        assertEquals(new Label("heavymatica.wheel.title", "Castle Gate"), new RadialMenu(Mode.HOLD, ANCHORED).label());
    }
}
