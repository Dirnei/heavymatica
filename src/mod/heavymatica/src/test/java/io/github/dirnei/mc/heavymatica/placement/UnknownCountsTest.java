package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UnknownCountsTest {

    /** 40 shafts spread over 8 layers, 5 per layer, and 3 cogwheels on the top layer. */
    private static UnknownCounts counts() {
        UnknownCounts counts = new UnknownCounts(8);
        for (int layer = 0; layer < 8; layer++) {
            for (int i = 0; i < 5; i++) {
                counts.add(layer, "create:shaft");
            }
        }
        for (int i = 0; i < 3; i++) {
            counts.add(7, "create:cogwheel");
        }
        return counts;
    }

    @Test
    void allLayers() {
        assertEquals(Map.of("create:cogwheel", 3, "create:shaft", 40), counts().sum(0, 7));
        assertEquals(List.of("create:cogwheel", "create:shaft"), List.copyOf(counts().sum(0, 7).keySet()));
    }

    @Test
    void followsTheLayers() {
        assertEquals(Map.of("create:shaft", 10), counts().sum(0, 1));
    }

    @Test
    void theListCarriesTheUnknownPartWithoutShortages() {
        MaterialList<String, String> list = SharedLists.of(Map.of("oak_planks", 10), Map.of(), Map.of())
                .withUnknown(counts().sum(0, 7));
        assertEquals(Map.of("create:cogwheel", 3, "create:shaft", 40), list.unknown());
        assertEquals(List.of("oak_planks"), list.rows().stream().map(MaterialList.Row::item).toList());
        assertEquals(List.of(), list.cantCarry());
    }
}
