package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.placement.Layers;
import java.util.List;

/**
 * The open wheel, free of the game: which segments show for the selection it was opened on, which page is up, and
 * what a key release, a click, the wheel key or Esc does. Segment index -1 is the centre.
 */
public final class RadialMenu {

    public enum Mode {
        HOLD,
        TOGGLE,
    }

    public enum State {
        NONE,
        FOLLOWING,
        ANCHORED,
        LOCKED,
    }

    /** The selected placement as the wheel opened. */
    public record Selection(State state, String name, Layers.Mode layers, boolean inHand) {

        public static final Selection NONE = new Selection(State.NONE, "", Layers.Mode.OFF, false);
    }

    public enum Page {
        MAIN,
        LAYERS,
        CONFIRM_REMOVE,
        CONFIRM_UNLOCK,
    }

    public enum Entry {
        LIBRARY,
        CANCEL,
        LOCK,
        REMOVE,
        UNLOCK,
        IN_HAND,
        LAYERS,
        MATERIAL_LIST,
        MATERIALS,
        LAYERS_OFF,
        LAYERS_FROM_TO,
        LAYERS_FOLLOW,
        YES,
        NO,
    }

    /** @param current whether the entry is the placement's current setting (layer mode, in-hand filter on) */
    public record Segment(Entry entry, boolean enabled, boolean current) {
    }

    public enum Command {
        OPEN_LIBRARY,
        CANCEL,
        LOCK,
        UNLOCK,
        REMOVE,
        TOGGLE_IN_HAND,
        LAYERS_OFF,
        LAYERS_FROM_TO,
        LAYERS_FOLLOW,
        OPEN_MATERIAL_LIST,
        OPEN_MATERIALS;

        /** The layer mode a layers command sets, or null for other commands. */
        public Layers.Mode layerMode() {
            return switch (this) {
                case LAYERS_OFF -> Layers.Mode.OFF;
                case LAYERS_FROM_TO -> Layers.Mode.FROM_TO;
                case LAYERS_FOLLOW -> Layers.Mode.FOLLOW;
                default -> null;
            };
        }
    }

    public sealed interface Outcome {

        Outcome STAY = new Stay();
        Outcome CLOSE = new Close();

        record Stay() implements Outcome {
        }

        record Close() implements Outcome {
        }

        record Run(Command command) implements Outcome {
        }
    }

    /** The centre text: a translation key and the placement name it may name. */
    public record Label(String key, String name) {
    }

    private final Mode mode;
    private final Selection selection;
    private Page page = Page.MAIN;

    public RadialMenu(Mode mode, Selection selection) {
        this.mode = mode;
        this.selection = selection;
    }

    public Mode mode() {
        return mode;
    }

    public Page page() {
        return page;
    }

    public List<Segment> segments() {
        return switch (page) {
            case MAIN -> switch (selection.state()) {
                case NONE -> List.of(new Segment(Entry.MATERIALS, false, false), segment(Entry.LIBRARY));
                case FOLLOWING -> List.of(segment(Entry.CANCEL), segment(Entry.MATERIALS), segment(Entry.LIBRARY));
                case ANCHORED -> List.of(segment(Entry.LOCK), segment(Entry.REMOVE), segment(Entry.MATERIALS), segment(Entry.LIBRARY));
                case LOCKED -> List.of(segment(Entry.UNLOCK), new Segment(Entry.IN_HAND, true, selection.inHand()), segment(Entry.LAYERS),
                        segment(Entry.MATERIAL_LIST), segment(Entry.MATERIALS), segment(Entry.LIBRARY));
            };
            case LAYERS -> List.of(layer(Entry.LAYERS_OFF, Layers.Mode.OFF), layer(Entry.LAYERS_FROM_TO, Layers.Mode.FROM_TO),
                    layer(Entry.LAYERS_FOLLOW, Layers.Mode.FOLLOW));
            case CONFIRM_REMOVE, CONFIRM_UNLOCK -> List.of(segment(Entry.YES), segment(Entry.NO));
        };
    }

    public Label label() {
        return switch (page) {
            case MAIN -> selection.state() == State.NONE ? new Label("heavymatica.wheel.none", "") : new Label("heavymatica.wheel.title", selection.name());
            case LAYERS -> new Label("heavymatica.wheel.layers", selection.name());
            case CONFIRM_REMOVE -> new Label("heavymatica.wheel.confirm.remove", selection.name());
            case CONFIRM_UNLOCK -> new Label("heavymatica.wheel.confirm.unlock", selection.name());
        };
    }

    /**
     * The wheel key let go in HOLD mode. Only the main page answers it, so the release that opened a confirmation or
     * the layers can't also answer them.
     */
    public Outcome release(int index) {
        if (mode == Mode.TOGGLE || page != Page.MAIN) {
            return Outcome.STAY;
        }
        Segment segment = at(index);
        return segment == null || !segment.enabled() ? Outcome.CLOSE : pick(segment.entry());
    }

    public Outcome click(int index) {
        if (index == -1) {
            return Outcome.CLOSE;
        }
        Segment segment = at(index);
        return segment == null || !segment.enabled() ? Outcome.STAY : pick(segment.entry());
    }

    /** The wheel key pressed while open; in HOLD mode that only happens after a release the screen never saw. */
    public Outcome wheelKey() {
        return Outcome.CLOSE;
    }

    public Outcome escape() {
        return Outcome.CLOSE;
    }

    private Outcome pick(Entry entry) {
        return switch (entry) {
            case LIBRARY -> run(Command.OPEN_LIBRARY);
            case CANCEL -> run(Command.CANCEL);
            case LOCK -> run(Command.LOCK);
            case REMOVE -> turnTo(Page.CONFIRM_REMOVE);
            case UNLOCK -> turnTo(Page.CONFIRM_UNLOCK);
            case IN_HAND -> run(Command.TOGGLE_IN_HAND);
            case LAYERS -> turnTo(Page.LAYERS);
            case MATERIAL_LIST -> run(Command.OPEN_MATERIAL_LIST);
            case MATERIALS -> run(Command.OPEN_MATERIALS);
            case LAYERS_OFF -> run(Command.LAYERS_OFF);
            case LAYERS_FROM_TO -> run(Command.LAYERS_FROM_TO);
            case LAYERS_FOLLOW -> run(Command.LAYERS_FOLLOW);
            case YES -> run(page == Page.CONFIRM_REMOVE ? Command.REMOVE : Command.UNLOCK);
            case NO -> Outcome.CLOSE;
        };
    }

    private Outcome turnTo(Page next) {
        page = next;
        return Outcome.STAY;
    }

    private static Outcome run(Command command) {
        return new Outcome.Run(command);
    }

    private Segment at(int index) {
        List<Segment> segments = segments();
        return index >= 0 && index < segments.size() ? segments.get(index) : null;
    }

    private static Segment segment(Entry entry) {
        return new Segment(entry, true, false);
    }

    private Segment layer(Entry entry, Layers.Mode layers) {
        return new Segment(entry, true, selection.layers() == layers);
    }
}
