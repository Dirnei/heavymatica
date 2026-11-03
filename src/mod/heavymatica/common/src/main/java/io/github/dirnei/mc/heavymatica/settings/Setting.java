package io.github.dirnei.mc.heavymatica.settings;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * One setting of a {@link SettingsSchema}: where it lives in the file, its comment, default and check. Its value comes
 * from the backend the schema is bound to.
 */
public final class Setting<T> {

    public enum Kind {
        BOOLEAN,
        INT,
        DOUBLE,
        ENUM,
        STRING,
        NUMBER_LIST,
    }

    private final SettingsSchema schema;
    private final Kind kind;
    private final List<String> path;
    private final String @Nullable [] comment;
    private final T defaultValue;
    private final @Nullable Number min;
    private final @Nullable Number max;
    private final @Nullable Predicate<Object> check;
    private final @Nullable Supplier<?> newElement;

    Setting(SettingsSchema schema, Kind kind, List<String> path, String @Nullable [] comment, T defaultValue, @Nullable Number min,
            @Nullable Number max, @Nullable Predicate<Object> check, @Nullable Supplier<?> newElement) {
        this.schema = schema;
        this.kind = kind;
        this.path = List.copyOf(path);
        this.comment = comment;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.check = check;
        this.newElement = newElement;
    }

    public T get() {
        return schema.backend().get(this);
    }

    /** Changes the value; {@link #save()} writes it to the file. */
    public void set(T value) {
        schema.backend().set(this, value);
    }

    public void save() {
        schema.backend().save(this);
    }

    public Kind kind() {
        return kind;
    }

    /** The keys from the top section down to the setting's own key. */
    public List<String> path() {
        return path;
    }

    /** The path joined with dots, for example {@code ghost.opacity}. */
    public String dottedPath() {
        return String.join(".", path);
    }

    public String key() {
        return path.getLast();
    }

    /** The comment lines given in the schema, or null. */
    public String @Nullable [] comment() {
        return comment == null ? null : comment.clone();
    }

    public T defaultValue() {
        return defaultValue;
    }

    public @Nullable Number min() {
        return min;
    }

    public @Nullable Number max() {
        return max;
    }

    /** The schema's own check of a string value or of a list element; null for the other kinds. */
    public @Nullable Predicate<Object> check() {
        return check;
    }

    /** What a new list element starts as; null for the other kinds. */
    public @Nullable Supplier<?> newElement() {
        return newElement;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends Enum<?>> enumClass() {
        return (Class<? extends Enum<?>>) ((Enum<?>) defaultValue).getDeclaringClass();
    }

    /**
     * The value as this setting stores it, or null when it is not a valid value: the right type (numbers of another
     * type are converted), within the range, a known enum name (ignoring case), passing the check. Invalid list
     * elements are dropped.
     */
    @SuppressWarnings("unchecked")
    public @Nullable T accept(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        return switch (kind) {
            case BOOLEAN -> raw instanceof Boolean ? (T) raw : null;
            case INT -> raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte
                    ? inRange(((Number) raw).longValue(), min.longValue(), max.longValue()) ? (T) Integer.valueOf(((Number) raw).intValue()) : null
                    : null;
            case DOUBLE -> raw instanceof Number number && inRange(number.doubleValue(), min.doubleValue(), max.doubleValue())
                    ? (T) Double.valueOf(number.doubleValue())
                    : null;
            case ENUM -> (T) enumValue(raw);
            case STRING -> raw instanceof String && check.test(raw) ? (T) raw : null;
            case NUMBER_LIST -> raw instanceof List<?> list ? (T) list.stream().filter(check).toList() : null;
        };
    }

    private @Nullable Enum<?> enumValue(Object raw) {
        if (enumClass().isInstance(raw)) {
            return (Enum<?>) raw;
        }
        if (!(raw instanceof String name)) {
            return null;
        }
        for (Enum<?> constant : enumClass().getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(name)) {
                return constant;
            }
        }
        return null;
    }

    private static boolean inRange(long value, long min, long max) {
        return value >= min && value <= max;
    }

    private static boolean inRange(double value, double min, double max) {
        return value >= min && value <= max;
    }

    @Override
    public String toString() {
        return dottedPath() + " (" + kind.name().toLowerCase(Locale.ROOT) + ")";
    }
}
