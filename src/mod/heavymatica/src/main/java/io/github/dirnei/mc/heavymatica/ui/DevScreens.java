package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaClient;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.capture.CaptureBox;
import io.github.dirnei.mc.heavymatica.capture.CaptureController;
import io.github.dirnei.mc.heavymatica.capture.CaptureSession;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.formats.SchematicReader;
import io.github.dirnei.mc.heavymatica.library.LocalLibrary;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.placement.ComparisonState;
import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.placement.MaterialList;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform;
import io.github.dirnei.mc.heavymatica.placement.WorldComparison;
import io.github.dirnei.mc.heavymatica.remote.PlacementOverlay;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu;
import io.github.dirnei.mc.heavymatica.remote.RadialMenuScreen;
import io.github.dirnei.mc.heavymatica.remote.RemoteInput;
import io.github.dirnei.mc.heavymatica.remote.RemoteKeys;
import io.github.dirnei.mc.heavymatica.remote.Remotes;
import io.github.dirnei.mc.heavymatica.targeting.GhostTargeting;
import io.github.dirnei.mc.heavymatica.targeting.TargetHud;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import io.github.dirnei.mc.heavymatica.account.CloudClient;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development aid: runs a script of UI steps once the game has loaded, so screens can be checked on a real client
 * without clicking. The {@code heavymatica.dev.screen} system property (set from {@code HEAVYMATICA_DEV_SCREEN} by the
 * dev runs) holds steps separated by {@code ;}:
 * <ul>
 *     <li>{@code signin}: sign in (use with {@code devSessionJoinUrl} in the client config)</li>
 *     <li>{@code import=<dir>}: add every file in the directory as a local entry</li>
 *     <li>{@code library}: open the library</li>
 *     <li>{@code select}: select the first card</li>
 *     <li>{@code search=<text>}: type into the search box, character by character</li>
 *     <li>{@code clickSidebar=<label>}, {@code clickCard=<index>}, {@code clickVersion=<index>}: click there; card indexes
 *     count the up and folder cards too</li>
 *     <li>{@code openFolder=<path>}: open a folder of the library explorer ({@code openFolder=} for the top level)</li>
 *     <li>{@code clickButton=<translation key>}: click the button with that label</li>
 *     <li>{@code chooseAll}, {@code confirmImport}: tick every file in the import list and import them</li>
 *     <li>{@code drop=<dir>}: drop every file of the directory onto the screen</li>
 *     <li>{@code delay=<ms>}: hold every cloud answer back this long</li>
 *     <li>{@code wait=<ticks>}: wait</li>
 *     <li>{@code screenshot=<name>}: save a screenshot as {@code screenshots/<name>.png}</li>
 *     <li>{@code waitWorld}: wait until the player is in a world (see {@code HEAVYMATICA_QUICKPLAY})</li>
 *     <li>{@code pressLibraryKey}: press the library key through Minecraft's key handling</li>
 *     <li>{@code look=<yaw>,<pitch>}: turn the player's camera</li>
 *     <li>{@code command=<command>}: run a command on the singleplayer server, for example
 *     {@code item replace entity @p weapon.mainhand with minecraft:stick}</li>
 *     <li>{@code hotbar=<0-8>}: select a hotbar slot</li>
 *     <li>{@code press=<key mapping>}, {@code down=<key mapping>}, {@code up=<key mapping>}: press, hold or release a key
 *     mapping by name (for example {@code key.use}, {@code key.heavymatica.rotate}) through Minecraft's key handling</li>
 *     <li>{@code logPlacements}: log the placements, the selection and the ghost mesh count</li>
 *     <li>{@code selectPlacement=<name>}: select the first placement with that name</li>
 *     <li>{@code logComparison}: log the selected placement's comparison: progress, totals, slowest scan tick</li>
 *     <li>{@code logFinish}: log the placement count, the selected placement and whether a finished one can be brought back</li>
 *     <li>{@code bringBack}: bring the latest finished placement back</li>
 *     <li>{@code logCell=<x>,<y>,<z>}: log the selected placement's comparison state at a world position</li>
 *     <li>{@code logMaterials}: log the selected placement's material list, counting the player's items again, the numbers
 *     and finishing counts per planned block, and the rows of an open materials screen</li>
 *     <li>{@code logOverlay}: log the placement overlay's lines as text</li>
 *     <li>{@code logTarget}: log the ghost under the crosshair: placement, position, planned block, state, found block,
 *     distance and whether it is in reach, or none</li>
 *     <li>{@code logHud}: log the targeting HUD's lines as text, or why it is hidden</li>
 *     <li>{@code logInventory}: log the selected slot and the items of the hotbar and the main inventory</li>
 *     <li>{@code logScreen}, {@code closeScreen}: log which screen is open, close it</li>
 *     <li>{@code controls}: open the key bindings screen; {@code scroll=<rows>}: scroll the open screen down</li>
 *     <li>{@code hideGui=<true|false>}: hide the HUD like F1</li>
 *     <li>{@code logButton=<translation key>}: log whether the button with that label is visible and active</li>
 *     <li>{@code checkTransform}: compare the placement mapping with vanilla's structure template transform</li>
 *     <li>{@code logConflicts}: log the bindings the controls screen marks as colliding with Heavymatica's keys</li>
 *     <li>{@code wheelPoint=<entry>[@<n>]}, {@code wheelCentre}: move the mouse cursor onto a segment of the open wheel (for
 *     example {@code REMOVE}, or {@code OPACITY_STEP@3} for the fourth opacity step) or into its centre</li>
 *     <li>{@code wheelRelease}, {@code wheelKey}, {@code wheelClick}: send the wheel key's release or press, or a left click at the
 *     cursor, to the open wheel; {@code escape}: send Esc to the open screen</li>
 *     <li>{@code logWheel}: log the open wheel's page, label, segments and hovered segment</li>
 *     <li>{@code logOpacity}: log the ghost opacity and the wheel's opacity steps</li>
 *     <li>{@code holdMovement=<key mapping>}, {@code releaseMovement=<key mapping>}: hold or let go of a movement key as the
 *     open wheel reads it (for example {@code key.forward})</li>
 *     <li>{@code logPlayer}: log the player's position, yaw and pitch</li>
 *     <li>{@code quit}: stop the game</li>
 * </ul>
 */
