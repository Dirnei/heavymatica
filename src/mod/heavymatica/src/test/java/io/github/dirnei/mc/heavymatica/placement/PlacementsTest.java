package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.placement.Placement.State;
import io.github.dirnei.mc.heavymatica.placement.Placements.Ray;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlacementsTest {

    private final Placements placements = new Placements();

    private Placement add(int minX, int minZ, int width, int height, int length) {
        Placement placement = PlacementTransformTest.at(PlacementTransformTest.placement(width, height, length, State.ANCHORED), minX, 64, minZ);
        placements.add(placement);
        return placement;
    }

    @Test
    void picksTheNearestBox() {
        Placement near = add(0, 5, 3, 3, 3);
        Placement far = add(0, 20, 3, 3, 3);
        // Eye at x 1.5, y 65, looking south (+z).
        assertEquals(Optional.of(near), placements.firstHit("minecraft:overworld", new Ray(1.5, 65, 0, 0, 0, 1)));
        assertEquals(Optional.of(far), placements.firstHit("minecraft:overworld", new Ray(1.5, 65, 10, 0, 0, 1)));
    }

    @Test
    void aBoxTheEyeIsInsideIsHitFirst() {
        Placement around = add(-10, -10, 21, 10, 21);
        add(0, 3, 2, 2, 2);
        assertEquals(Optional.of(around), placements.firstHit("minecraft:overworld", new Ray(0.5, 65, 0.5, 0, 0, 1)));
    }

    @Test
    void theSmallestOfSeveralBoxesAroundTheEyeWins() {
        add(-10, -10, 21, 10, 21);
        Placement small = add(-1, -1, 3, 3, 3);
        add(-5, -5, 11, 10, 11);
        assertEquals(Optional.of(small), placements.firstHit("minecraft:overworld", new Ray(0.5, 65, 0.5, 1, 0, 0)));
    }

    @Test
    void reachIsSixtyFourBlocks() {
        add(0, 70, 3, 3, 3);
        assertEquals(Optional.empty(), placements.firstHit("minecraft:overworld", new Ray(1.5, 65, 0, 0, 0, 1)));
        assertTrue(placements.firstHit("minecraft:overworld", new Ray(1.5, 65, 10, 0, 0, 1)).isPresent());
    }

    @Test
    void missesBoxesBehindOrBeside() {
        add(0, 5, 3, 3, 3);
        assertEquals(Optional.empty(), placements.firstHit("minecraft:overworld", new Ray(1.5, 65, 0, 0, 0, -1)));
        assertEquals(Optional.empty(), placements.firstHit("minecraft:overworld", new Ray(10.5, 65, 0, 0, 0, 1)));
    }

    @Test
    void onlyTheGivenDimensionIsHit() {
        add(0, 5, 3, 3, 3);
        assertEquals(Optional.empty(), placements.firstHit("minecraft:the_nether", new Ray(1.5, 65, 0, 0, 0, 1)));
        assertEquals(List.of(), placements.inDimension("minecraft:the_nether"));
    }

    @Test
    void selectionIsSingleAndMovesToTheFront() {
        Placement gate = add(0, 0, 3, 3, 3);
        Placement tower = add(10, 0, 3, 3, 3);
        assertEquals(Optional.of(tower), placements.selected());

        placements.select(gate.id());
        assertEquals(Optional.of(gate), placements.selected());
        assertEquals(List.of(gate, tower), placements.all());

        placements.remove(gate.id());
        assertEquals(Optional.empty(), placements.selected());
    }

    @Test
    void replacingLoadsUnselected() {
        add(0, 0, 3, 3, 3);
        Placement loaded = PlacementTransformTest.placement(2, 2, 2, State.ANCHORED);
        placements.replaceAll(List.of(loaded));
        assertEquals(List.of(loaded), placements.all());
        assertEquals(Optional.empty(), placements.selected());
    }

    @Test
    void listenersHearChangesButNotNoOps() {
        List<Integer> sizes = new ArrayList<>();
        placements.onChange(all -> sizes.add(all.all().size()));
        Placement placement = add(0, 0, 3, 3, 3);
        placements.update(placement);
        placements.update(PlacementTransform.mirror(placement));
        assertEquals(List.of(1, 1), sizes);
    }
}
