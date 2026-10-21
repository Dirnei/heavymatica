# Tasks

## 1. Rendering spike

- [x] 1.1 Spike the GPU mask (D4, option C): a custom ghost pipeline based on vanilla's block shader that reads a per-placement R8 mask texture in the vertex stage, keyed by a per-vertex cell index, and a partial texture update path; record the outcome (C or fallback B) in design.md D4; verify in `runClient` that a 64³ locked placement hides and shows single cells within a frame with no visible frame-time cost, or that the API limit forcing B is named

## 2. Formats: multi-region gaps

- [x] 2.1 Make `generate.py`'s `model_from_regions` fill gaps with `minecraft:structure_void`, update the two-region note, add a fixture with air saved inside a region next to a gap, and regenerate `src/conformance/files` and `expected` (D7); verify `git diff` changes only the multi-region expectations' `paletteDigest`, `blockTotal` and `blockCounts` staying the same
- [x] 2.2 Fill uncovered positions with `structure_void` in the Java `LitematicaFormat`, adding it to the palette only when a gap exists (D7); verify `ConformanceTest` and `SchematicModelTest` pass in `./gradlew test`
- [x] 2.3 Make the same change in the C# `LitematicaFormat` (D7); verify the cloud conformance tests pass in `dotnet test`

## 3. Comparison model (game-free)

- [x] 3.1 Add the cell states and `classify(expected, found)` over small game-free records, priority wrong block > wrong orientation > wrong water, extra for air vs non-air, ignored for void or unknown (D1); verify unit tests for every scenario in "Cell states", "Orientation properties" and "The first fix decides the state"
- [x] 3.2 Add the comparison state: byte per cell, per-layer counted / matching counts, totals per mistake state, compared-once flag per section, updated by diffing old and new state (D1); verify unit tests for progress, extra not lowering progress, unknown counting as not done, layer progress over a visible range, and the scanning flag clearing once every loaded section is compared

## 4. Comparing against the world

- [x] 4.1 Build expected keys per palette entry (orientation key from the fixed property list, waterlogged, ignored flag for `structure_void` and unparseable entries) and cache found keys per world `BlockState` (D2); verify in `runClient` that fence connections, stair shape and a double chest's `type` never mark a position orange (unit tests have no Minecraft classes)
- [x] 4.2 Create a comparison when a placement locks and drop it on unlock, remove, dimension change and leaving (D1); verify in `runClient` that `lastFrame()` / a debug line shows comparisons only for locked placements, and that unlocking frees them
- [x] 4.3 Add the scanner with the reach zone, the near and far rings, unknown sections first, section-wise reads and the `comparison.cellsPerTick` config entry (D3); verify in `runClient` that a placed block in a locked placement is classified within one second, another player's change within 32 blocks within 5 s, and a section in a chunk that loads later is compared after it loads, with tick time staying flat on a 256³ placement

## 5. Drawing

- [x] 5.1 Draw every locked placement from `GhostCells` and hide matching and ignored cells with the approach chosen in 1.1, keeping layers and the in-hand filter working on top (D4); verify in `runClient` the scenarios "Locking starts the comparison", "Unlocking stops it", "Placing the right block" and "Breaking a placed block"
- [x] 5.2 Add the per-section mistake box meshes, ordered by layer, coloured red / orange / cyan / magenta, depth-tested like ghosts and drawn in the visible layer range only, ignoring the in-hand filter (D5); verify in `runClient` the "Showing the states" and "Comparison combines with layers" scenarios, including a box hidden behind a wall

## 6. Overlay

- [x] 6.1 Extend `PlacementOverlay` for locked placements with progress or `Scanning...`, layer progress, the four totals in their colours and the expected / found line for a targeted mistake (D6); verify in `runClient` against the overlay scenarios in the `client/placement-remote` delta

## 7. Integration

- [x] 7.1 Build a small house by hand in singleplayer with a locked placement in follow feet: place some blocks right, one wrong, one stair turned, one slab without water where water is expected, and leave grass inside; verify the ghosts disappear as blocks go in, the four colours show where expected, progress rises, and after rejoining the comparison starts again with the same result
- [x] 7.2 Update `ROADMAP.md` step 4 to describe world comparison as done, with progress, and keep the material list as the next item; verify the file reads consistently

## Workflow follow-up

- Commit the implementation once all tasks are done (Conventional Commits, for example `feat(placement): compare locked placements with the world`).
- Archive the change with `/opsx:archive` when asked.
