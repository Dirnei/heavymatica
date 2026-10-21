# Tasks

## 1. Ghost rendering spike

- [x] 1.1 Behind a dev flag (like `ThumbnailSpike`), tessellate a small fixed schematic off-thread with `ModelBlockRenderer`, upload it to a `GpuBuffer` and draw it translucent in `RenderLevelStageEvent` at a fixed world position (D6, D7); verify in `runClient` that the ghost shows with depth test on, real blocks hide it, and frame time does not change visibly

## 2. Placement model and transform (game-free)

- [x] 2.1 Add `placement/Placement`, `PlacementSource` (Cloud / Local) and `PlacementTransform` with the doubled pivot, derived bounds, rotate/mirror and local-to-world block mapping (D1, D5); verify unit tests: four clockwise rotations restore identical bounds, a 5 x 3 x 9 rotated clockwise becomes 9 x 5 around the same centre, mirroring keeps the footprint, and block mapping matches vanilla's mirror-then-rotate order
- [x] 2.2 Add camera-relative movement (yaw snapped to four directions, Shift = 10) and following (target block from hit block + face, centred footprint, follow offset) to `PlacementTransform` (D5); verify unit tests for each facing, Shift steps, and the "looking at the ground at 10, 63, 20" scenario

## 3. Placements and controller (game-free)

- [x] 3.1 Add `Placements` (list, single selection, current dimension filter, change listeners) and the ray-vs-box hit test with eye-inside and smallest-volume tie rules (D4); verify unit tests for nearest box, camera inside a box, overlapping boxes and the 64-block limit
- [x] 3.2 Add `PlacementController` with the right-click resolution table, start, move, vertical, rotate, mirror and two-press remove with a 3-second window (D4); verify unit tests covering every spec scenario in `client/schematic-placement` for anchoring, releasing, selecting, opening the library, moving, rotating, mirroring and removing

## 4. Persistence

- [x] 4.1 Add `PlacementStore`: per-player, per-world-key JSON files, the world key rules for singleplayer, multiplayer and Realms, atomic writes, ANCHORED placements only, debounced save (D8); verify unit tests for round-trip, key sanitising, FOLLOWING placements not being written, and a corrupt file loading as empty without crashing
- [x] 4.2 Load placements on join and save on disconnect from `HeavymaticaClient`, resolving each version through `Library.file` and marking failures unavailable, retrying on library or cloud-state change (D8); verify in `runClient`: anchor two placements, rejoin and see both unselected at the same spots, and with the cloud stopped an uncached one shows as outline and the overlay says unavailable

## 5. Remote and input

- [x] 5.1 Add `remote/Remotes` with the `remote.item` config entry (validated item ID, default `minecraft:stick`) and the empty `heavymatica:remotes` item tag (D2); verify in `runClient` that a stick counts, a blaze rod counts after changing the config, and an item added to the tag by a datapack counts
- [x] 5.2 Add `RemoteKeys` with the `HOLDING_REMOTE` conflict context and the nine key mappings with their defaults and lang entries (D3); verify the controls screen lists them under Heavymatica and a rebound key shows its new name
- [x] 5.3 Add `RemoteInput`: cancel the use interaction while a remote is held, feed rays, keys with Shift and key repeat into the controller, and update FOLLOWING placements each tick from a 64-block clip (D4, D5); verify in `runClient` that right-clicking a chest with the remote does not open it, left-click still breaks blocks, held arrows repeat, and keys do nothing with a sword in hand

## 6. Ghost rendering

- [x] 6.1 Turn the spike into `GhostMeshes`: transformed view, hidden-block culling, off-thread build keyed by (sha256, rotation, mirror), reference counting, and freeing on disconnect and resource reload (D6); remove the spike flag; verify in `runClient` that rotating rebuilds without a frame hitch, rotated stairs face the right way, and unused meshes are freed (logged count drops to 0 after removing all placements)
- [x] 6.2 Add `GhostRenderer` with the current-dimension filter, state-coloured outlines and the block budget with selected-first ordering, plus the `ghost.opacity` and `ghost.blockBudget` config entries (D7); verify in `runClient`: a ghost in the Overworld is hidden in the Nether, a schematic over the budget shows as outline, and the selected outline is yellow while following and green when anchored

## 7. Overlay

- [x] 7.1 Add `PlacementOverlay` as a GUI layer with the details block, state-filtered hints using bound key names, the remove and unavailable notes, and the `overlay.side` and `overlay.compact` config entries (D9); verify in `runClient` against the overlay scenarios in `client/placement-remote`, including a rebound key showing its new name and the overlay hiding when the remote is put away

## 8. Place from the library

- [x] 8.1 Add the Place button to `DetailsPanel` with its disabled-offline state, loading the version off-thread and starting a FOLLOWING placement, plus the "Hold a <item> to place" message when no remote is held (D10); verify in `runClient` against the `client/schematic-library` Place scenarios, including a local entry while signed out

## 9. Integration

- [x] 9.1 End to end on a vanilla dedicated server (no Heavymatica on the server): join, place from the library, anchor, move, rotate, mirror, select a second placement, release and remove, then rejoin; verify every step works and the server log shows no unknown packets or item errors
- [x] 9.2 Update `ROADMAP.md` step 3 to describe the remote-based controls (keyboard instead of the on-screen pad and mouse dragging) and list material swaps and layers as the remaining parts of step 3; verify the file reads consistently with this change
