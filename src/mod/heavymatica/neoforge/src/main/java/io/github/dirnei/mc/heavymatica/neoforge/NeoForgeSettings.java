package io.github.dirnei.mc.heavymatica.neoforge;

import io.github.dirnei.mc.heavymatica.settings.Setting;
import io.github.dirnei.mc.heavymatica.settings.SettingsBackend;
import io.github.dirnei.mc.heavymatica.settings.SettingsSchema;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.neoforged.neoforge.common.ModConfigSpec;

/** A {@link SettingsSchema} as NeoForge's config: the same builder calls, so NeoForge writes, checks and reloads the file. */
public final class NeoForgeSettings implements SettingsBackend {

    private final ModConfigSpec spec;
    private final Map<Setting<?>, ModConfigSpec.ConfigValue<?>> values;

    private NeoForgeSettings(ModConfigSpec spec, Map<Setting<?>, ModConfigSpec.ConfigValue<?>> values) {
        this.spec = spec;
        this.values = values;
    }

    /** Builds the spec for the schema and binds the schema to it. Register the spec as a config afterwards. */
    public static ModConfigSpec bind(SettingsSchema schema) {
        NeoForgeSettings settings = build(schema);
        schema.bind(settings);
        return settings.spec;
    }

    /** Builds the spec without binding the schema. */
    public static ModConfigSpec spec(SettingsSchema schema) {
        return build(schema).spec;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static NeoForgeSettings build(SettingsSchema schema) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        Map<Setting<?>, ModConfigSpec.ConfigValue<?>> values = new IdentityHashMap<>();
        for (SettingsSchema.Operation operation : schema.operations()) {
            switch (operation) {
                case SettingsSchema.Push push -> {
                    if (push.comment() != null) {
                        builder.comment(push.comment());
                    }
                    builder.push(push.name());
                }
                case SettingsSchema.Pop pop -> builder.pop();
                case SettingsSchema.Define define -> {
                    Setting<?> setting = define.setting();
                    String[] comment = setting.comment();
                    if (comment != null) {
                        builder.comment(comment);
                    }
                    String key = setting.key();
                    ModConfigSpec.ConfigValue<?> value = switch (setting.kind()) {
                        case BOOLEAN -> builder.define(key, (boolean) (Boolean) setting.defaultValue());
                        case INT -> builder.defineInRange(key, (int) (Integer) setting.defaultValue(), Objects.requireNonNull(setting.min()).intValue(),
                                Objects.requireNonNull(setting.max()).intValue());
                        case DOUBLE -> builder.defineInRange(key, (double) (Double) setting.defaultValue(),
                                Objects.requireNonNull(setting.min()).doubleValue(), Objects.requireNonNull(setting.max()).doubleValue());
                        case ENUM -> builder.defineEnum(key, (Enum) setting.defaultValue());
                        case STRING -> builder.define(key, (String) setting.defaultValue(), Objects.requireNonNull(setting.check()));
                        case NUMBER_LIST -> builder.defineList(key, (List<? extends Number>) setting.defaultValue(),
                                (Supplier<Number>) Objects.requireNonNull(setting.newElement()), Objects.requireNonNull(setting.check()));
                    };
                    values.put(setting, value);
                }
            }
        }
        return new NeoForgeSettings(builder.build(), values);
    }

    @SuppressWarnings("unchecked")
    private <T> ModConfigSpec.ConfigValue<T> value(Setting<T> setting) {
        return (ModConfigSpec.ConfigValue<T>) Objects.requireNonNull(values.get(setting), setting.dottedPath());
    }

    @Override
    public <T> T get(Setting<T> setting) {
        return value(setting).get();
    }

    @Override
    public <T> void set(Setting<T> setting, T value) {
        value(setting).set(value);
    }

    @Override
    public void save(Setting<?> setting) {
        value(setting).save();
    }
}