public final class DevScreens {

    private static final Deque<String> STEPS = new ArrayDeque<>();
    private static int waiting;
    private static boolean started;
    private static double[] cursor = {0, 0};

    private DevScreens() {
    }

    public static void registerIfRequested() {
        String script = System.getProperty("heavymatica.dev.screen");
        if (script != null && !script.isBlank()) {
            Arrays.stream(script.split(";")).map(String::trim).filter(step -> !step.isEmpty()).forEach(STEPS::add);
            Heavymatica.LOGGER.info("Dev screen script: {}", STEPS);
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
        }
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        // Start once loading is over: on a screen, or already in a world (quick play may skip every screen).
        if (!started && (minecraft.getOverlay() != null || minecraft.screen == null && minecraft.player == null)) {
            return;
        }
        started = true;
        if (waiting > 0) {
            waiting--;
            return;
        }
        String step = STEPS.poll();
        if (step == null) {
            return;
        }
        Heavymatica.LOGGER.info("Dev screen step: {}", step);
        try {
            run(minecraft, step);
        } catch (RuntimeException e) {
            Heavymatica.LOGGER.error("Dev screen step {} failed", step, e);
        }
    }

    private static void run(Minecraft minecraft, String step) {
        String argument = step.contains("=") ? step.substring(step.indexOf('=') + 1) : "";
        switch (step.contains("=") ? step.substring(0, step.indexOf('=')) : step) {
            case "signin" -> HeavymaticaClient.account().signIn().whenComplete((ignored, failure) -> {
                Heavymatica.LOGGER.info("Dev sign-in {}", failure == null ? "succeeded" : "failed: " + failure);
                HeavymaticaClient.library().refresh();
                HeavymaticaClient.account().refreshAccount().exceptionally(error -> null);
            });
            case "import" -> importAll(Path.of(argument));
            case "library" -> minecraft.setScreen(HeavymaticaClient.libraryScreen());
            case "select" -> {
                if (minecraft.screen instanceof LibraryScreen screen) {
                    screen.selectFirstForDevelopment();
                }
            }
            case "wait" -> waiting = Integer.parseInt(argument);
            case "search" -> {
                if (minecraft.screen instanceof LibraryScreen screen) {
                    screen.setFocused(screen.searchForDevelopment());
                    argument.codePoints().forEach(codepoint -> screen.charTyped(new CharacterEvent(codepoint)));
                }
            }
            case "clickSidebar" -> {
                if (minecraft.screen instanceof LibraryScreen screen) {
                    click(screen, screen.sidebarPointForDevelopment(argument));
                }
            }
            case "openFolder" -> {
                if (minecraft.screen instanceof LibraryScreen screen) {
                    screen.openFolderForDevelopment(argument);
                }
            }
            case "clickCard" -> {
                if (minecraft.screen instanceof LibraryScreen screen) {
                    click(screen, screen.cardPointForDevelopment(Integer.parseInt(argument)));
                }
            }
            case "clickVersion" -> {
                if (minecraft.screen instanceof LibraryScreen screen) {
                    click(screen, screen.versionPointForDevelopment(Integer.parseInt(argument)));
                }
            }
            case "clickButton" -> clickButton(minecraft.screen, Component.translatable(argument).getString());
            case "waitWorld" -> {
                if (minecraft.player == null) {
                    STEPS.addFirst(step);
                    waiting = 20;
                } else {
                    Heavymatica.LOGGER.info("Dev: in a world on {}", minecraft.getCurrentServer() == null ? "singleplayer" : minecraft.getCurrentServer().ip);
                }
            }
            case "pressLibraryKey" -> KeyMapping.click(HeavymaticaClient.OPEN_LIBRARY.getKey());
            case "command" -> {
                var server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), argument));
                }
            }
            case "hotbar" -> minecraft.player.getInventory().setSelectedSlot(Integer.parseInt(argument));
            case "press" -> KeyMapping.click(mapping(minecraft, argument).getKey());
            case "down" -> KeyMapping.set(mapping(minecraft, argument).getKey(), true);
            case "up" -> KeyMapping.set(mapping(minecraft, argument).getKey(), false);
            case "logPlacements" -> logPlacements();
            case "logComparison" -> logComparison();
            case "logFinish" -> logFinish();
            case "bringBack" -> {
                PlacementSession session = HeavymaticaClient.placements();
                if (session != null) {
                    session.bringBack();
                }
            }
            case "logMaterials" -> logMaterials(minecraft);
            case "logOverlay" -> {
                PlacementSession session = HeavymaticaClient.placements();
                if (session != null) {
                    Heavymatica.LOGGER.info("Dev overlay: {}", String.join(" | ", PlacementOverlay.textForDevelopment(minecraft, session)));
                }
            }
            case "logTarget" -> logTarget();
            case "logHud" -> Heavymatica.LOGGER.info("Dev HUD: {}", TargetHud.textForDevelopment(minecraft, HeavymaticaClient.placements(),
                    HeavymaticaClient.targeting()));
            case "logInventory" -> logInventory(minecraft);
            case "selectPlacement" -> {
                PlacementSession session = HeavymaticaClient.placements();
                if (session != null) {
                    session.placements().all().stream().filter(placement -> placement.name().equals(argument)).findFirst()
                            .ifPresent(placement -> session.placements().select(placement.id()));
                }
            }
            case "logCell" -> {
                String[] parts = argument.split(",");
                logCell(new net.minecraft.core.BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            }
            case "logScreen" -> Heavymatica.LOGGER.info("Dev screen: {}", minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName());
            case "closeScreen" -> minecraft.setScreen(null);
            case "checkTransform" -> checkTransform();
            case "logButton" -> {
                String label = Component.translatable(argument).getString();
                minecraft.screen.children().stream()
                        .filter(child -> child instanceof AbstractWidget widget && widget.getMessage().getString().equals(label))
                        .map(child -> (AbstractWidget) child)
                        .forEach(widget -> Heavymatica.LOGGER.info("Dev button {}: visible {}, active {}", label, widget.visible, widget.active));
            }
            case "hideGui" -> minecraft.options.hideGui = Boolean.parseBoolean(argument);
            case "logConflicts" -> {
                for (KeyMapping mine : minecraft.options.keyMappings) {
                    if (!mine.getName().startsWith("key.heavymatica.")) {
                        continue;
                    }
                    for (KeyMapping other : minecraft.options.keyMappings) {
                        if (other != mine && mine.same(other) || other.hasKeyModifierConflict(mine)) {
                            Heavymatica.LOGGER.info("Dev conflict: {} ({}, {}) with {} ({}, {}): same {}, modifier {}, contexts {} / {}, modifiers {} / {}",
                                    mine.getName(), mine.getKey().getName(), mine.getKeyConflictContext(), other.getName(), other.getKey().getName(),
                                    other.getKeyConflictContext(), mine.same(other), other.hasKeyModifierConflict(mine),
                                    mine.getKeyConflictContext().conflicts(other.getKeyConflictContext()),
                                    other.getKeyConflictContext().conflicts(mine.getKeyConflictContext()), mine.getKeyModifier(), other.getKeyModifier());
                        }
                    }
                }
            }
            case "controls" -> minecraft.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(null, minecraft.options));
            case "scroll" -> minecraft.screen.mouseScrolled(minecraft.screen.width / 2.0, minecraft.screen.height / 2.0, 0, -Double.parseDouble(argument));
            case "look" -> {
                String[] angles = argument.split(",");
                minecraft.player.setYRot(Float.parseFloat(angles[0]));
                minecraft.player.setXRot(Float.parseFloat(angles[1]));
            }
            case "type" -> argument.codePoints().forEach(codepoint -> minecraft.screen.charTyped(new CharacterEvent(codepoint)));
            case "chooseAll" -> {
                if (minecraft.screen instanceof ImportScreen screen) {
                    screen.chooseAll();
                }
            }
            case "confirmImport" -> {
                if (minecraft.screen instanceof ImportScreen screen) {
                    screen.confirmForDevelopment();
                }
            }
            case "drop" -> {
                try (Stream<Path> files = Files.list(Path.of(argument))) {
                    minecraft.screen.onFilesDrop(files.filter(Files::isRegularFile).sorted().toList());
                } catch (IOException e) {
                    Heavymatica.LOGGER.error("Dev drop failed", e);
                }
            }
            case "delay" -> CloudClient.developmentDelay(Long.parseLong(argument));
            case "screenshot" -> Screenshot.grab(minecraft.gameDirectory, argument + ".png", minecraft.getMainRenderTarget(), 1,
                    message -> Heavymatica.LOGGER.info("Dev screenshot: {}", message.getString()));
            case "wheelPoint" -> {
                if (minecraft.screen instanceof RadialMenuScreen wheel) {
                    String[] parts = argument.split("@", 2);
                    RadialMenu.Entry entry = RadialMenu.Entry.valueOf(parts[0]);
                    int nth = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    List<RadialMenu.Segment> segments = wheel.menu().segments();
                    int index = -1;
                    for (int i = 0, seen = 0; i < segments.size() && index < 0; i++) {
                        if (segments.get(i).entry() == entry && seen++ == nth) {
                            index = i;
                        }
                    }
                    if (index < 0) {
                        Heavymatica.LOGGER.warn("Dev wheel: no segment {}", argument);
                    } else {
                        moveCursor(minecraft, wheel.segmentPoint(index));
                    }
                }
            }
            case "wheelCentre" -> {
                if (minecraft.screen != null) {
                    moveCursor(minecraft, new double[] {minecraft.screen.width / 2.0, minecraft.screen.height / 2.0});
                }
            }
            case "wheelRelease" -> {
                if (minecraft.screen instanceof RadialMenuScreen wheel) {
                    wheel.keyReleased(new KeyEvent(RemoteKeys.WHEEL.getKey().getValue(), 0, 0));
                }
            }
            case "wheelKey" -> {
                if (minecraft.screen instanceof RadialMenuScreen wheel) {
                    wheel.keyPressed(new KeyEvent(RemoteKeys.WHEEL.getKey().getValue(), 0, 0));
                }
            }
            case "escape" -> {
                if (minecraft.screen != null) {
                    minecraft.screen.keyPressed(new KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0));
                }
            }
            case "wheelClick" -> {
                if (minecraft.screen != null) {
                    click(minecraft.screen, cursor);
                }
            }
            case "logWheel" -> {
                if (minecraft.screen instanceof RadialMenuScreen wheel) {
                    RadialMenu.Label label = wheel.menu().label();
                    Heavymatica.LOGGER.info("Dev wheel: page {}, label {}, segments {}, hovered {}", wheel.menu().page(),
                            Component.translatable(label.key(), label.name()).getString(), wheel.menu().segments(), wheel.hovered());
                } else {
                    Heavymatica.LOGGER.info("Dev wheel: closed");
                }
            }
            case "logOpacity" -> Heavymatica.LOGGER.info("Dev opacity: {}, steps {}", HeavymaticaConfig.ghostOpacity(), HeavymaticaConfig.ghostOpacitySteps());
            case "holdMovement" -> RadialMenuScreen.holdForDevelopment(argument, true);
            case "releaseMovement" -> RadialMenuScreen.holdForDevelopment(argument, false);
            case "logPlayer" -> Heavymatica.LOGGER.info("Dev player: at {}, yaw {}, pitch {}, input {} {}", minecraft.player.position(), minecraft.player.getYRot(),
                    minecraft.player.getXRot(), minecraft.player.input.getClass().getSimpleName(), minecraft.player.input.keyPresses);
            case "logSpecialGeometry" -> logSpecialGeometry();
            case "capture" -> {
                CaptureController capture = HeavymaticaClient.capture();
                switch (argument) {
                    case "start" -> capture.start();
                    case "capture" -> capture.capture();
                    case "swap" -> capture.swap();
                    case "reset" -> capture.reset();
                    case "cancel" -> capture.cancel();
                    default -> Heavymatica.LOGGER.warn("Unknown capture command {}", argument);
                }
            }
            case "shift" -> RemoteInput.holdShiftForDevelopment(Boolean.parseBoolean(argument));
            case "gameScroll" -> gameScroll(minecraft, Double.parseDouble(argument));
            case "logCapture" -> logCapture(minecraft);
            case "logLocal" -> logLocal();
            case "logEntries" -> HeavymaticaClient.library().entries().forEach(entry -> Heavymatica.LOGGER.info(
                    "Dev entry: {} {} {} v{} thumbnail {} cached {}", entry.name(), entry.kind(), entry.format(), entry.version(), entry.hasThumbnail(),
                    HeavymaticaClient.library().cached(entry.sha256())));
            case "alignSelected" -> {
                String[] parts = argument.split(",");
                alignSelected(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
            case "lockSelected" -> HeavymaticaClient.placements().controller().lock();
            case "quit" -> minecraft.stop();
            default -> Heavymatica.LOGGER.warn("Unknown dev screen step {}", step);
        }
    }

    /** Scrolls through the mouse handler's own scroll callback, as the mouse wheel does. */
    private static void gameScroll(Minecraft minecraft, double amount) {
        try {
            java.lang.reflect.Method onScroll = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onScroll", long.class, double.class, double.class);
            onScroll.setAccessible(true);
            onScroll.invoke(minecraft.mouseHandler, minecraft.getWindow().handle(), 0.0, amount);
        } catch (ReflectiveOperationException e) {
            Heavymatica.LOGGER.error("Dev scroll failed", e);
        }
    }

    private static void logCapture(Minecraft minecraft) {
        CaptureController capture = HeavymaticaClient.capture();
        CaptureSession session = capture.session();
        Heavymatica.LOGGER.info("Dev capture: on {}, corners {}, first {}, second {}, second active {}, bounds {}, progress {}, hotbar slot {}", session.on(),
                session.box().map(CaptureBox::corners).orElse(0), session.box().flatMap(CaptureBox::first).orElse(null),
                session.box().flatMap(CaptureBox::second).orElse(null), session.box().map(CaptureBox::secondActive).orElse(false),
                session.box().flatMap(CaptureBox::bounds).orElse(null), session.progress().orElse(null),
                minecraft.player == null ? -1 : minecraft.player.getInventory().getSelectedSlot());
    }

    private static void logLocal() {
        LocalLibrary local = HeavymaticaClient.library().local();
        for (LocalLibrary.LocalEntry entry : local.entries()) {
            Heavymatica.LOGGER.info("Dev local: {} {} {} x {} x {} blocks {} file {}", entry.name(), entry.format(), entry.width(), entry.height(), entry.length(),
                    entry.blockTotal(), local.file(entry).getFileName());
        }
        local.entries().stream().max(java.util.Comparator.comparing(LocalLibrary.LocalEntry::importedAt)).ifPresent(entry -> {
            SchematicModel model = SchematicReader.read(local.file(entry));
            Heavymatica.LOGGER.info("Dev local newest {}: data version {}, palette {}", entry.name(), model.dataVersion(), model.palette());
            model.blockEntities().forEach((pos, tag) -> Heavymatica.LOGGER.info("Dev local block entity at {}: {} keys {} items {} front text {}", pos,
                    tag.string("id"), tag.entries().keySet(), tag.list("Items") == null ? 0 : tag.list("Items").items().size(), tag.get("front_text")));
        });
    }

    /** Moves the selected placement until its box starts at the given corner. */
    private static void alignSelected(int x, int y, int z) {
        PlacementSession session = HeavymaticaClient.placements();
        Placement placement = session.placements().selected().orElse(null);
        if (placement == null) {
            return;
        }
        PlacementTransform.Bounds bounds = PlacementTransform.bounds(placement);
        // Yaw -90 faces east (+x), 0 faces south (+z).
        for (int i = bounds.minX(); i < x; i++) {
            session.controller().move(PlacementTransform.Relative.FORWARD, -90, false);
        }
        for (int i = bounds.minX(); i > x; i--) {
            session.controller().move(PlacementTransform.Relative.BACK, -90, false);
        }
        for (int i = bounds.minZ(); i < z; i++) {
            session.controller().move(PlacementTransform.Relative.FORWARD, 0, false);
        }
        for (int i = bounds.minZ(); i > z; i--) {
            session.controller().move(PlacementTransform.Relative.BACK, 0, false);
        }
        for (int i = bounds.minY(); i < y; i++) {
            session.controller().vertical(1, false);
        }
        for (int i = bounds.minY(); i > y; i--) {
            session.controller().vertical(-1, false);
        }
    }

    private static void logSpecialGeometry() {
        java.util.List<net.minecraft.world.level.block.state.BlockState> states = java.util.List.of(
                net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD),
                net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT),
                net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT),
                net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT),
                net.minecraft.world.level.block.Blocks.OAK_WALL_SIGN.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.SKELETON_SKULL.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        io.github.dirnei.mc.heavymatica.blocks.SpecialGeometry special = io.github.dirnei.mc.heavymatica.blocks.SpecialGeometry.get();
        special.prepare(states);
        for (net.minecraft.world.level.block.state.BlockState state : states) {
            java.util.List<String> parts = special.summary(state);
            Heavymatica.LOGGER.info("Dev special geometry {}: {}", state, parts.isEmpty() ? "none" : String.join("; ", parts));
        }
    }

    private static KeyMapping mapping(Minecraft minecraft, String name) {
        return Arrays.stream(minecraft.options.keyMappings).filter(mapping -> mapping.getName().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No key mapping " + name));
    }

    private static void logPlacements() {
        PlacementSession session = HeavymaticaClient.placements();
        if (session == null) {
            return;
        }
        Heavymatica.LOGGER.info("Dev placements: {} placements, selected {}, {} ghost meshes, remote held {}, last frame {}", session.placements().all().size(),
                session.placements().selected().map(Placement::name).orElse("none"), session.meshCount(), Remotes.held(), session.lastFrame());
        for (Placement placement : session.placements().all()) {
            Heavymatica.LOGGER.info("Dev placement: {} {} {} bounds {} rotation {} mirror {} in {} layers {} visible {} in-hand {}{}", placement.name(),
                    placement.source(), placement.state(), PlacementTransform.bounds(placement), placement.rotation(), placement.mirror(),
                    placement.dimension(), placement.layers(), Layers.visible(placement, PlacementSession.feetLayer(placement)), placement.inHand(), session.unavailable(placement) ? " (unavailable)" : "");
        }
    }

    private static void logTarget() {
        GhostTargeting targeting = HeavymaticaClient.targeting();
        GhostTargeting.GhostTarget target = targeting == null ? null : targeting.fresh();
        if (target == null) {
            Heavymatica.LOGGER.info("Dev target: none");
            return;
        }
        Heavymatica.LOGGER.info("Dev target: {} at {} planned {} state {} found {} finish {} distance {} in reach {}", target.placement().name(),
                target.pos().toShortString(), target.planned(), target.state(), target.found(), target.finish(),
                String.format(Locale.ROOT, "%.2f", target.distance()), target.inReach());
    }

    private static void logInventory(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        Inventory inventory = minecraft.player.getInventory();
        StringBuilder hotbar = new StringBuilder();
        StringBuilder main = new StringBuilder();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                (slot < Inventory.getSelectionSize() ? hotbar : main)
                        .append(' ').append(slot).append('=').append(stack.getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
        }
        Heavymatica.LOGGER.info("Dev inventory: selected {}, hotbar{}, main{}", inventory.getSelectedSlot(), hotbar, main);
    }

    private static void logFinish() {
        PlacementSession session = HeavymaticaClient.placements();
        if (session == null) {
            return;
        }
        Heavymatica.LOGGER.info("Dev finish: {} placements, selected {}, can bring back {}", session.placements().all().size(),
                session.placements().selected().map(placement -> placement.name() + " " + placement.state()).orElse("none"), session.canBringBack());
    }

    private static void logComparison() {
        PlacementSession session = HeavymaticaClient.placements();
        if (session == null) {
            return;
        }
        Placement placement = session.placements().selected().orElse(null);
        WorldComparison comparison = placement == null ? null : session.comparison(placement);
        if (comparison == null) {
            Heavymatica.LOGGER.info("Dev comparison: none ({} comparisons), {} fps", session.comparisonCount(), Minecraft.getInstance().getFps());
            return;
        }
        ComparisonState state = comparison.state();
        StringBuilder totals = new StringBuilder();
        for (CellState cell : CellState.values()) {
            totals.append(' ').append(cell).append('=').append(state.total(cell));
        }
        Heavymatica.LOGGER.info("Dev comparison: {} comparisons, scanning {}, matching {} of {},{}, slowest tick {} us, {} fps", session.comparisonCount(),
                state.scanning(), state.matching(0, state.sizeY() - 1), state.counted(0, state.sizeY() - 1), totals,
                session.takeSlowestComparisonTick() / 1000, Minecraft.getInstance().getFps());
    }

    private static void logMaterials(Minecraft minecraft) {
        PlacementSession session = HeavymaticaClient.placements();
        Placement placement = session == null ? null : session.placements().selected().orElse(null);
        if (session == null || placement == null) {
            Heavymatica.LOGGER.info("Dev materials: no placement");
            return;
        }
        session.refreshMaterials();
        MaterialList<Item, Block> list = session.materials(placement);
        if (list == null) {
            Heavymatica.LOGGER.info("Dev materials: none ({})", placement.state());
        } else {
            Heavymatica.LOGGER.info("Dev materials: visible {}, rows {}, can't carry {}", Layers.visible(placement, PlacementSession.feetLayer(placement)),
                    list.rows().stream().map(row -> BuiltInRegistries.ITEM.getKey(row.item()) + " " + row.needed() + "/" + row.had() + "/" + row.shortage())
                            .toList(),
                    list.cantCarry().stream().map(entry -> BuiltInRegistries.BLOCK.getKey(entry.block()) + " " + entry.count()).toList());
            WorldComparison comparison = session.comparison(placement);
            if (comparison != null) {
                Heavymatica.LOGGER.info("Dev materials planned (needed/given/short, left, finishing): {}", comparison.plannedBlocks().stream()
                        .map(block -> {
                            MaterialList.Planned<Item> planned = list.planned(block);
                            return BuiltInRegistries.BLOCK.getKey(block) + (planned == null ? " -" : " " + BuiltInRegistries.ITEM.getKey(planned.item()) + " "
                                    + planned.needed() + "/" + planned.given() + "/" + planned.shortage() + ", left " + planned.left()
                                    + (planned.finish() == null ? "" : ", " + planned.finishing() + " " + planned.finish()));
                        })
                        .toList());
            }
        }
        if (minecraft.screen instanceof MaterialsScreen screen) {
            Heavymatica.LOGGER.info("Dev materials screen rows: {}, can't carry {}", screen.rowsForDevelopment().stream()
                    .map(row -> row.block() + " " + row.total() + (row.target() == null ? "" : " -> " + row.target())
                            + (row.numbers() == null ? "" : " " + row.numbers().needed() + "/" + row.numbers().had() + "/" + row.numbers().shortage())
                            + (row.finish() == null || row.finishing() == 0 ? "" : " finish " + row.finishing() + " " + row.finish()))
                    .toList(), screen.cantCarryForDevelopment().stream().map(entry -> BuiltInRegistries.BLOCK.getKey(entry.block()) + " " + entry.count()).toList());
        }
    }

    private static void logCell(net.minecraft.core.BlockPos pos) {
        PlacementSession session = HeavymaticaClient.placements();
        Placement placement = session == null ? null : session.placements().selected().orElse(null);
        WorldComparison comparison = placement == null ? null : session.comparison(placement);
        if (comparison == null) {
            Heavymatica.LOGGER.info("Dev cell {}: no comparison", pos.toShortString());
            return;
        }
        CellState state = comparison.stateAt(pos);
        Heavymatica.LOGGER.info("Dev cell {}: {} expected {} found {}", pos.toShortString(), state,
                state == null ? "-" : comparison.expectedAt(pos), Minecraft.getInstance().level.getBlockState(pos));
    }

    /** Every rotation and mirror of a 4 x 7 footprint against {@code StructureTemplate.transform}, shifted into the box. */
    private static void checkTransform() {
        int width = 4;
        int length = 7;
        int mismatches = 0;
        int[] placed = new int[2];
        for (Placement.Turn turn : Placement.Turn.values()) {
            for (Placement.Flip flip : Placement.Flip.values()) {
                net.minecraft.world.level.block.Rotation rotation = net.minecraft.world.level.block.Rotation.values()[turn.ordinal()];
                net.minecraft.world.level.block.Mirror mirror = net.minecraft.world.level.block.Mirror.values()[flip.ordinal()];
                int minX = Integer.MAX_VALUE;
                int minZ = Integer.MAX_VALUE;
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < length; z++) {
                        net.minecraft.core.BlockPos moved = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
                                .transform(new net.minecraft.core.BlockPos(x, 0, z), mirror, rotation, net.minecraft.core.BlockPos.ZERO);
                        minX = Math.min(minX, moved.getX());
                        minZ = Math.min(minZ, moved.getZ());
                    }
                }
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < length; z++) {
                        net.minecraft.core.BlockPos moved = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
                                .transform(new net.minecraft.core.BlockPos(x, 0, z), mirror, rotation, net.minecraft.core.BlockPos.ZERO);
                        PlacementTransform.toPlaced(turn, flip, width, length, x, z, placed);
                        if (placed[0] != moved.getX() - minX || placed[1] != moved.getZ() - minZ) {
                            mismatches++;
                        }
                    }
                }
            }
        }
        net.minecraft.world.level.block.state.BlockState stairs = net.minecraft.world.level.block.Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.NORTH);
        Heavymatica.LOGGER.info("Dev transform check: {} mismatches; north stairs turned clockwise face {}", mismatches,
                stairs.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90).getValue(net.minecraft.world.level.block.StairBlock.FACING));
    }

    /**
     * Moves the real cursor to a point in GUI coordinates and tells the screen, since GLFW only reports the move to a
     * focused window.
     */
    private static void moveCursor(Minecraft minecraft, double[] point) {
        cursor = point;
        if (minecraft.screen != null) {
            minecraft.screen.mouseMoved(point[0], point[1]);
        }
        var window = minecraft.getWindow();
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(window.handle(), point[0] * window.getScreenWidth() / window.getGuiScaledWidth(),
                point[1] * window.getScreenHeight() / window.getGuiScaledHeight());
    }

    /** Clicks through the screen's normal input handling, like the mouse would. */
    private static void click(Screen screen, double[] point) {
        MouseButtonEvent event = new MouseButtonEvent(point[0], point[1], new MouseButtonInfo(0, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static void clickButton(@Nullable Screen screen, String label) {
        if (screen == null) {
            return;
        }
        screen.children().stream()
                .filter(child -> child instanceof AbstractWidget widget && widget.getMessage().getString().equals(label))
                .map(child -> (AbstractWidget) child)
                .findFirst()
                .ifPresentOrElse(widget -> click(screen, new double[] {widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0}),
                        () -> Heavymatica.LOGGER.warn("No button {} on {}", label, screen.getClass().getSimpleName()));
    }

    private static void importAll(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                try {
                    HeavymaticaClient.library().local().add(file);
                } catch (RuntimeException e) {
                    Heavymatica.LOGGER.info("Dev import skipped {}: {}", file.getFileName(), e.getMessage());
                }
            });
        } catch (IOException e) {
            Heavymatica.LOGGER.error("Dev import failed", e);
        }
    }
}
