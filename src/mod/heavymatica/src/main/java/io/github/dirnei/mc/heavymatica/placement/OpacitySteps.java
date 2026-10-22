package io.github.dirnei.mc.heavymatica.placement;

import java.util.List;
import java.util.Objects;

/** The ghost opacities the wheel offers. */
public final class OpacitySteps {

    public static final List<Double> DEFAULT = List.of(0.2, 0.4, 0.6, 0.8, 1.0);
    public static final double MIN = 0.05;
    public static final double MAX = 1.0;
    private static final int MIN_STEPS = 2;
    private static final int MAX_STEPS = 8;
    private static final double EPSILON = 0.001;

    private OpacitySteps() {
    }

    /** The configured values within range, without duplicates and sorted, or {@link #DEFAULT} unless 2 to 8 remain. */
    public static List<Double> sanitize(List<? extends Number> values) {
        List<Double> steps = values.stream()
                .filter(Objects::nonNull)
                .map(Number::doubleValue)
                .filter(value -> value >= MIN && value <= MAX)
                .distinct()
                .sorted()
                .toList();
        return steps.size() < MIN_STEPS || steps.size() > MAX_STEPS ? DEFAULT : steps;
    }

    public static boolean matches(double step, double current) {
        return Math.abs(step - current) < EPSILON;
    }

    public static int percent(double step) {
        return (int) Math.round(step * 100);
    }
}
