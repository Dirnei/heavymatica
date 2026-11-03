package io.github.dirnei.mc.heavymatica.settings;

/** Where the values of a {@link SettingsSchema} live: the loader's config system or a file of our own. */
public interface SettingsBackend {

    <T> T get(Setting<T> setting);

    <T> void set(Setting<T> setting, T value);

    /** Writes the setting's current value to the file. */
    void save(Setting<?> setting);
}
