package io.github.dirnei.mc.heavymatica.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * A loader-free description of a settings file. It is built with the same calls, in the same order, as NeoForge's
 * config builder: a comment applies to the next push (the section's comment) or define (the setting's comment). Loader
 * code replays {@link #operations()} to build its own config, so every loader writes the same file.
 */
public final class SettingsSchema {

    /** One builder call. */
    public sealed interface Operation {
    }

    public record Push(String name, String @Nullable [] comment) implements Operation {
    }

    public record Pop() implements Operation {
    }

    public record Define(Setting<?> setting) implements Operation {
    }

    private final List<Operation> operations = new ArrayList<>();
    private final Map<String, Setting<?>> settings = new LinkedHashMap<>();
    private final List<String> stack = new ArrayList<>();
    private String @Nullable [] pendingComment;
    private @Nullable SettingsBackend backend;

    public SettingsSchema comment(String... lines) {
        pendingComment = lines.clone();
        return this;
    }

    public SettingsSchema push(String name) {
        operations.add(new Push(name, takeComment()));
        stack.add(name);
        return this;
    }

    public SettingsSchema pop() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("pop without push");
        }
        operations.add(new Pop());
        stack.removeLast();
        return this;
    }

    public Setting<Boolean> define(String key, boolean defaultValue) {
        return add(Setting.Kind.BOOLEAN, key, defaultValue, null, null, null, null);
    }

    public Setting<String> define(String key, String defaultValue, Predicate<Object> check) {
        return add(Setting.Kind.STRING, key, defaultValue, null, null, check, null);
    }

    public Setting<Integer> defineInRange(String key, int defaultValue, int min, int max) {
        return add(Setting.Kind.INT, key, defaultValue, min, max, null, null);
    }

    public Setting<Double> defineInRange(String key, double defaultValue, double min, double max) {
        return add(Setting.Kind.DOUBLE, key, defaultValue, min, max, null, null);
    }

    public <E extends Enum<E>> Setting<E> defineEnum(String key, E defaultValue) {
        return add(Setting.Kind.ENUM, key, defaultValue, null, null, null, null);
    }

    public Setting<List<? extends Number>> defineList(String key, List<? extends Number> defaultValue, Supplier<? extends Number> newElement,
            Predicate<Object> elementCheck) {
        return add(Setting.Kind.NUMBER_LIST, key, defaultValue, null, null, elementCheck, newElement);
    }

    private <T> Setting<T> add(Setting.Kind kind, String key, T defaultValue, @Nullable Number min, @Nullable Number max,
            @Nullable Predicate<Object> check, @Nullable Supplier<?> newElement) {
        List<String> path = new ArrayList<>(stack);
        path.add(key);
        Setting<T> setting = new Setting<>(this, kind, path, takeComment(), defaultValue, min, max, check, newElement);
        if (settings.putIfAbsent(setting.dottedPath(), setting) != null) {
            throw new IllegalStateException("Setting " + setting.dottedPath() + " is defined twice");
        }
        operations.add(new Define(setting));
        return setting;
    }

    private String @Nullable [] takeComment() {
        String[] comment = pendingComment;
        pendingComment = null;
        return comment;
    }

    /** The builder calls in order. */
    public List<Operation> operations() {
        if (!stack.isEmpty()) {
            throw new IllegalStateException("Sections still open: " + stack);
        }
        return Collections.unmodifiableList(operations);
    }

    /** Every setting in definition order. */
    public List<Setting<?>> settings() {
        return List.copyOf(settings.values());
    }

    /** The setting at a dotted path such as {@code ghost.opacity}, or null. */
    public @Nullable Setting<?> setting(String dottedPath) {
        return settings.get(dottedPath);
    }

    /** Gives the settings their values. Called once by the loader before any setting is read. */
    public void bind(SettingsBackend backend) {
        if (this.backend != null) {
            throw new IllegalStateException("Settings are bound already");
        }
        this.backend = backend;
    }

    SettingsBackend backend() {
        if (backend == null) {
            throw new IllegalStateException("Settings are read before the loader loaded them");
        }
        return backend;
    }
}
