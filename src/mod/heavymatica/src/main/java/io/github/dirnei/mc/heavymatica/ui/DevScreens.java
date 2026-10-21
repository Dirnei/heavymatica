package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaClient;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.placement.ComparisonState;
import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform;
import io.github.dirnei.mc.heavymatica.placement.WorldComparison;
import io.github.dirnei.mc.heavymatica.remote.Remotes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.stream.Stream;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import io.github.dirnei.mc.heavymatica.account.CloudClient;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
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
 *     <li>{@code clickSidebar=<label>}, {@code clickCard=<index>}, {@code clickVersion=<index>}: click there</li>
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
 *     <li>{@code logCell=<x>,<y>,<z>}: log the selected placement's comparison state at a world position</li>
 *     <li>{@code logScreen}, {@code closeScreen}: log which screen is open, close it</li>
 *     <li>{@code controls}: open the key bindings screen; {@code scroll=<rows>}: scroll the open screen down</li>
 *     <li>{@code hideGui=<true|false>}: hide the HUD like F1</li>
 *     <li>{@code logButton=<translation key>}: log whether the button with that label is visible and active</li>
 *     <li>{@code checkTransform}: compare the placement mapping with vanilla's structure template transform</li>
 *     <li>{@code logConflicts}: log the bindings the controls screen marks as colliding with Heavymatica's keys</li>
 *     <li>{@code quit}: stop the game</li>
 * </ul>
 */
public final class DevScreens {

    private static final Deque<String> STEPS = new ArrayDeque<>();
    private static int waiting;
    private static boolean started;

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
            case "quit" -> minecraft.stop();
            default -> Heavymatica.LOGGER.warn("Unknown dev screen step {}", step);
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
