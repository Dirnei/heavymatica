# Tasks

## 1. Model and saving (game-free)

- [x] 1.1 Add `swaps` to `Placement` (immutable sorted map, empty by default) with `PlacementController.swap` / `resetSwap` on the selected placement in any state, swapping to itself meaning reset (D1); verify unit tests for swapping, resetting, swapping while LOCKED, and swapping with nothing selected
- [x] 1.2 Extend `PlacementStore` with the optional `swaps` field (D1); verify unit tests for a placement with two swaps round-tripping and a file without the field loading with no swaps
- [x] 1.3 Add `BlockStates.swapped(state, swaps)`, replacing the block ID by the original ID only and keeping the property text (D2); verify unit tests for a swapped entry, an unswapped entry, and two swaps that must not chain

## 2. Effective palette

- [ ] 2.1 Make `GhostView` take the swaps and resolve swapped entries leniently (target default state plus every original property the target accepts, unknown target → original), include the swaps in `GhostMeshes.Key` and in `WorldComparison` / `matches` (D2, D3); verify in `runClient` with a test schematic of oak planks and stairs that swapping stairs to spruce stairs keeps facing, half and waterlogging, stairs to spruce planks shows plain planks, an unknown target shows the original, two placements without swaps still share one mesh, and on a locked placement the built oak stairs turn red while the material list asks for spruce stairs (runClient check pending)

## 3. Screens, wheel segment and key

- [ ] 3.1 Add the `MATERIALS` wheel entry and `OPEN_MATERIALS` command to `RadialMenu` (before Library in every state, disabled with nothing selected), the unbound `RemoteKeys.MATERIALS_SCREEN` key, and `MaterialsScreen` listing the selected placement's block types by count with target, pick and reset, marking unknown targets as missing, and the key's "select a placement first" action-bar message (D4); verify unit tests for the Materials segment per state and the disabled click / release, and in `runClient` the "Open the screen", "No selection", "Greyed out without a selection", "Bound key" and "Mod removed" scenarios and that the key shows unbound in the controls screen (unit tests pass; runClient check pending)
- [ ] 3.2 Add `BlockPickerScreen` with same-class suggestions first, all blocks with an item, and search by name and ID, applying the choice at once (D4); verify in `runClient` the "Suggestions for stairs" and "Search" scenarios, and that picking the original block resets the swap (runClient check pending)

## 4. Overlay and integration

- [ ] 4.1 Show "N swaps" in `PlacementOverlay` and a materials screen hint only when its key is bound (D5); verify in `runClient` that a placement with two swaps shows "2 swaps" and one without shows nothing, and that the hint appears once the key is bound (runClient check pending)
- [ ] 4.2 Run a scripted session: place, swap stairs and planks, anchor, rejoin, lock, build part of it in the old material, reset one swap; verify swaps survive the rejoin, the comparison and material list follow each change, and the reset brings matches back (runClient check pending)
- [x] 4.3 Update `ROADMAP.md` step 3: material swaps are per block on the placement, variants later, and mark them done; verify the file reads consistently

## Workflow follow-up

- Archive `world-comparison`, `material-list` and `radial-menu` (in that order) before archiving this change.
- Commit the implementation once all tasks are done (Conventional Commits, for example `feat(placement): add material swaps`).
- Archive the change with `/opsx:archive` when asked.
