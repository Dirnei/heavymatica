# Tasks

## 1. Counting (game-free)

- [x] 1.1 Add `CellState.needsItem()` (missing, wrong block, unknown), make `ComparisonState.set` return the previous state, and add `MaterialCounts` with per-layer needed and can't-carry counters, `add`, `remove` and `sum(from, to)` (D2); verify unit tests for each state's `needsItem`, sums over a layer range, and add/remove pairs returning to zero
- [x] 1.2 Add `MaterialList` rows (item, needed, had, short clamped at 0), sorted by short, then needed, then key, with can't-carry rows kept apart and the top-3 / "+N more" selection for the overlay (D4); verify unit tests for sorting, clamping, nothing short, and more than three short items

## 2. Demand and carried items

- [x] 2.1 Compute a `Demand` per expected palette entry (upper halves and bed heads none, no item → can't carry, double slab 2, candles / pickles / snow layers by amount) and keep `MaterialCounts` current in `WorldComparison`: full demand at creation, then add or remove whenever `needsItem()` flips (D1, D2); verify in `runClient` with a test schematic holding a door, a bed, a double slab, 3 candles, 5 snow layers and a water source that a fresh lock needs exactly 1 door, 1 bed, 2 slabs, 3 candles, 5 snow layers and lists water under can't carry
- [x] 2.2 Add `CarriedItems`: inventory, hotbar and offhand counts plus a read-only walk of `Capabilities.Item.ITEM` through `ItemAccess.forPlayerSlot` / `forHandlerIndex` up to depth 3, at most every 10 ticks, with failing handlers caught (D3); verify in `runClient` that 10 loose oak planks plus a shulker box with 64 count as 74, a bundle's contents count, and a chest in the world does not

## 3. Overlay and screen

- [x] 3.1 Add the shortage lines to `PlacementOverlay` for a locked placement, built from `MaterialCounts.sum` over `Layers.visible` and `CarriedItems` (D5); verify in `runClient` the overlay scenarios "Several items short", "Nothing short" and "Shortages", and that switching to a from-to window or climbing in follow feet changes the lines while the in-hand filter does not
- [x] 3.2 Add the K key (`RemoteKeys.MATERIALS`, rebindable, Heavymatica category) and `MaterialListScreen` with item icon, needed, have, short (red above 0), a can't-carry section, scrolling, a live refresh every 10 ticks, and the "lock first" action-bar message (D5); verify in `runClient` the "Open the list", "Not locked" and "Picking up items while it is open" scenarios and that K shows in the controls screen

## 4. Integration

- [x] 4.1 Run a scripted session: lock a placement, check the list against the placed blocks, place the right block for a red position and see its item leave the list, fetch planks into a shulker box and see the shortage drop; verify the logged list and screenshots match the spec scenarios
- [x] 4.2 Update `ROADMAP.md` step 4 to describe the material list as done and point at the next step; verify the file reads consistently

## Workflow follow-up

- Archive `world-comparison` before archiving this change.
- Commit the implementation once all tasks are done (Conventional Commits, for example `feat(placement): add material list`).
- Archive the change with `/opsx:archive` when asked.
