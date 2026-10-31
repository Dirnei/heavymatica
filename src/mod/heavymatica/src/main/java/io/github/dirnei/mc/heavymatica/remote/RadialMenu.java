package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.placement.OpacitySteps;
import java.util.ArrayList;
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
    }

    public enum Entry {
        BRING_BACK,
        LIBRARY,
        CANCEL,
        LOCK,
        REMOVE,
        UNLOCK,
        IN_HAND,
        LAYERS,
        MATERIALS,
        OPACITY,
        START_CAPTURE,
        CAPTURE,
        SWAP_CORNER,
        RESET_CORNERS,
        CANCEL_CAPTURE,
        LAYERS_OFF,
        LAYERS_FROM_TO,
        LAYERS_FOLLOW,
        OPACITY_STEP,
        YES,
        NO,
    }

    /**
     * @param current whether the entry is the current setting (layer mode, in-hand filter on, opacity step)
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
        LAYERS_OFF,
        LAYERS_FROM_TO,
        LAYERS_FOLLOW,
        OPEN_MATERIALS,
        SET_OPACITY,
        START_CAPTURE,
        CAPTURE,
        SWAP_CORNER,
        RESET_CORNERS,
        CANCEL_CAPTURE;

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

        /** @param value the opacity to set, 0 for other commands */
        record Run(Command command, double value) implements Outcome {

            public Run(Command command) {
                this(command, 0);
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

    public List<Segment> segments() {
        return switch (page) {
            case MAIN -> capture.on() ? List.of(new Segment(Entry.CAPTURE, capture.corners() == 2, false),
                    new Segment(Entry.SWAP_CORNER, capture.corners() == 2, false), new Segment(Entry.RESET_CORNERS, capture.corners() > 0, false),
                    segment(Entry.CANCEL_CAPTURE)) : withBringBack(switch (selection.state()) {
                case NONE -> List.of(new Segment(Entry.MATERIALS, false, false), segment(Entry.OPACITY), segment(Entry.LIBRARY),
                        segment(Entry.START_CAPTURE));
                case FOLLOWING -> List.of(segment(Entry.CANCEL), segment(Entry.MATERIALS), segment(Entry.OPACITY), segment(Entry.LIBRARY));
                case ANCHORED -> List.of(segment(Entry.LOCK), segment(Entry.REMOVE), segment(Entry.MATERIALS), segment(Entry.OPACITY),
                        segment(Entry.LIBRARY), segment(Entry.START_CAPTURE));
                case LOCKED -> List.of(segment(Entry.UNLOCK), new Segment(Entry.IN_HAND, true, selection.inHand()), segment(Entry.LAYERS),
                        segment(Entry.MATERIALS), segment(Entry.OPACITY), segment(Entry.LIBRARY), segment(Entry.START_CAPTURE));
            });
            case OPACITY -> opacity.steps().stream()
                    .map(step -> new Segment(Entry.OPACITY_STEP, true, OpacitySteps.matches(step, opacity.current()), step))
                    .toList();
            case LAYERS -> List.of(layer(Entry.LAYERS_OFF, Layers.Mode.OFF), layer(Entry.LAYERS_FROM_TO, Layers.Mode.FROM_TO),
                    layer(Entry.LAYERS_FOLLOW, Layers.Mode.FOLLOW));
            case CONFIRM_REMOVE, CONFIRM_UNLOCK -> List.of(segment(Entry.YES), segment(Entry.NO));
        };
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
            case LAYERS -> turnTo(Page.LAYERS);
            case MATERIALS -> run(Command.OPEN_MATERIALS);
            case OPACITY -> turnTo(Page.OPACITY);
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
}
