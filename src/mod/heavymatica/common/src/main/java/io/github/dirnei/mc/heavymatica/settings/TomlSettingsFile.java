package io.github.dirnei.mc.heavymatica.settings;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlWriter;
import io.github.dirnei.mc.heavymatica.Heavymatica;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/**
 * A settings file of our own, for loaders without a config system. It writes the same TOML as NeoForge does for the
 * same {@link SettingsSchema}: same order, same comments, including the default, range and allowed-values lines
 * NeoForge adds. The file is read once; changes made to it while the game runs are not picked up.
 */
public final class TomlSettingsFile implements SettingsBackend {

    private final SettingsSchema schema;
    private final Path file;
    private final Map<Setting<?>, Object> values = new IdentityHashMap<>();

    private TomlSettingsFile(SettingsSchema schema, Path file) {
        this.schema = schema;
        this.file = file;
    }

    /**
     * Reads the file, falls back to the default for each missing or invalid value, and writes the file back when it
     * differs from what this would write. An unreadable file is kept as {@code <name>.bak}.
     */
    public static TomlSettingsFile load(SettingsSchema schema, Path file) {
        TomlSettingsFile settings = new TomlSettingsFile(schema, file);
        UnmodifiableConfig stored = settings.read();
        for (Setting<?> setting : schema.settings()) {
            Object raw = stored == null ? null : stored.get(setting.path());
            Object accepted = setting.accept(raw);
            if (accepted == null) {
                if (raw != null) {
                    Heavymatica.LOGGER.warn("Setting {} in {} is not valid ({}); using the default", setting.dottedPath(), file.getFileName(), raw);
                }
                accepted = setting.defaultValue();
            }
            settings.values.put(setting, accepted);
        }
        settings.writeIfChanged();
        return settings;
    }

    /** Loads the file and binds the schema to it. */
    public static TomlSettingsFile bind(SettingsSchema schema, Path file) {
        TomlSettingsFile settings = load(schema, file);
        schema.bind(settings);
        return settings;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(Setting<T> setting) {
        return (T) values.get(setting);
    }

    @Override
    public <T> void set(Setting<T> setting, T value) {
        values.put(setting, value);
    }

    @Override
    public void save(Setting<?> setting) {
        write();
    }

    /** The file's text for the current values. Line breaks are the platform's, as NightConfig writes them. */
    public String text() {
        StringWriter out = new StringWriter();
        new TomlWriter().write(config(), out);
        return out.toString();
    }

    private @Nullable UnmodifiableConfig read() {
        if (!Files.exists(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return TomlFormat.instance().createParser().parse(reader);
        } catch (IOException | ParsingException e) {
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            Heavymatica.LOGGER.warn("Could not read {}; keeping it as {} and starting from the defaults", file, backup.getFileName(), e);
            try {
                Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFailed) {
                Heavymatica.LOGGER.warn("Could not keep {} as {}", file, backup.getFileName(), moveFailed);
            }
            return null;
        }
    }

    private void writeIfChanged() {
        try {
            String current = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
            if (!text().equals(current)) {
                write();
            }
        } catch (IOException e) {
            Heavymatica.LOGGER.warn("Could not read {}", file, e);
            write();
        }
    }

    private synchronized void write() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            new TomlWriter().write(config(), file, WritingMode.REPLACE_ATOMIC);
        } catch (IOException | RuntimeException e) {
            Heavymatica.LOGGER.error("Could not write {}", file, e);
        }
    }

    /** The values as NeoForge's config tracker builds them for writing: in schema order, with NeoForge's comments. */
    private CommentedConfig config() {
        CommentedConfig config = CommentedConfig.of(LinkedHashMap::new, InMemoryCommentedFormat.defaultInstance());
        List<String> stack = new ArrayList<>();
        for (SettingsSchema.Operation operation : schema.operations()) {
            switch (operation) {
                case SettingsSchema.Push push -> {
                    stack.add(push.name());
                    if (config.get(stack) == null) {
                        config.set(stack, config.createSubConfig());
                    }
                    if (push.comment() != null) {
                        config.setComment(stack, String.join("\n", push.comment()));
                    }
                }
                case SettingsSchema.Pop pop -> stack.removeLast();
                case SettingsSchema.Define define -> {
                    Setting<?> setting = define.setting();
                    config.set(setting.path(), stored(setting, values.get(setting)));
                    String comment = comment(setting);
                    if (comment != null) {
                        config.setComment(setting.path(), comment);
                    }
                }
            }
        }
        return config;
    }

    private static Object stored(Setting<?> setting, Object value) {
        return setting.kind() == Setting.Kind.ENUM ? ((Enum<?>) value).name() : value;
    }

    /** The comment NeoForge writes: the schema's lines, then its default and range, or the allowed values. */
    static @Nullable String comment(Setting<?> setting) {
        List<String> lines = new ArrayList<>();
        String[] own = setting.comment();
        if (own != null) {
            lines.addAll(Arrays.asList(own));
        }
        switch (setting.kind()) {
            case INT, DOUBLE -> {
                lines.add(" Default: " + setting.defaultValue());
                lines.add(" Range: " + setting.min() + " ~ " + setting.max());
            }
            case ENUM -> lines.add("Allowed Values: " + Arrays.stream(setting.enumClass().getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", ")));
            default -> {
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }
}
