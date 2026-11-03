package io.github.dirnei.mc.heavymatica.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dirnei.mc.heavymatica.targeting.PickDecision.Action;
import io.github.dirnei.mc.heavymatica.targeting.PickDecision.Candidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PickDecisionTest {

    private static final String SHULKER_BOX = "shulker_box";

    private static Candidate<String> carried(String item, List<Integer> hotbar, List<Integer> main) {
        return new Candidate<>(item, hotbar, main, null);
    }

    private static Candidate<String> none(String item) {
        return new Candidate<>(item, List.of(), List.of(), null);
    }

    private static Action<String> survival(List<Candidate<String>> candidates, int selected, int suitable) {
        return PickDecision.decide(candidates, false, selected, suitable);
    }

    @Test
    void noCandidatesGiveVanilla() {
        assertEquals(new PickDecision.Vanilla<String>(), survival(List.of(), 0, 0));
        assertEquals(new PickDecision.Vanilla<String>(), PickDecision.decide(List.<Candidate<String>>of(), true, 0, 0));
    }

    @Test
    void ownItemInTheHotbarIsSelectedLowestFirst() {
        assertEquals(new PickDecision.Select<String>(2), survival(List.of(carried("oak_stairs", List.of(2, 6), List.of())), 0, 0));
    }

    @Test
    void theSelectedSlotIsPreferred() {
        assertEquals(new PickDecision.Select<String>(6), survival(List.of(carried("oak_stairs", List.of(2, 6), List.of())), 6, 0));
    }

    @Test
    void ownItemInTheMainInventoryBeatsThePlacingItemInTheHotbar() {
        List<Candidate<String>> candidates = List.of(carried("grass_block", List.of(), List.of(12, 20)), carried("dirt", List.of(1), List.of()));
        assertEquals(new PickDecision.Swap<String>(12, 3), survival(candidates, 0, 3));
    }

    @Test
    void dirtIsPickedForGrassWhenOnlyDirtIsCarried() {
        List<Candidate<String>> candidates = List.of(none("grass_block"), carried("dirt", List.of(4), List.of(15)));
        assertEquals(new PickDecision.Select<String>(4), survival(candidates, 0, 3));
    }

    @Test
    void creativeWithNothingCarriedAddsTheFirstCandidate() {
        List<Candidate<String>> candidates = List.of(none("grass_block"), none("dirt"));
        assertEquals(new PickDecision.CreativeAdd<>("grass_block", 5), PickDecision.decide(candidates, true, 0, 5));
    }

    @Test
    void creativeWithTheItemInTheHotbarSelectsIt() {
        assertEquals(new PickDecision.Select<String>(2), PickDecision.decide(List.of(carried("oak_stairs", List.of(2), List.of())), true, 0, 5));
    }

    @Test
    void creativeIgnoresContainers() {
        List<Candidate<String>> candidates = List.of(new Candidate<>("oak_stairs", List.of(), List.of(), SHULKER_BOX));
        assertEquals(new PickDecision.CreativeAdd<>("oak_stairs", 0), PickDecision.decide(candidates, true, 0, 0));
    }

    @Test
    void survivalWithTheItemOnlyInAContainer() {
        List<Candidate<String>> candidates = List.of(none("grass_block"), new Candidate<>("dirt", List.of(), List.of(), SHULKER_BOX));
        assertEquals(new PickDecision.InContainer<>("dirt", SHULKER_BOX), survival(candidates, 0, 0));
    }

    @Test
    void survivalWithNothingCarriedNamesTheFirstCandidate() {
        assertEquals(new PickDecision.NotCarried<>("oak_stairs"), survival(List.of(none("oak_stairs"), none("oak_planks")), 0, 0));
    }
}
