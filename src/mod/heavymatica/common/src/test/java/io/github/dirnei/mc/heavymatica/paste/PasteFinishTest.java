package io.github.dirnei.mc.heavymatica.paste;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.placement.Placement;
import io.github.dirnei.mc.heavymatica.placement.Placement.Flip;
import io.github.dirnei.mc.heavymatica.placement.Placement.State;
import io.github.dirnei.mc.heavymatica.placement.Placement.Turn;
import io.github.dirnei.mc.heavymatica.placement.PlacementSource;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasteFinishTest {

    private final Placement pasted = placement(UUID.randomUUID(), 0);
    private final PasteFinish finish = new PasteFinish(pasted.id());

    @Test
    void remembersTheFinishWhileThePasteRuns() {
        finish.finished(pasted, 1_000);
        assertEquals(Optional.of(pasted), finish.finished());
    }

    @Test
    void remembersAFinishShortlyAfterThePasteIsDone() {
        finish.pasteDone(1_000);
        finish.finished(pasted, 1_000 + PasteFinish.GRACE_MILLIS);
        assertEquals(Optional.of(pasted), finish.finished());
    }

    @Test
    void ignoresAFinishLongAfterThePaste() {
        finish.pasteDone(1_000);
        finish.finished(pasted, 1_001 + PasteFinish.GRACE_MILLIS);
        assertEquals(Optional.empty(), finish.finished());
    }

    @Test
    void ignoresOtherPlacements() {
        finish.finished(placement(UUID.randomUUID(), 0), 1_000);
        assertEquals(Optional.empty(), finish.finished());
    }

    @Test
    void keepsTheFirstFinish() {
        finish.finished(pasted, 1_000);
        finish.finished(placement(pasted.id(), 20), 2_000);
        assertEquals(Optional.of(pasted), finish.finished());
    }

    private static Placement placement(UUID id, int pivotX2) {
        return new Placement(id, new PlacementSource.Local("abc"), "Test", 5, 5, 5, pivotX2, 64, 0, Turn.NONE, Flip.NONE, "minecraft:overworld",
                State.LOCKED, 0, Layers.NONE, false, Map.of());
    }
}
