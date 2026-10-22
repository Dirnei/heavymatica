# Design

## Context

For motivation see proposal.md, Why. For behaviour see `specs/client/material-swaps` and the deltas for
`client/schematic-placement`, `client/placement-remote`, `client/radial-menu` and `client/material-list`.

The current state this builds on:
- **`SchematicModel`:** a palette of canonical state strings (`id[key=value,...]`) plus palette indices per position.
  `BlockStates` (game-free) parses and builds those strings.
- **`GhostView`:** resolves each palette string once through `SchematicView.resolve` (vanilla's
  `BlockStateParser`, air on failure), then mirrors and rotates it. Ghost meshes, cell meshes and `WorldComparison`
  all read blocks through a `GhostView`.
- **`GhostMeshes`:** caches meshes and cells by `Key(sha256, rotation, mirror)`, shared between placements.
- **`WorldComparison.matches`:** compares source, rotation, mirror and bounds. A mismatch makes `Comparisons`
  rebuild the comparison.
- **`Placement`:** an immutable record. `PlacementStore` writes it as JSON with optional fields for newer data
  (`locked`, `layers`, `inHand`).
- **The `material-list` change**, applied before this one, adds `MaterialList` (per-item needed, had and short,
  plus can't carry), `PlacementSession.materials`, `MaterialListScreen` and the `RemoteKeys.MATERIALS` key. This
  change folds that screen into its materials screen.
- **The `radial-menu` change**, applied before this one, moves one-shot remote actions onto a wheel:
  - `RadialMenu` (game-free) decides the segments per selection state, with `Segment.enabled` for greyed-out ones.
  - `RadialMenuScreen` draws them, and `RemoteInput.run` executes the returned `Command`.
  - Wheel actions keep an unbound `KeyMapping` in `RemoteKeys`, and the overlay hints them only when bound.

## Goals / Non-Goals

**Goals:**
- A swap never touches positions, only palette entries, so applying or resetting one costs a palette pass and a mesh
  rebuild, nothing per cell.
- Placements without swaps keep sharing meshes exactly as today.
- String rewriting and state carry-over are game-free where they can be, and unit-tested.

**Non-Goals:**
- Variants (saved or shared swap sets) and family-wide swaps.
- Re-culling faces after a swap changes a block's shape. Meshes are rebuilt from scratch for the new palette anyway.

## Decisions

### D1. Swaps on the placement

- `Placement` gains `Map<String, String> swaps`: original block ID → target block ID. The map is immutable, sorted
  and empty by default.
- `PlacementStore` writes it as an optional `"swaps": {"minecraft:oak_stairs": "minecraft:spruce_stairs"}` and reads
  a missing field as empty, without a file version bump. This follows `locked`, `layers` and `inHand`.
- `PlacementController.swap(blockId, target)` and `resetSwap(blockId)` act on the selected placement in any state.
  Swapping a block to itself counts as a reset.

Alternative: keys by full state string. Rejected: the spec swaps a block type in every state.

### D2. Effective palette

- `BlockStates.swapped(String state, Map<String, String> swaps)` (game-free) replaces the ID and keeps the property
  text. Lookup is by the original ID only, so swaps never chain.
- `GhostView` takes the swaps and resolves each swapped entry leniently:
  - parse the target block
  - start from its default state
  - apply each original property whose name exists on the target and whose value it accepts
  - skip the rest
- An unknown target ID falls back to the original entry. The comparison's "ignored" rule (`CellKeys.expected`)
  sees the swapped string, so a valid swap target is never mistaken for an unknown block.

Alternative: strict parsing of the rewritten string through `BlockStateParser`. Rejected: one property the target
lacks (stairs `shape` on planks) would fail the whole entry.

### D3. Cache key and comparison

- `GhostMeshes.Key` becomes `(sha256, swaps, rotation, mirror)`. Records compare maps by value, so placements with
  equal swap sets still share meshes, and placements without swaps share as before.
- `WorldComparison` builds its `GhostView` with the placement's swaps. `matches` also compares swaps, so a swap on a
  locked placement makes `Comparisons` build a fresh comparison. A fresh comparison starts unknown and rescans
  nearest first, which is the spec's "starts over".
- The material list's per-palette demand is computed from the same view, so it follows automatically.
- The in-hand filter compares `Block`s from the cell meshes, which are built from the swapped view, so it needs no
  change.

### D4. One materials screen and the picker

**Opening it**
- `RadialMenu` has a `MATERIALS` entry and an `OPEN_MATERIALS` command, and loses `MATERIAL_LIST` and
  `OPEN_MATERIAL_LIST`:
  - The entry is listed before Library on the main page in every state, in the slot Material list had on a locked
    placement.
  - With nothing selected it is `enabled = false`: it draws greyed out, a click on it does nothing, and a HOLD
    release on it closes the wheel.
  - `RemoteInput.run` opens `MaterialsScreen` for `OPEN_MATERIALS`.
- The existing `RemoteKeys.MATERIALS` key (`key.heavymatica.materials`, unbound since `radial-menu`,
  `HOLDING_REMOTE`) opens the same screen in every state. With no selection it shows "Select a placement first" in
  the action bar. `MATERIALS_SCREEN` is removed, and so is the "lock first" message.

**Rows** (`MaterialRows`, game-free like `BlockChoices`)
- One row per entry of `SchematicModel.blockCounts()`, which already excludes air and structure void. The counts
  are read once per model, since swaps don't change them.
- Each row carries the original block ID, its total, its swap target or none, and, when locked, the material list
  numbers for the item of the block it shows as. That block is the target, or the original when there is no swap or
  the game doesn't know the target, matching `GhostView`. A shown-as block without an item has no numbers.
- Numbers come from the `MaterialList` row of that item. An item the list doesn't contain is needed 0 times, with
  had from the player's carried items (`PlacementSession.carried`) and 0 short. Rows of blocks that show as the same
  item repeat the same numbers.
- Sort when locked: shortage, then needed, then total, all descending, then block ID. Unlocked: total descending,
  then block ID.
- The "Can't carry" part is `MaterialList.cantCarry()` as before, listed after the rows.

**Screen** (`MaterialsScreen`; `MaterialListScreen` is deleted)
- Columns: block (icon and name), total, arrow, shows as (icon and name, "—", or the ID in red with "missing"),
  then Need, Have and Short only when locked (Short red above 0), then Pick and Reset.
- `tick` rebuilds the rows from the current swaps and `session.materials(placement)`, so the screen follows the
  world, the inventory and swaps. Opening it asks the session to count the player's items again.
- A fixed pool of Pick and Reset buttons, one per visible line, is pointed at the rows in view after each scroll,
  search or refresh, so typing never rebuilds widgets. Search matches the name or ID of the block or its target, and
  of can't-carry blocks.
- The draggable scroll bar, the loading message, and not pausing the game stay as in the merged screens.

**Picker**
- `BlockPickerScreen` lists every block in `BuiltInRegistries.BLOCK` whose `asItem()` is not air, sorted by name.
- Blocks with the same `getClass()` as the original come first under "Suggested".
- A search box filters by display name and ID. Choosing a block applies it and returns to the materials screen.

Alternative: keep two screens and link them. Rejected by the player: swapping a material and seeing what it costs
belong in one table.

### D5. Overlay

`PlacementOverlay` adds "N swaps" under rotation and mirror when the selected placement has any. The materials key
gets a hint only when bound, like the other wheel actions, in every state. The "+N more short" line names the
materials key when bound and otherwise the wheel key and the Materials segment. The expected block in the
comparison lines already comes from `WorldComparison.expectedAt`, which reads the swapped view.

## Risks / Trade-offs

- [A swap on a large placement rebuilds its meshes and restarts its comparison] → the same cost as placing it fresh.
  Meshes build off-thread, and the old mesh keeps drawing until the new one is ready.
- [Two placements of the same version with different swaps no longer share meshes] → accepted. Placements without
  swaps share as before.
- [Lenient carry-over can produce surprising states for unrelated blocks (a door's `half` kept on a tall plant)] →
  only properties that are valid for the target are kept, so the result is always a valid state. Odd results are
  visible right away and undone with reset.
- [Blocks of the same class are a rough notion of "same kind". Some modded blocks share generic classes] → it only
  orders suggestions, and every block stays pickable.
- [Rows that show as the same item repeat its numbers, so summing the Need column over-counts] → accepted. The
  numbers answer "what do I fetch for this row", and the overlay's shortage lines stay per item.
- [The merged screen's row is per block, but the material list is per item. A door's two halves or a double slab
  are one block row with the item's numbers] → the numbers come straight from the material list, which already
  handles those rules.

## Migration Plan

Placement files gain an optional field and older files load unchanged. A player who bound the material list key
keeps that binding, and it now opens the materials screen. Archive this change before `ghost-opacity-wheel` and
`block-equivalents`, which build on its requirements.
