# Tasks

## 1. Resolver

- [ ] 1.1 Add `blocks.BlockResolver` with the `StateUpgrader` interface, the swap-by-raw-ID step and lenient resolution moved from `GhostView.lenient`; it returns a `Resolved` (state, or unknown with its shown ID) (D1, D2). Verify with a new `BlockResolverTest` under `./gradlew :heavymatica:test`, using a table fake upgrader, covering: unknown property value keeps the rest, extra property dropped, unknown ID, swap keyed by raw ID applied after upgrade with upgraded properties, swap of an unknown block to a known one, swap target unknown leaves the original, and no upgrade without a data version or for a newer one.
- [ ] 1.2 Add the DataFixer `StateUpgrader` (`DataFixers.getDataFixer()`, `References.BLOCK_STATE`, `NbtOps`), with a process-wide cache per (data version, raw entry) (D1). Add `legacy-grass.litematic` (data version 3578, `minecraft:grass`) and `legacy-wall.litematic` (data version 2230, `cobblestone_wall[north=true,...]`) to `src/conformance/generator/generate.py` with their expected outputs, raw as written. Verify with `ConformanceTest` and the cloud's conformance tests passing unchanged in behaviour, and with a `HEAVYMATICA_DEV_SCREEN` step in `runClient` that logs the resolved states of both files (`minecraft:short_grass`, a wall with `north=low`).

## 2. Ghosts and thumbnails

- [ ] 2.1 Switch `GhostView` and `SchematicView` to `BlockResolver` with the model's data version, remove the strict `SchematicView.resolve`, and keep unknown entries as air plus an unknown flag per palette entry. Add the per-view unknown summary (types, positions, per raw ID) (D1, D5). Verify with `./gradlew :heavymatica:test` and in `runClient`: `legacy-grass.litematic` shows short grass, `legacy-wall.litematic` shows its walls, and an upload thumbnail of `modded.v3.schem` has no placeholder cubes.
- [ ] 2.2 Emit missing-texture cubes for unknown positions into a dedicated placeholder buffer in `GhostMesh` and a placeholder cell in `GhostCells`, culling faces against other placeholders and solid known blocks (D4). Verify in `runClient` with `modded.v3.schem`: purple-and-black cubes at the modded positions in FOLLOWING and ANCHORED, and on a locked placement only within the visible layers ("Unknown blocks show a placeholder").
- [ ] 2.3 Add `ghost.showUnknownBlocks` (default true, saved like `ghost.opacity`) to `HeavymaticaConfig`, and drive the placeholder's draw opacity from it and the in-hand filter, without rebuilding (D4, D7). Verify in `runClient`: editing the config value hides and shows the cubes on every placement at once, and the in-hand filter hides them.

## 3. Comparison and finishing

- [ ] 3.1 Add `CellState.CANT_CHECK` and `Expected.Kind.CANT_CHECK`, give unknown blocks CANT_CHECK in `CellKeys.expected`, show those cells in the mask, and keep a total for them in `ComparisonState` (D3). Verify with `CellStateTest` and `ComparisonStateTest` cases for "Block from a missing mod", "Can't check does not count" (480 / 960) and structure void staying ignored, and in `runClient` that a locked modded placement shows cubes and no boxes.
- [ ] 3.2 Pass the can't-check count to the finish toast and add its language key. Verify with a `FinishesTest` case for "Only blocks it can't check" (no finish), and in `runClient` for "Finished with blocks it can't check": sound plays, and the toast names the placement and the count.

## 4. Overlay, targeting and wheel

- [ ] 4.1 Add the overlay line from the view's unknown summary in every placement state, saying when the blocks are hidden. Verify in `runClient` with `modded.v3.schem` anchored and locked, with the setting on and off, and with an all-known schematic showing no line.
- [ ] 4.2 Make shown CANT_CHECK cells targetable in `GhostTargeting`, carry the raw ID in `GhostTarget`, and add the unknown line to `TargetLines` and the Jade plugin; pick block stays vanilla (no planned index). Verify with `TargetLinesTest` and `TargetRulesTest` cases, and in `runClient`: the HUD names `create:...` on a cube, the cube isn't targetable while hidden, and middle click does what vanilla does.
- [ ] 4.3 Add `RadialMenu.Entry.UNKNOWN_BLOCKS` after Opacity in FOLLOWING, ANCHORED and LOCKED when the selection has unknown types, labelled shown or hidden; picking it toggles and saves `ghost.showUnknownBlocks` and closes the wheel. Add the icon, label and language keys (D7). Verify with `RadialMenuTest` cases for "No unknown blocks", "Nothing selected" and each state, and in `runClient` for "Hide from the wheel" and "Kept after a restart".

## 5. Lists, materials screen and details

- [ ] 5.1 Keep CANT_CHECK positions out of the needs, and add the "unknown blocks" part per raw ID over the visible layers to the material list and the materials screen (D5). Verify with `MaterialListTest` and `MaterialCountsTest` cases for "Missing mod" (40) and "Follows the layers" (10).
- [ ] 5.2 In the materials screen, list unknown block types with their raw ID and an unknown mark, allow swapping them with no same-kind suggestions, and show upgraded rows by their current name while the swap is saved under the raw ID. Verify in `runClient`: swap `modded.v3.schem`'s modded block to iron chain (the cubes become chains, the overlay line goes away), and `legacy-grass.litematic`'s row shows short grass.
- [ ] 5.3 Resolve the details' block counts by ID with the version's data version, mark unknown rows and show the unknown types count (D6). Verify with a `LibraryTest` case on the counts mapping through a fake upgrader, and in `runClient` on the details of `legacy-grass.litematic` and `modded.v3.schem`.

## 6. Integration

- [ ] 6.1 Check end to end in `runClient`: a placement of `modded.v3.schem` with a swap and the setting hidden survives leaving and rejoining the world (swap still keyed by the raw ID, cubes still hidden); exporting `legacy-grass.litematic` writes the stored file byte for byte; `ROADMAP.md` mentions unknown blocks and the DataFixer upgrade in step 4. Verify by screenshots in `runs/client/screenshots/`, the client log and a file hash comparison.
