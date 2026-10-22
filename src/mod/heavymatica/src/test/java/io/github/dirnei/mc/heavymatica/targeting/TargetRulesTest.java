package io.github.dirnei.mc.heavymatica.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.placement.CellState.Expected.Kind;
import io.github.dirnei.mc.heavymatica.targeting.TargetRules.Candidate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TargetRulesTest {

    private static final UUID GATE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WALL = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TOWER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @ParameterizedTest
    @EnumSource(value = CellState.class, names = {"MISSING", "UNKNOWN"})
    void ghostsAreDrawnInTheLayersWhenTheInHandFilterShowsThem(CellState state) {
        assertTrue(TargetRules.drawn(Kind.BLOCK, state, true, true));
        assertFalse(TargetRules.drawn(Kind.BLOCK, state, true, false));
        assertFalse(TargetRules.drawn(Kind.BLOCK, state, false, true));
    }

    @ParameterizedTest
    @EnumSource(value = CellState.class, names = {"WRONG_BLOCK", "WRONG_ORIENTATION", "WRONG_WATER", "NEEDS_FINISHING"})
    void boxesAreDrawnInTheLayersWhateverTheFilterSays(CellState state) {
        assertTrue(TargetRules.drawn(Kind.BLOCK, state, true, true));
        assertTrue(TargetRules.drawn(Kind.BLOCK, state, true, false));
        assertFalse(TargetRules.drawn(Kind.BLOCK, state, false, true));
    }

    @ParameterizedTest
    @EnumSource(value = CellState.class, names = {"MATCHING", "IGNORED", "EXTRA"})
    void otherStatesAreNeverDrawn(CellState state) {
        assertFalse(TargetRules.drawn(Kind.BLOCK, state, true, true));
    }

    @ParameterizedTest
    @EnumSource(CellState.class)
    void airAndIgnoredPositionsAreNeverDrawn(CellState state) {
        assertFalse(TargetRules.drawn(Kind.AIR, state, true, true));
        assertFalse(TargetRules.drawn(Kind.IGNORED, state, true, true));
    }

    @Test
    void theSelectedPlacementWinsASharedCell() {
        Candidate<String> chosen = TargetRules.choose(List.of(new Candidate<>(GATE, "gate"), new Candidate<>(WALL, "wall")), WALL);
        assertEquals("wall", chosen.value());
    }

    @Test
    void otherwiseTheEarlierPlacementWins() {
        Candidate<String> chosen = TargetRules.choose(List.of(new Candidate<>(GATE, "gate"), new Candidate<>(WALL, "wall")), TOWER);
        assertEquals("gate", chosen.value());
        assertEquals("tower", TargetRules.choose(List.of(new Candidate<>(TOWER, "tower"), new Candidate<>(GATE, "gate")), null).value());
    }

    @Test
    void noCandidatesGiveNone() {
        assertNull(TargetRules.choose(List.<Candidate<String>>of(), GATE));
    }
}
