package io.github.dirnei.mc.heavymatica.remote;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Outcome;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Segment;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The placement wheel. A screen, so the mouse is free and the camera stays still; it does not pause, and while it is
 * open the player's movement is read straight from the movement keys, since key mappings in the in-game context
 * report up while any screen is open.
 */
public final class RadialMenuScreen extends Screen {

    private static final int NORMAL = 0xB0282828;
    private static final int HOVERED = 0xE0406FB0;
    private static final int CURRENT = 0xC0205030;
    private static final int DISABLED = 0x80101010;
    private static final int DANGER = 0xE0A03030;
    private static final int CONFIRM = 0xE0309040;
    private static final int CENTRE = 0xC0000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF808080;
    private static final double GAP = 3;

    /** Movement keys a development script holds, as if they were down on the keyboard. */
    private static final Set<String> heldForDevelopment = new HashSet<>();

    private final RadialMenu menu;
    private final Consumer<RadialMenu.Command> commands;
    private final Input before;
    private @Nullable ClientInput playerInput;
    private List<WheelGeometry.Span> spans = List.of();
    private int spanCount = -1;
    private double mouseX = Double.NaN;
    private double mouseY = Double.NaN;

    /** @param before the player's movement input of the last tick, for sneak and sprint in toggle mode */
    public RadialMenuScreen(RadialMenu menu, Consumer<RadialMenu.Command> commands, Input before) {
        super(Component.translatable("key.heavymatica.wheel"));
        this.menu = menu;
        this.commands = commands;
        this.before = before;
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
        spanCount = -1;
    }

    @Override
    public void removed() {
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
        if (minecraft.player == null || !Remotes.held()) {
            onClose();
        }
    }

    /** The outer radius; the ring shrinks on small windows. */
    public int outer() {
        return Math.max(40, Math.min(96, height / 2 - 28));
    }

    public int inner() {
        return outer() * 2 / 5;
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
            apply(menu.wheelKey());
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (!RemoteKeys.WHEEL.isUnbound() && RemoteKeys.WHEEL.matches(event)) {
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
            }
            case Outcome.Close close -> onClose();
            case Outcome.Run run -> {
                onClose();
                commands.accept(run.command());
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        List<Segment> segments = menu.segments();
        if (spanCount != segments.size()) {
            spans = WheelGeometry.spans(inner(), outer(), segments.size(), GAP);
            spanCount = segments.size();
        }
        int cx = width / 2;
        int cy = height / 2;
        int hovered = hovered();
        for (WheelGeometry.Span span : spans) {
            graphics.fill(cx + span.x0(), cy + span.y(), cx + span.x1(), cy + span.y() + 1, color(segments.get(span.segment()), span.segment() == hovered));
        }
        for (WheelGeometry.Span span : WheelGeometry.spans(0, inner() - 3, 1, 0)) {
            graphics.fill(cx + span.x0(), cy + span.y(), cx + span.x1(), cy + span.y() + 1, CENTRE);
        }
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            double[] point = segmentPoint(i);
            int x = (int) Math.round(point[0]);
            int y = (int) Math.round(point[1]);
            graphics.item(new ItemStack(icon(segment.entry())), x - 8, y - 12);
            graphics.centeredText(font, name(segment.entry()), x, y + 6, segment.enabled() ? TEXT : MUTED);
        }
        RadialMenu.Label label = menu.label();
        Component title = Component.translatable(label.key(), label.name());
        graphics.centeredText(font, title, cx, cy - outer() - 14, TEXT);
        if (hovered >= 0) {
            Component hint = name(segments.get(hovered).entry());
            graphics.centeredText(font, hint, cx, cy - 4, TEXT);
        }
    }

    private int color(Segment segment, boolean hovered) {
        if (!segment.enabled()) {
            return DISABLED;
        }
        if (hovered) {
            return switch (segment.entry()) {
                case REMOVE, CANCEL, NO -> DANGER;
                case YES -> CONFIRM;
                default -> HOVERED;
            };
        }
        return segment.current() ? CURRENT : NORMAL;
    }

    public static Component name(Entry entry) {
        return Component.translatable("heavymatica.wheel.entry." + entry.name().toLowerCase(Locale.ROOT));
    }

    private static Item icon(Entry entry) {
        return switch (entry) {
            case LIBRARY -> Items.BOOK;
            case CANCEL, REMOVE -> Items.BARRIER;
            case LOCK -> Items.IRON_BARS;
            case UNLOCK -> Items.OAK_DOOR;
            case IN_HAND -> Items.SPYGLASS;
            case LAYERS -> Items.LADDER;
            case MATERIAL_LIST -> Items.CHEST;
            case LAYERS_OFF -> Items.GLASS;
            case LAYERS_FROM_TO -> Items.SCAFFOLDING;
            case LAYERS_FOLLOW -> Items.LEATHER_BOOTS;
            case YES -> Items.LIME_DYE;
            case NO -> Items.RED_DYE;
        };
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
        InputConstants.Key key = mapping.getKey();
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
