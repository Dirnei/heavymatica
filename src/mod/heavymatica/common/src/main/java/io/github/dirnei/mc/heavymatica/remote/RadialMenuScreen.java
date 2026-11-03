package io.github.dirnei.mc.heavymatica.remote;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.placement.OpacitySteps;
import io.github.dirnei.mc.heavymatica.platform.Platform;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Outcome;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Page;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Segment;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Selection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The placement wheel. A screen, so the mouse is free and the camera stays still; it does not pause, and while it is
 * open the player's movement is read straight from the movement keys, since key mappings in the in-game context
 * report up while any screen is open.
 */
public final class RadialMenuScreen extends Screen {

    /** The quickest a fast sweep can tick, so it does not buzz. */
    private static final long TICK_SPACING = 30;

    /** Movement keys a development script holds, as if they were down on the keyboard. */
    private static final Set<String> heldForDevelopment = new HashSet<>();

    private final RadialMenu menu;
    private final Consumer<Outcome.Run> commands;
    private final BooleanSupplier allowed;
    private final Input before;
    private @Nullable ClientInput playerInput;
    private final WheelAnimation animation;
    private final boolean sounds;
    private Page shownPage;
    private List<Segment> shownSegments;
    private List<Segment> previousSegments = List.of();
    private int lastHovered = -1;
    private long lastTick;
    private @Nullable WheelPainter lastFrame;
    private double mouseX = Double.NaN;
    private double mouseY = Double.NaN;
    /** Holding the key sends repeated presses, which must not count as a second tap. */
    private boolean wheelKeyHeld;

    /**
     * @param before  the player's movement input of the last tick, for sneak and sprint in toggle mode
     * @param allowed whether the wheel may stay open, checked every tick
     */
    public RadialMenuScreen(RadialMenu menu, Consumer<Outcome.Run> commands, Input before, BooleanSupplier allowed) {
        super(Component.translatable("key.heavymatica.wheel"));
        this.menu = menu;
        this.commands = commands;
        this.before = before;
        this.allowed = allowed;
        this.wheelKeyHeld = physicallyDown(RemoteKeys.WHEEL);
        this.animation = new WheelAnimation(HeavymaticaConfig.wheelAnimations(), Util.getMillis());
        this.sounds = HeavymaticaConfig.wheelSounds();
        this.shownPage = menu.page();
        this.shownSegments = menu.segments();
    }

    public RadialMenu menu() {
        return menu;
    }

    @Override
    protected void init() {
        LocalPlayer player = minecraft.player;
        if (player != null && !(player.input instanceof WheelMovement)) {
            playerInput = player.input;
            player.input = new WheelMovement(minecraft.options, before);
        }
    }

