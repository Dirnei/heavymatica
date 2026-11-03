package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpacityStepsTest {

    @Test
    void defaultSteps() {
        assertEquals(List.of(0.2, 0.4, 0.6, 0.8, 1.0), OpacitySteps.DEFAULT);
        assertEquals(OpacitySteps.DEFAULT, OpacitySteps.sanitize(OpacitySteps.DEFAULT));
    }

    @Test
    void dropsOutOfRangeRemovesDuplicatesAndSorts() {
        assertEquals(List.of(0.3, 0.9), OpacitySteps.sanitize(List.of(0.9, 0.3, 1.5, 0.3)));
        assertEquals(List.of(0.05, 1.0), OpacitySteps.sanitize(List.of(1.0, 0.04, 0.05, -1.0, 1.01)));
    }

    @Test
    void dropsNonFiniteAndMissingValues() {
        assertEquals(List.of(0.5, 0.7), OpacitySteps.sanitize(Arrays.asList(0.5, Double.NaN, null, Double.POSITIVE_INFINITY, 0.7)));
    }

    @Test
    void acceptsIntegers() {
        assertEquals(List.of(0.5, 1.0), OpacitySteps.sanitize(List.of(1, 0.5)));
    }

    @Test
    void fallsBackWithTooFewSteps() {
        assertEquals(OpacitySteps.DEFAULT, OpacitySteps.sanitize(List.of(0.5, 2.0)));
        assertEquals(OpacitySteps.DEFAULT, OpacitySteps.sanitize(List.of(0.5, 0.5)));
        assertEquals(OpacitySteps.DEFAULT, OpacitySteps.sanitize(List.of()));
    }

    @Test
    void fallsBackWithTooManySteps() {
        assertEquals(OpacitySteps.DEFAULT, OpacitySteps.sanitize(List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9)));
        List<Double> eight = List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8);
        assertEquals(eight, OpacitySteps.sanitize(eight));
    }

    @Test
    void matchesWithinASmallEpsilon() {
        assertTrue(OpacitySteps.matches(0.4, 0.4F));
        assertTrue(OpacitySteps.matches(0.4, 0.4004));
        assertFalse(OpacitySteps.matches(0.4, 0.45));
        assertFalse(OpacitySteps.matches(0.4, 0.402));
    }

    @Test
    void percent() {
        assertEquals(40, OpacitySteps.percent(0.4));
        assertEquals(5, OpacitySteps.percent(0.05));
        assertEquals(100, OpacitySteps.percent(1.0));
        assertEquals(33, OpacitySteps.percent(0.333));
    }
}
