package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.paste.PasteMode;
import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.placement.OpacitySteps;
import io.github.dirnei.mc.heavymatica.targeting.PointerMode;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

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

    /**
     * The selected placement, what paste allows, and the Easy Place, placement restriction and mistake pointer settings
     * as the wheel opened.
     *
     * @param unknownBlocks whether it has blocks the game does not know
     * @param unknownShown  whether unknown blocks are shown
     */
    public record Selection(State state, String name, Layers.Mode layers, boolean inHand, boolean unknownBlocks, boolean unknownShown, Paste paste,
                            boolean easyPlace, boolean restriction, PointerMode pointer) {

        public static final Selection NONE = new Selection(State.NONE, "", Layers.Mode.OFF, false);

        public Selection(State state, String name, Layers.Mode layers, boolean inHand) {
            this(state, name, layers, inHand, false, true, Paste.NONE);
        }

        public Selection(State state, String name, Layers.Mode layers, boolean inHand, boolean unknownBlocks, boolean unknownShown) {
            this(state, name, layers, inHand, unknownBlocks, unknownShown, Paste.NONE);
        }

        public Selection(State state, String name, Layers.Mode layers, boolean inHand, boolean unknownBlocks, boolean unknownShown, Paste paste) {
            this(state, name, layers, inHand, unknownBlocks, unknownShown, paste, false, false, PointerMode.OFF);
        }

        public Selection with(Paste paste) {
            return new Selection(state, name, layers, inHand, unknownBlocks, unknownShown, paste, easyPlace, restriction, pointer);
        }

        public Selection withBuild(boolean easyPlace, boolean restriction) {
            return new Selection(state, name, layers, inHand, unknownBlocks, unknownShown, paste, easyPlace, restriction, pointer);
        }

        public Selection with(PointerMode pointer) {
            return new Selection(state, name, layers, inHand, unknownBlocks, unknownShown, paste, easyPlace, restriction, pointer);
        }
    }

    /**
     * @param available whether paste is available at all: creative mode in the player's own world
     * @param running   whether a paste or undo runs
     * @param undoable  whether the last paste can be undone
     */
    public record Paste(boolean available, boolean running, boolean undoable) {

        public static final Paste NONE = new Paste(false, false, false);
    }

    /** Whether capture mode was on as the wheel opened, and how many corners were set. */
    public record Capture(boolean on, int corners) {

        public static final Capture OFF = new Capture(false, 0);
    }

    /** The ghost opacity steps and the current ghost opacity as the wheel opened. */
    public record Opacity(List<Double> steps, double current) {
    }

    public enum Page {
        MAIN,
        LAYERS,
        CONFIRM_REMOVE,
        CONFIRM_UNLOCK,
        OPACITY,
        PASTE,
        OPTIONS,
        MISTAKES,
    }

    public enum Entry {
        BRING_BACK,
        LIBRARY,
        CANCEL,
        LOCK,
        REMOVE,
        UNLOCK,
        IN_HAND,
        OPTIONS,
        EASY_PLACE,
        RESTRICTION,
        MISTAKES,
        MISTAKES_OFF,
        MISTAKES_ONLY,
        MISTAKES_AND_MISSING,
        LAYERS,
        MATERIALS,
        PASTE,
        OPACITY,
        UNKNOWN_BLOCKS,
        START_CAPTURE,
        CAPTURE,
        SWAP_CORNER,
        RESET_CORNERS,
        CANCEL_CAPTURE,
        LAYERS_OFF,
        LAYERS_FROM_TO,
        LAYERS_FOLLOW,
        OPACITY_STEP,
        PASTE_REPLACE_ALL,
        PASTE_ONLY_AIR,
        PASTE_SKIP_AIR,
        UNDO_PASTE,
        YES,
        NO,
    }

    /**
     * @param current whether the entry is the current setting (layer mode, opacity step, unknown blocks shown, or a
     *                setting that is on)
     * @param value   the opacity of an opacity step, 0 for other entries
     */
    public record Segment(Entry entry, boolean enabled, boolean current, double value) {

        public Segment(Entry entry, boolean enabled, boolean current) {
            this(entry, enabled, current, 0);
        }
    }

    public enum Command {
        BRING_BACK,
        OPEN_LIBRARY,
        CANCEL,
        LOCK,
        UNLOCK,
        REMOVE,
        TOGGLE_IN_HAND,
        TOGGLE_EASY_PLACE,
        TOGGLE_RESTRICTION,
        SET_MISTAKE_POINTER,
        LAYERS_OFF,
        LAYERS_FROM_TO,
        LAYERS_FOLLOW,
        OPEN_MATERIALS,
        SET_OPACITY,
        TOGGLE_UNKNOWN_BLOCKS,
        START_CAPTURE,
        CAPTURE,
        SWAP_CORNER,
        RESET_CORNERS,
        CANCEL_CAPTURE,
        PASTE,
        UNDO_PASTE;

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

        /**
         * @param value   the opacity to set, 0 for other commands
         * @param paste   the mode of a paste, null for other commands
         * @param pointer the mistake pointer mode to set, null for other commands
         */
        record Run(Command command, double value, @Nullable PasteMode paste, @Nullable PointerMode pointer) implements Outcome {

            public Run(Command command) {
                this(command, 0);
            }

            public Run(Command command, double value) {
                this(command, value, null, null);
            }

            public static Run paste(PasteMode mode) {
                return new Run(Command.PASTE, 0, mode, null);
            }

            public static Run pointer(PointerMode mode) {
                return new Run(Command.SET_MISTAKE_POINTER, 0, null, mode);
            }
        }
    }

    /** The centre text: a translation key and the placement name it may name. */
    public record Label(String key, String name) {
    }

    private final Mode mode;
    private final Selection selection;
    private final Opacity opacity;
    private final boolean bringBack;
    private final Capture capture;
    private Page page = Page.MAIN;

    public RadialMenu(Mode mode, Selection selection, Opacity opacity) {
        this(mode, selection, opacity, false);
    }

    /** @param bringBack whether a finished placement can be brought back as the wheel opened */
    public RadialMenu(Mode mode, Selection selection, Opacity opacity, boolean bringBack) {
        this(mode, selection, opacity, bringBack, Capture.OFF);
    }

    public RadialMenu(Mode mode, Selection selection, Opacity opacity, boolean bringBack, Capture capture) {
        this.mode = mode;
        this.selection = selection;
        this.opacity = opacity;
        this.bringBack = bringBack;
        this.capture = capture;
    }

    public Mode mode() {
        return mode;
    }

    public Page page() {
        return page;
    }

    public Selection selection() {
        return selection;
    }

    public Capture capture() {
        return capture;
    }

    public List<Segment> segments() {
        return switch (page) {
            case MAIN -> capture.on() ? List.of(new Segment(Entry.CAPTURE, capture.corners() == 2, false),
                    new Segment(Entry.SWAP_CORNER, capture.corners() == 2, false), new Segment(Entry.RESET_CORNERS, capture.corners() > 0, false),
                    segment(Entry.CANCEL_CAPTURE)) : withUndoPaste(withBringBack(withPaste(withUnknownBlocks(switch (selection.state()) {
                case NONE -> List.of(new Segment(Entry.MATERIALS, false, false), segment(Entry.OPACITY), segment(Entry.LIBRARY),
                        segment(Entry.START_CAPTURE));
                case FOLLOWING -> List.of(segment(Entry.CANCEL), segment(Entry.MATERIALS), segment(Entry.OPACITY), segment(Entry.LIBRARY));
                case ANCHORED -> List.of(segment(Entry.LOCK), segment(Entry.REMOVE), segment(Entry.MATERIALS), segment(Entry.OPACITY),
                        segment(Entry.LIBRARY), segment(Entry.START_CAPTURE));
                case LOCKED -> List.of(segment(Entry.UNLOCK), segment(Entry.OPTIONS), segment(Entry.LAYERS),
                        segment(Entry.MATERIALS), segment(Entry.OPACITY), segment(Entry.LIBRARY), segment(Entry.START_CAPTURE));
            }))));
            case OPACITY -> opacity.steps().stream()
                    .map(step -> new Segment(Entry.OPACITY_STEP, true, OpacitySteps.matches(step, opacity.current()), step))
                    .toList();
            case LAYERS -> List.of(layer(Entry.LAYERS_OFF, Layers.Mode.OFF), layer(Entry.LAYERS_FROM_TO, Layers.Mode.FROM_TO),
                    layer(Entry.LAYERS_FOLLOW, Layers.Mode.FOLLOW));
            case PASTE -> List.of(segment(Entry.PASTE_REPLACE_ALL), segment(Entry.PASTE_ONLY_AIR), segment(Entry.PASTE_SKIP_AIR));
            case OPTIONS -> List.of(new Segment(Entry.IN_HAND, true, selection.inHand()), new Segment(Entry.EASY_PLACE, true, selection.easyPlace()),
                    new Segment(Entry.RESTRICTION, true, selection.restriction()), new Segment(Entry.MISTAKES, true, selection.pointer() != PointerMode.OFF));
            case MISTAKES -> List.of(pointer(Entry.MISTAKES_OFF, PointerMode.OFF), pointer(Entry.MISTAKES_ONLY, PointerMode.MISTAKES),
                    pointer(Entry.MISTAKES_AND_MISSING, PointerMode.MISTAKES_AND_MISSING));
            case CONFIRM_REMOVE, CONFIRM_UNLOCK -> List.of(segment(Entry.YES), segment(Entry.NO));
        };
    }

    /** Paste goes after Materials on an anchored or locked placement. */
    private List<Segment> withPaste(List<Segment> main) {
        Paste paste = selection.paste();
        if (!paste.available() || selection.state() != State.ANCHORED && selection.state() != State.LOCKED) {
            return main;
        }
        List<Segment> segments = new ArrayList<>(main.size() + 1);
        for (Segment segment : main) {
            segments.add(segment);
            if (segment.entry() == Entry.MATERIALS) {
                segments.add(new Segment(Entry.PASTE, !paste.running(), false));
            }
        }
        return List.copyOf(segments);
    }

    private List<Segment> withUndoPaste(List<Segment> main) {
        Paste paste = selection.paste();
        if (!paste.available() || !paste.undoable()) {
            return main;
        }
        List<Segment> segments = new ArrayList<>(main);
        segments.add(new Segment(Entry.UNDO_PASTE, !paste.running(), false));
        return List.copyOf(segments);
    }

    /** Adds the unknown blocks toggle after Opacity when the selected placement has unknown blocks. */
    private List<Segment> withUnknownBlocks(List<Segment> main) {
        if (selection.state() == State.NONE || !selection.unknownBlocks()) {
            return main;
        }
        List<Segment> segments = new ArrayList<>(main.size() + 1);
        for (Segment segment : main) {
            segments.add(segment);
            if (segment.entry() == Entry.OPACITY) {
                segments.add(new Segment(Entry.UNKNOWN_BLOCKS, true, selection.unknownShown()));
            }
        }
        return List.copyOf(segments);
    }

    private List<Segment> withBringBack(List<Segment> main) {
        if (!bringBack) {
            return main;
        }
        List<Segment> segments = new ArrayList<>(main.size() + 1);
        segments.add(segment(Entry.BRING_BACK));
        segments.addAll(main);
        return List.copyOf(segments);
    }

    public Label label() {
        return switch (page) {
            case MAIN -> capture.on() ? new Label("heavymatica.wheel.capture", "")
                    : selection.state() == State.NONE ? new Label("heavymatica.wheel.none", "") : new Label("heavymatica.wheel.title", selection.name());
            case LAYERS -> new Label("heavymatica.wheel.layers", selection.name());
            case OPACITY -> new Label("heavymatica.wheel.opacity", "");
            case PASTE -> new Label("heavymatica.wheel.paste", selection.name());
            case OPTIONS -> new Label("heavymatica.wheel.options", "");
            case MISTAKES -> new Label("heavymatica.wheel.mistakes", "");
            case CONFIRM_REMOVE -> new Label("heavymatica.wheel.confirm.remove", selection.name());
            case CONFIRM_UNLOCK -> new Label("heavymatica.wheel.confirm.unlock", selection.name());
        };
    }

    /**
     * The wheel key let go in HOLD mode. Only the main page answers it, so the release that opened a confirmation or
     * a sub-wheel can't also answer them.
     */
    public Outcome release(int index) {
        if (mode == Mode.TOGGLE || page != Page.MAIN) {
            return Outcome.STAY;
        }
        Segment segment = at(index);
        if (segment != null && !segment.enabled() && segment.entry() == Entry.PASTE) {
            return Outcome.STAY;
        }
        return segment == null || !segment.enabled() ? Outcome.CLOSE : pick(segment);
    }

    public Outcome click(int index) {
        if (index == -1) {
            return Outcome.CLOSE;
        }
        Segment segment = at(index);
        return segment == null || !segment.enabled() ? Outcome.STAY : pick(segment);
    }

    /** The wheel key pressed while open; in HOLD mode that only happens after a release the screen never saw. */
    public Outcome wheelKey() {
        return Outcome.CLOSE;
    }

    public Outcome escape() {
        return Outcome.CLOSE;
    }

    private Outcome pick(Segment segment) {
        return switch (segment.entry()) {
            case BRING_BACK -> run(Command.BRING_BACK);
            case LIBRARY -> run(Command.OPEN_LIBRARY);
            case CANCEL -> run(Command.CANCEL);
            case LOCK -> run(Command.LOCK);
            case REMOVE -> turnTo(Page.CONFIRM_REMOVE);
            case UNLOCK -> turnTo(Page.CONFIRM_UNLOCK);
            case IN_HAND -> run(Command.TOGGLE_IN_HAND);
            case OPTIONS -> turnTo(Page.OPTIONS);
            case EASY_PLACE -> run(Command.TOGGLE_EASY_PLACE);
            case RESTRICTION -> run(Command.TOGGLE_RESTRICTION);
            case MISTAKES -> turnTo(Page.MISTAKES);
            case MISTAKES_OFF -> Outcome.Run.pointer(PointerMode.OFF);
            case MISTAKES_ONLY -> Outcome.Run.pointer(PointerMode.MISTAKES);
            case MISTAKES_AND_MISSING -> Outcome.Run.pointer(PointerMode.MISTAKES_AND_MISSING);
            case LAYERS -> turnTo(Page.LAYERS);
            case MATERIALS -> run(Command.OPEN_MATERIALS);
            case PASTE -> turnTo(Page.PASTE);
            case PASTE_REPLACE_ALL -> Outcome.Run.paste(PasteMode.REPLACE_ALL);
            case PASTE_ONLY_AIR -> Outcome.Run.paste(PasteMode.ONLY_INTO_AIR);
            case PASTE_SKIP_AIR -> Outcome.Run.paste(PasteMode.SKIP_AIR);
            case UNDO_PASTE -> run(Command.UNDO_PASTE);
            case OPACITY -> turnTo(Page.OPACITY);
            case UNKNOWN_BLOCKS -> run(Command.TOGGLE_UNKNOWN_BLOCKS);
            case START_CAPTURE -> run(Command.START_CAPTURE);
            case CAPTURE -> run(Command.CAPTURE);
            case SWAP_CORNER -> run(Command.SWAP_CORNER);
            case RESET_CORNERS -> run(Command.RESET_CORNERS);
            case CANCEL_CAPTURE -> run(Command.CANCEL_CAPTURE);
            case OPACITY_STEP -> new Outcome.Run(Command.SET_OPACITY, segment.value());
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

    private Segment pointer(Entry entry, PointerMode pointer) {
        return new Segment(entry, true, selection.pointer() == pointer);
    }
}
