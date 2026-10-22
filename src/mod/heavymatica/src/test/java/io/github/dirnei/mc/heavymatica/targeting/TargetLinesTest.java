package io.github.dirnei.mc.heavymatica.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.dirnei.mc.heavymatica.placement.BlockEquivalents.Finish;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.targeting.TargetLines.StateLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TargetLinesTest {

    @Test
    void wrongBlockIsRed() {
        assertEquals(new StateLine("heavymatica.target.wrong_block", TargetLines.RED), TargetLines.state(CellState.WRONG_BLOCK, null, false));
    }

    @Test
    void wrongOrientationIsOrange() {
        assertEquals(new StateLine("heavymatica.target.wrong_orientation", TargetLines.ORANGE),
                TargetLines.state(CellState.WRONG_ORIENTATION, null, false));
    }

    @Test
    void wrongWaterIsCyanAndSaysWhichWay() {
        assertEquals(new StateLine("heavymatica.target.needs_water", TargetLines.CYAN), TargetLines.state(CellState.WRONG_WATER, null, true));
        assertEquals(new StateLine("heavymatica.target.not_waterlogged", TargetLines.CYAN), TargetLines.state(CellState.WRONG_WATER, null, false));
    }

    @Test
    void needsFinishingIsYellowWithTheStep() {
        assertEquals(new StateLine("heavymatica.target.finish.shovel", TargetLines.YELLOW),
                TargetLines.state(CellState.NEEDS_FINISHING, Finish.SHOVEL, false));
        assertEquals(new StateLine("heavymatica.target.finish.water", TargetLines.YELLOW),
                TargetLines.state(CellState.NEEDS_FINISHING, Finish.WATER, false));
    }

    @Test
    void unknownIsMuted() {
        assertEquals(new StateLine("heavymatica.target.unknown", TargetLines.MUTED), TargetLines.state(CellState.UNKNOWN, null, false));
    }

    @Test
    void missingIsPlain() {
        assertEquals(new StateLine("heavymatica.target.missing", TargetLines.TEXT), TargetLines.state(CellState.MISSING, null, false));
    }

    @ParameterizedTest
    @EnumSource(value = CellState.class, names = {"MATCHING", "IGNORED", "EXTRA"})
    void noLineForStatesThatAreNeverTargets(CellState state) {
        assertNull(TargetLines.state(state, null, false));
    }
}
