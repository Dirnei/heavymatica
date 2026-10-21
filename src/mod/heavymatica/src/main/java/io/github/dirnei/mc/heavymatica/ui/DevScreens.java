package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaClient;
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
        if (!started && (minecraft.getOverlay() != null || minecraft.screen == null)) {
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