    @Override
    public void removed() {
        if (animation.enabled() && lastFrame != null) {
            WheelAfterglow.start(lastFrame);
        }
        LocalPlayer player = minecraft.player;
        if (player != null && playerInput != null && player.input instanceof WheelMovement) {
            player.input = playerInput;
        }
        Options options = minecraft.options;
        for (KeyMapping key : new KeyMapping[] {options.keyUp, options.keyDown, options.keyLeft, options.keyRight, options.keyJump}) {
            key.setDown(physicallyDown(key));
        }
        if (!options.toggleCrouch().get()) {
            options.keyShift.setDown(physicallyDown(options.keyShift));
        }
        if (!options.toggleSprint().get()) {
            options.keySprint.setDown(physicallyDown(options.keySprint));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (minecraft.player == null || !allowed.getAsBoolean()) {
            onClose();
        }
    }

    /** The outer radius; the ring shrinks on small windows. */
    public int outer() {
        return Math.max(40, Math.min(96, height / 2 - 28));
    }

    public int inner() {
        return outer() * 9 / 20;
    }

    /** The hovered segment index, or -1 in the centre. */
    public int hovered() {
        if (Double.isNaN(mouseX)) {
            return -1;
        }
        return WheelGeometry.segmentAt(mouseX - width / 2.0, mouseY - height / 2.0, menu.segments().size(), inner());
    }

    /** Where a segment's icon sits, in GUI coordinates. */
    public double[] segmentPoint(int index) {
        double angle = WheelGeometry.middle(index, menu.segments().size());
        double radius = (inner() + outer()) / 2.0;
        return new double[] {width / 2.0 + Math.sin(angle) * radius, height / 2.0 - Math.cos(angle) * radius};
    }

    @Override
    public void mouseMoved(double x, double y) {
        mouseX = x;
        mouseY = y;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            apply(menu.escape());
        } else if (!RemoteKeys.WHEEL.isUnbound() && RemoteKeys.WHEEL.matches(event)) {
            if (!wheelKeyHeld) {
                wheelKeyHeld = true;
                apply(menu.wheelKey());
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (!RemoteKeys.WHEEL.isUnbound() && RemoteKeys.WHEEL.matches(event)) {
            wheelKeyHeld = false;
            apply(menu.release(hovered()));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        mouseMoved(event.x(), event.y());
        if (RemoteKeys.WHEEL.matchesMouse(event)) {
            apply(menu.wheelKey());
        } else if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            apply(menu.click(hovered()));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (RemoteKeys.WHEEL.matchesMouse(event)) {
            mouseMoved(event.x(), event.y());
            apply(menu.release(hovered()));
        }
        return true;
    }

    private void apply(Outcome outcome) {
        switch (outcome) {
            case Outcome.Stay stay -> {
                if (menu.page() != shownPage) {
                    play(1.4F, 0.3F);
                }
            }
            case Outcome.Close close -> onClose();
            case Outcome.Run run -> {
                play(1.4F, 0.3F);
                onClose();
                commands.accept(run);
            }
        }
    }

    private void play(float pitch, float volume) {
        if (sounds) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        long now = Util.getMillis();
        if (menu.page() != shownPage) {
            previousSegments = shownSegments;
            shownPage = menu.page();
            animation.turnPage(now);
            lastHovered = -1;
        }
        shownSegments = menu.segments();
        int count = shownSegments.size();
        int hovered = hovered();
        animation.update(now, count, hovered);
        if (hovered != lastHovered) {
            if (hovered >= 0 && now - lastTick >= TICK_SPACING) {
                play(2.0F, 0.12F);
                lastTick = now;
            }
            lastHovered = hovered;
        }
        double[] hover = new double[count];
        double[] arrival = new double[count];
        for (int i = 0; i < count; i++) {
            hover[i] = animation.hover(i);
            arrival[i] = animation.arrival(i, count, now);
        }
        WheelPainter.Ring ring = new WheelPainter.Ring(shownSegments, hover, arrival, 1, animation.openScale(now), animation.pageRotation(now));
        WheelPainter.Ring previous = null;
        if (animation.previousShown(now) && !previousSegments.isEmpty()) {
            int before = previousSegments.size();
            double[] previousHover = new double[before];
            double[] arrived = new double[before];
            for (int i = 0; i < before; i++) {
                previousHover[i] = animation.previousHover(i);
                arrived[i] = 1;
            }
            previous = new WheelPainter.Ring(previousSegments, previousHover, arrived, animation.previousAlpha(now),
                    animation.openScale(now) * animation.previousScale(now), animation.previousRotation(now));
        }
        WheelPainter painter = new WheelPainter(width, height, inner(), outer(), 1.0 / minecraft.getWindow().getGuiScale(),
                animation.openAlpha(now), ring, previous, hovered, animation.accentAngle(), animation.accentShown(), title());
        painter.paint(graphics, font, 1, 1);
        lastFrame = painter;
    }

    /** The centre text while nothing is hovered: the placement and its state, or what the page asks. */
    private WheelPainter.Title title() {
        RadialMenu.Label label = menu.label();
        Component head = Component.translatable(label.key(), label.name());
        Selection selection = menu.selection();
        return switch (menu.page()) {
            case MAIN -> {
                if (menu.capture().on()) {
                    yield new WheelPainter.Title(head, Component.translatable("heavymatica.wheel.centre.corners", menu.capture().corners()));
                }
                if (selection.state() == RadialMenu.State.NONE) {
                    yield new WheelPainter.Title(head, Component.translatable("heavymatica.wheel.centre.none"));
                }
                yield new WheelPainter.Title(head, Component.translatable(WheelText.stateKey(selection.state())));
            }
            case CONFIRM_REMOVE, CONFIRM_UNLOCK -> new WheelPainter.Title(head, Component.translatable("heavymatica.wheel.centre.confirm"));
            default -> new WheelPainter.Title(head, null);
        };
    }

    public static Component name(Segment segment) {
        String key = "heavymatica.wheel.entry." + segment.entry().name().toLowerCase(Locale.ROOT);
        if (segment.entry() == Entry.UNKNOWN_BLOCKS) {
            return Component.translatable(key + (segment.current() ? ".shown" : ".hidden"));
        }
        return segment.entry() == Entry.OPACITY_STEP ? Component.translatable(key, OpacitySteps.percent(segment.value())) : Component.translatable(key);
    }

    /** Holds or lets go of a movement key for development scripts, by key mapping name. */
    public static void holdForDevelopment(String mapping, boolean down) {
        if (down) {
            heldForDevelopment.add(mapping);
        } else {
            heldForDevelopment.remove(mapping);
        }
    }

    static boolean physicallyDown(KeyMapping mapping) {
        if (heldForDevelopment.contains(mapping.getName())) {
            return true;
        }
        InputConstants.Key key = Platform.get().keys().boundKey(mapping);
        long window = Minecraft.getInstance().getWindow().handle();
        return switch (key.getType()) {
            case KEYSYM -> key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> false;
        };
    }

    /** Movement from the keys themselves; sneak and sprint in toggle mode keep the state they had as the wheel opened. */
    private static final class WheelMovement extends ClientInput {

        private final Options options;
        private final Input before;

        WheelMovement(Options options, Input before) {
            this.options = options;
            this.before = before;
        }

        @Override
        public void tick() {
            boolean forward = physicallyDown(options.keyUp);
            boolean backward = physicallyDown(options.keyDown);
            boolean left = physicallyDown(options.keyLeft);
            boolean right = physicallyDown(options.keyRight);
            boolean shift = options.toggleCrouch().get() ? before.shift() : physicallyDown(options.keyShift);
            boolean sprint = options.toggleSprint().get() ? before.sprint() : physicallyDown(options.keySprint);
            keyPresses = new Input(forward, backward, left, right, physicallyDown(options.keyJump), shift, sprint);
            moveVector = new Vec2(impulse(left, right), impulse(forward, backward)).normalized();
        }

        private static float impulse(boolean positive, boolean negative) {
            return positive == negative ? 0.0F : positive ? 1.0F : -1.0F;
        }
    }
}
