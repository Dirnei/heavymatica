# Tasks

## 1. Model and controller (game-free)

- [x] 1.1 Add `State.LOCKED`, the `Layers` record (mode, bottom, top) and `inHand` to `Placement`, and `Layers.visible(placement, feetLayer, offset)` (D1, D2); verify unit tests for the visible range in each mode, clamping at 0 and the top, and an unlocked placement always showing all layers
- [x] 1.2 Add `lock`, `unlock` with the shared two-press pending action, `cycleLayers`, `moveWindow`, `resizeWindow` and `toggleInHand` to `PlacementController`, and make `rotate`, `mirror`, `move`, `vertical`, release and `remove` ignore LOCKED placements (D2); verify unit tests covering every scenario in `client/placement-layers` and the locking scenarios in `client/schematic-placement`, plus existing controller tests still passing

## 2. Saving

- [x] 2.1 Extend `PlacementStore` with the optional `locked`, `layers` and `inHand` fields (D5); verify unit tests for a locked placement with a window and in-hand round-tripping, and a file without the new fields loading as ANCHORED with layers off

## 3. Cell meshes and drawing

- [x] 3.1 Add `GhostCells`: one-pass, off-thread build of one buffer per block type with per-layer index offsets, tessellated against a cell view so faces outside the cell are drawn; cache and sweep it with the full meshes (D3); verify in `runClient` that a locked solid-wall schematic in from-to on a single layer shows that layer's top and bottom faces, and that the mesh count drops back after unlocking and removing
- [x] 3.2 Draw locked placements with layers or in-hand from `GhostCells`: visible layer range per block, main-hand block at full opacity, other hotbar blocks at the hotbar factor, others skipped, full mesh until the cells are ready, cyan outline for LOCKED; add `layers.followOffset` and `layers.hotbarOpacity` config entries (D4, D6); verify in `runClient`: stepping the window and scrolling the hotbar change the ghost immediately without flicker, follow feet tracks the player while holding oak planks, and holding the remote shows only dimmed hotbar blocks

## 4. Remote keys and overlay

- [x] 4.1 Add the L and I key mappings and route arrows and numpad + / - by state in `RemoteInput`, with key repeat for window moves (D2); verify in `runClient` that L locks, L twice unlocks, a single L expires after 3 s, R / M / Delete / sneak + right-click do nothing on a locked placement, and the arrows move the window instead of the placement
- [x] 4.2 Extend `PlacementOverlay` with the LOCKED badge, the layer and in-hand lines, the LOCKED hints and the pending-unlock note (D6); verify in `runClient` against the overlay scenarios in `client/placement-remote`

## 5. Integration

- [x] 5.1 Build one layer by hand in singleplayer: lock a placement, switch to follow feet with in-hand on, climb while placing blocks, then rejoin; verify the shown layers follow the player, the held block shows normally, and after rejoining the placement is still LOCKED with the same settings
- [x] 5.2 Update `ROADMAP.md` step 3 to describe locking and layers and mark layers as done; verify the file reads consistently

## 6. Follow offset per placement

- [x] 6.1 Move the follow offset from config into `Layers` (default 1, 0..16), let numpad + / - change it in follow feet, and show it in the overlay line; verify unit tests for raising, lowering and clamping the offset and the visible range following it, and in `runClient` that numpad + in follow feet shows one more layer

## Workflow follow-up

- Commit the implementation once all tasks are done (Conventional Commits, for example `feat(placement): add locking and layer views`).
- Archive the change with `/opsx:archive` when asked.
