# Design

## Context

For motivation see proposal.md, Why. For behaviour see `specs/client/material-swaps` and the deltas for
`client/schematic-placement` and `client/placement-remote`.

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
- **The `material-list` change**, applied before this one, adds `MaterialListScreen` and its key. This change
  follows the same screen pattern.
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

### D4. Materials screen and picker

**Opening it**
- `RadialMenu` gains the `MATERIALS` entry and an `OPEN_MATERIALS` command:
  - The entry is listed before Library on the main page in every state.
  - With nothing selected it is `enabled = false`: it draws greyed out, a click on it does nothing, and a HOLD
    release on it closes the wheel.
  - `RemoteInput.run` opens `MaterialsScreen` for `OPEN_MATERIALS`.
- `RemoteKeys.MATERIALS_SCREEN` (`key.heavymatica.materials_screen`, unbound, `HOLDING_REMOTE` context) opens the
  same screen. With no selection it shows an action-bar message.

**Materials screen**
- Rows come from `SchematicModel.blockCounts()`, which already excludes air and structure void and sorts by count.
- Each row shows the original block (icon and name), its count, an arrow, and the target (icon and name, or "—").
  Each row has a pick button and a reset button. A target the game doesn't know shows its ID in red with "missing".

**Picker**
- `BlockPickerScreen` lists every block in `BuiltInRegistries.BLOCK` whose `asItem()` is not air, sorted by name.
- Blocks with the same `getClass()` as the original come first under "Suggested".
- A search box filters by display name and ID. Choosing a block applies it and returns to the materials screen.

Both screens follow the list and scrolling style of `MaterialListScreen` and don't pause the game.

### D5. Overlay

`PlacementOverlay` adds "N swaps" under rotation and mirror when the selected placement has any. The materials
screen key gets a hint only when bound, like the other wheel actions. The expected block in the comparison lines
already comes from `WorldComparison.expectedAt`, which reads the swapped view.

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

## Migration Plan

Placement files gain an optional field and older files load unchanged. Archive `world-comparison`, `material-list`
and `radial-menu` before this change, in that order, since they all modify the key layout and overlay requirements
and this delta builds on their text.
