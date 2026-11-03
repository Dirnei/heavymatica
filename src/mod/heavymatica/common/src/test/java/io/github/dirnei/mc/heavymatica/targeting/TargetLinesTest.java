package io.github.dirnei.mc.heavymatica.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.placement.BlockEquivalents.Finish;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.targeting.TargetLines.StateLine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TargetLinesTest {

    @Test
    void wrongBlockIsRed() {
        assertEquals(new StateLine("heavymatica.target.wrong_block", TargetLines.RED), TargetLines.state(CellState.WRONG_BLOCK, null, false));
    }

    @Test
    void cantCheckIsMuted() {
        assertEquals(new StateLine("heavymatica.target.cant_check", TargetLines.MUTED), TargetLines.state(CellState.CANT_CHECK, null, false));
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

    @Test
    void incompleteIsPlain() {
        assertEquals(new StateLine("heavymatica.target.incomplete", TargetLines.TEXT), TargetLines.state(CellState.INCOMPLETE, null, false));
    }

    @Test
    void tooManyIsOrange() {
        assertEquals(new StateLine("heavymatica.target.too_many", TargetLines.ORANGE), TargetLines.state(CellState.TOO_MANY, null, false));
    }

    @Test
    void extraOnAPlannedBlockIsMagenta() {
        assertEquals(new StateLine("heavymatica.target.extra", TargetLines.MAGENTA), TargetLines.state(CellState.EXTRA, null, false));
    }

    @ParameterizedTest
    @EnumSource(value = CellState.class, names = {"MATCHING", "IGNORED"})
    void noLineForStatesThatAreNeverTargets(CellState state) {
        assertNull(TargetLines.state(state, null, false));
    }

    @Test
    void gateToOpenIsOrange() {
        assertEquals(new StateLine("heavymatica.target.open_it", TargetLines.ORANGE), TargetLines.openState(true));
    }

    @Test
    void doorToCloseIsOrange() {
        assertEquals(new StateLine("heavymatica.target.close_it", TargetLines.ORANGE), TargetLines.openState(false));
    }

    @Test
    void chestWaitingForItsOtherHalfIsPlainLikeNotEnoughYet() {
        assertEquals(new StateLine("heavymatica.target.other_half", TargetLines.TEXT), TargetLines.otherHalf());
    }

    @Test
    void chestThatShouldStaySingleNamesTheFoundOrientation() {
        assertEquals(new StateLine("heavymatica.target.wrong_orientation", TargetLines.ORANGE),
                TargetLines.state(CellState.WRONG_ORIENTATION, null, false));
    }

    @Test
    void newLinesAreTranslated() throws IOException {
        try (InputStream in = TargetLinesTest.class.getClassLoader().getResourceAsStream("assets/heavymatica/lang/en_us.json")) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (StateLine line : new StateLine[] {TargetLines.openState(true), TargetLines.openState(false), TargetLines.otherHalf()}) {
                assertTrue(json.contains("\"" + line.key() + "\""), line.key());
            }
        }
    }
}
