package io.github.dirnei.mc.heavymatica.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.dirnei.mc.heavymatica.targeting.GhostRay.Answer;
import io.github.dirnei.mc.heavymatica.targeting.GhostRay.Hit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GhostRayTest {

    /** Records the visited cells and answers from fixed target and blocker sets. */
    private static final class Grid implements GhostRay.Cells {

        final List<String> visited = new ArrayList<>();
        final List<Double> entries = new ArrayList<>();
        private final Set<String> targets;
        private final Set<String> blockers;

        Grid(Set<String> targets, Set<String> blockers) {
            this.targets = targets;
            this.blockers = blockers;
        }

        static Grid empty() {
            return new Grid(Set.of(), Set.of());
        }

        @Override
        public Answer at(int x, int y, int z, double entryDistance) {
            String cell = x + "," + y + "," + z;
            visited.add(cell);
            entries.add(entryDistance);
            return targets.contains(cell) ? Answer.TARGET : blockers.contains(cell) ? Answer.BLOCKED : Answer.PASS;
        }
    }

    @Test
    void visitsCellsAlongPositiveX() {
        Grid grid = Grid.empty();
        GhostRay.cast(0.5, 0.5, 0.5, 1, 0, 0, 3.0, grid);
        assertEquals(List.of("0,0,0", "1,0,0", "2,0,0", "3,0,0"), grid.visited);
    }

    @Test
    void visitsCellsAlongNegativeY() {
        Grid grid = Grid.empty();
        GhostRay.cast(0.5, 0.5, 0.5, 0, -1, 0, 2.0, grid);
        assertEquals(List.of("0,0,0", "0,-1,0", "0,-2,0"), grid.visited);
    }

    @Test
    void visitsCellsAlongNegativeZ() {
        Grid grid = Grid.empty();
        GhostRay.cast(2.5, 1.5, 2.5, 0, 0, -1, 2.0, grid);
        assertEquals(List.of("2,1,2", "2,1,1", "2,1,0"), grid.visited);
    }

    @Test
    void entryDistancesGrowAlongTheRay() {
        Grid grid = Grid.empty();
        GhostRay.cast(0.25, 0.5, 0.5, 1, 0, 0, 2.0, grid);
        assertEquals(List.of(0.0, 0.75, 1.75), grid.entries);
    }

    @Test
    void diagonalVisitsEveryCellItCrosses() {
        Grid grid = Grid.empty();
        GhostRay.cast(0.5, 0.2, 0.5, 1, 1, 0, 2.2, grid);
        assertEquals(List.of("0,0,0", "1,0,0", "1,1,0", "2,1,0"), grid.visited);
    }

    @Test
    void negativeDiagonal() {
        Grid grid = Grid.empty();
        GhostRay.cast(0.5, 0.5, 0.8, -1, 0, -1, 1.5, grid);
        assertEquals(List.of("0,0,0", "-1,0,0", "-1,0,-1"), grid.visited);
    }

    @Test
    void startOnABoundaryGoingNegativeSkipsTheCellBehind() {
        Grid grid = Grid.empty();
        GhostRay.cast(2.0, 0.5, 0.5, -1, 0, 0, 1.5, grid);
        assertEquals(List.of("1,0,0", "0,0,0"), grid.visited);
    }

    @Test
    void startOnABoundaryGoingPositive() {
        Grid grid = Grid.empty();
        GhostRay.cast(2.0, 0.5, 0.5, 1, 0, 0, 1.5, grid);
        assertEquals(List.of("2,0,0", "3,0,0"), grid.visited);
    }

    @Test
    void zeroDirectionComponentsStayInTheirCell() {
        Grid grid = Grid.empty();
        GhostRay.cast(5.0, 7.0, 0.5, 0, 0, 1, 2.0, grid);
        assertEquals(List.of("5,7,0", "5,7,1", "5,7,2"), grid.visited);
    }

    @Test
    void zeroDirectionGivesNothing() {
        Grid grid = Grid.empty();
        assertNull(GhostRay.cast(0.5, 0.5, 0.5, 0, 0, 0, 5.0, grid));
        assertEquals(List.of(), grid.visited);
    }

    @Test
    void directionNeedNotBeNormalised() {
        Grid grid = new Grid(Set.of("3,0,0"), Set.of());
        Hit hit = GhostRay.cast(0.5, 0.5, 0.5, 10, 0, 0, 5.0, grid);
        assertNotNull(hit);
        assertEquals(2.5, hit.distance(), 1e-9);
    }

    @Test
    void targetBehindABlockerGivesNone() {
        Grid grid = new Grid(Set.of("4,0,0"), Set.of("2,0,0"));
        assertNull(GhostRay.cast(0.5, 0.5, 0.5, 1, 0, 0, 10.0, grid));
        assertEquals(List.of("0,0,0", "1,0,0", "2,0,0"), grid.visited);
    }

    @Test
    void targetInFrontOfABlocker() {
        Grid grid = new Grid(Set.of("2,0,0"), Set.of("4,0,0"));
        Hit hit = GhostRay.cast(0.5, 0.5, 0.5, 1, 0, 0, 10.0, grid);
        assertEquals(new Hit(2, 0, 0, 1.5), hit);
    }

    @Test
    void nearestTargetWins() {
        Grid grid = new Grid(Set.of("4,0,0", "7,0,0"), Set.of());
        Hit hit = GhostRay.cast(0.5, 0.5, 0.5, 1, 0, 0, 10.0, grid);
        assertNotNull(hit);
        assertEquals(4, hit.x());
    }

    @Test
    void maxDistanceCutsOffAFartherTarget() {
        Grid grid = new Grid(Set.of("6,0,0"), Set.of());
        assertNull(GhostRay.cast(0.5, 0.5, 0.5, 1, 0, 0, 5.0, grid));
        assertEquals("5,0,0", grid.visited.getLast());
    }

    @Test
    void entryDistanceOfTheHitCell() {
        Grid grid = new Grid(Set.of("0,-3,0"), Set.of());
        Hit hit = GhostRay.cast(0.5, 1.6, 0.5, 0, -1, 0, 10.0, grid);
        assertNotNull(hit);
        assertEquals(3.6, hit.distance(), 1e-9);
    }
}
