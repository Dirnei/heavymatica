package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dirnei.mc.heavymatica.cloud.client.ApiClient;
import io.github.dirnei.mc.heavymatica.placement.Placement.Flip;
import io.github.dirnei.mc.heavymatica.placement.Placement.State;
import io.github.dirnei.mc.heavymatica.placement.Placement.Turn;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlacementStoreTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TempDir
    Path directory;

    private final ObjectMapper json = ApiClient.createDefaultObjectMapper();

    private static Placement placement(PlacementSource source, State state) {
        return new Placement(UUID.randomUUID(), source, "Castle Gate", 5, 3, 9, 21, 64, -13, Turn.CLOCKWISE_90, Flip.FRONT_BACK, "minecraft:overworld",
                state, 0, Layers.NONE, false);
    }

    @Test
    void roundTripKeepsAnchoredPlacements() {
        PlacementStore store = new PlacementStore(directory, json);
        assertEquals(List.of(), store.open(PLAYER, "mp-play.example.net_25565"));
        Placement cloud = placement(new PlacementSource.Cloud("s1", 3, "aa"), State.ANCHORED);
        Placement local = placement(new PlacementSource.Local("bb"), State.ANCHORED);
        store.changed(List.of(cloud, local), 0);
        store.close();

        PlacementStore again = new PlacementStore(directory, json);
        assertEquals(List.of(cloud, local), again.open(PLAYER, "mp-play.example.net_25565"));
        assertTrue(Files.isRegularFile(directory.resolve(PLAYER.toString()).resolve("mp-play.example.net_25565.json")));
    }

    @Test
    void followingPlacementsAreNotWritten() {
        PlacementStore store = new PlacementStore(directory, json);
        store.open(PLAYER, "sp-testworld");
        Placement anchored = placement(new PlacementSource.Local("bb"), State.ANCHORED);
        store.changed(List.of(placement(new PlacementSource.Local("aa"), State.FOLLOWING), anchored), 0);
        store.flush();
        assertEquals(List.of(anchored), new PlacementStore(directory, json).open(PLAYER, "sp-testworld"));
    }

    @Test
    void worldsAndPlayersAreSeparate() {
        PlacementStore store = new PlacementStore(directory, json);
        store.open(PLAYER, "mp-play.example.net_25565");
        store.changed(List.of(placement(new PlacementSource.Local("bb"), State.ANCHORED)), 0);
        assertEquals(List.of(), store.open(PLAYER, "mp-other.example.net_25565"));
        assertEquals(List.of(), store.open(UUID.randomUUID(), "mp-play.example.net_25565"));
        assertEquals(1, store.open(PLAYER, "mp-play.example.net_25565").size());
    }

    @Test
    void savingIsDebounced() {
        PlacementStore store = new PlacementStore(directory, json);
        store.open(PLAYER, "sp-a");
        Path file = directory.resolve(PLAYER.toString()).resolve("sp-a.json");
        store.changed(List.of(placement(new PlacementSource.Local("bb"), State.ANCHORED)), 1_000);
        store.changed(List.of(placement(new PlacementSource.Local("cc"), State.ANCHORED)), 2_500);
        store.tick(2_999);
        assertFalse(Files.exists(file));
        store.tick(3_000);
        assertTrue(Files.exists(file));
        assertFalse(store.hasPending());
        assertEquals("cc", new PlacementStore(directory, json).open(PLAYER, "sp-a").getFirst().source().sha256());
    }

    @Test
    void corruptFileLoadsEmptyAndIsSetAside() throws IOException {
        Path file = directory.resolve(PLAYER.toString()).resolve("sp-a.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ not json");
        PlacementStore store = new PlacementStore(directory, json);
        assertEquals(List.of(), store.open(PLAYER, "sp-a"));
        assertTrue(Files.exists(file.resolveSibling("sp-a.json.corrupt")));
    }

    @Test
    void lockedPlacementKeepsItsLayers() {
        PlacementStore store = new PlacementStore(directory, json);
        store.open(PLAYER, "sp-a");
        Placement locked = placement(new PlacementSource.Local("bb"), State.LOCKED).with(new Layers(Layers.Mode.FOLLOW, 4, 6, 3)).withInHand(true);
        store.changed(List.of(locked), 0);
        store.flush();
        assertEquals(List.of(locked), new PlacementStore(directory, json).open(PLAYER, "sp-a"));
    }

    @Test
    void olderFilesLoadAnchoredWithLayersOff() throws IOException {
        Path file = directory.resolve(PLAYER.toString()).resolve("sp-a.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {"version":1,"placements":[{"id":"6c85d78e-f2db-4bb8-8541-0f9eecfffef1","source":{"kind":"local","sha256":"aa"},
                "name":"Hut","width":5,"height":4,"length":6,"pivotX2":-289,"y":71,"pivotZ2":-135,"rotation":"NONE","mirror":"NONE",
                "dimension":"minecraft:overworld"}]}
                """);
        Placement loaded = new PlacementStore(directory, json).open(PLAYER, "sp-a").getFirst();
        assertEquals(State.ANCHORED, loaded.state());
        assertEquals(Layers.NONE, loaded.layers());
        assertFalse(loaded.inHand());
    }

    @Test
    void worldKeys() {
        assertEquals("sp-new_world__2_", PlacementStore.singleplayerKey("New World (2)"));
        assertEquals("mp-play.example.net_25565", PlacementStore.multiplayerKey("play.example.net"));
        assertEquals("mp-play.example.net_25565", PlacementStore.multiplayerKey("Play.Example.NET:25565"));
        assertEquals("mp-localhost_25599", PlacementStore.multiplayerKey("localhost:25599"));
        assertEquals("mp-___1__25565", PlacementStore.multiplayerKey("[::1]"));
        assertEquals("mp-___1__25570", PlacementStore.multiplayerKey("[::1]:25570"));
        assertEquals("realms-my_realm", PlacementStore.realmsKey("My Realm"));
    }
}
