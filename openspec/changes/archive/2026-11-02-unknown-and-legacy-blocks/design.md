# Design

## Context

- A schematic reaches the game as a `SchematicModel`: palette entries are state strings exactly as written in the file
  (`minecraft:grass[snowy=false]`) plus `dataVersion()`. The cloud and the conformance set depend on that model staying
  raw (see `schematic-formats`).
- Palette entries become game states in three places, each in its own way:
  - `GhostView` (ghosts, comparison, targeting): unswapped entries go through `SchematicView.resolve`, which is
    `BlockStateParser.parseForBlock` and returns air on *any* failure, so one bad property value loses a known block.
    Swapped entries use `GhostView.lenient`, which already keeps what fits.
  - `SchematicView` (upload thumbnails): the same strict `resolve`.
  - The library details, the materials screen and the material list: by block ID through the registry.
- `CellKeys.expected` turns an air result from a non-air ID into `Expected.IGNORE`. So comparison already doesn't flag
  unknown blocks; they just disappear and don't count.
- Swaps are a map from the original block ID (raw, as in the file) to a target ID, applied with `BlockStates.swapped`
  on the raw string.
- Ghost drawing: `GhostMesh` (following / anchored, buffers per texture) and `GhostCells` (locked, per block type and
  texture, with a per-block alpha function and a per-cell mask from `ComparisonState`). Both are built on a worker
  from a `GhostView` and cached per view key (model, rotation, mirror, swaps).
- Minecraft's DataFixer is on the client classpath (`DataFixers.getDataFixer()`, `References.BLOCK_STATE`,
  `NbtOps`). Unit tests don't bootstrap Minecraft.

## Goals / Non-Goals

**Goals:**
- One resolver that every place uses, so ghost, comparison, thumbnail, details and lists always agree on what a
  palette entry is.
- Toggling `ghost.showUnknownBlocks` is a draw-time switch: no mesh rebuild, no rescan.
- Resolution logic (leniency, swap order, unknown detection) is unit-testable without a game.

**Non-Goals:**
- Upgrading block-entity data. Ghosts and thumbnails don't read it.
- Downgrading files from newer versions. The DataFixer can't, and their new blocks just stay unknown.
- Pre-flattening files whose palette is numeric IDs (MCEdit `.schematic`). The supported formats store names, and the
  DataFixer handles old names.
- Upgrading anything in the cloud. The website keeps the names as written.

## Decisions

### D1. One `BlockResolver`, cached per data version and entry

A new `blocks.BlockResolver` maps `(raw entry, file data version, swaps)` to a `Resolved`: either a game `BlockState`
or *unknown* with the ID it would show as. Steps:

```
raw entry --upgrade--> upgraded entry --swap by RAW id--> shown entry --lenient--> BlockState | unknown
```

1. **Upgrade.** Only when the data version is known and older than the game's. The entry becomes a `CompoundTag`
   `{Name, Properties}`, goes through `update(References.BLOCK_STATE, ...)`, and is read back. Results are cached in
   a process-wide map keyed by (data version, raw entry), since the same palettes recur across views, rotations and
   swaps.
2. **Swap.** Looked up by the *raw* block ID (the saved key). The target ID takes the upgraded entry's properties,
   as swaps already carry properties over by name.
3. **Resolve leniently.** A known ID gets `lenient` (moved from `GhostView` into the resolver). An unknown ID is
   unknown. `SchematicView.resolve` and the strict parse go away.

The upgrade step sits behind a small `StateUpgrader` interface. The game uses the DataFixer one; tests use a table
fake. That keeps steps 2 and 3 and their order covered by plain unit tests.

*Alternatives:* upgrading on import (rewrites the file and its hash, and breaks "export unchanged" and the cloud's
copy); a hand-made rename table shared with the cloud (never matches the DataFixer, and the flattening alone is
hundreds of rows); a Java sidecar for the cloud (discussed and dropped: the game needs the in-process step anyway).

### D2. Swap keys stay raw

Swaps keep keying by the raw ID. An upgraded name depends on the game version (grass, then short_grass, then maybe
something else), so it's no stable key. Saved placements need no migration. The materials screen shows the upgraded
name as "shows as" and keeps the raw ID as the row's identity.

### D3. "Can't check" is a new comparison state

`CellState.CANT_CHECK` and `Expected.Kind.CANT_CHECK` replace the IGNORE that `CellKeys.expected` gives unknown
blocks today. `counted()` stays false, so progress, layer progress and `Finishes` need no change: a placement that is
only unknown blocks has nothing counted and never finishes. `ComparisonState` keeps a total for the new state like the
others; that's the count the finish toast reads. The cell mask shows CANT_CHECK cells, so the placeholder can draw
there. Targeting and the HUD get one new case.

*Alternative:* keep IGNORED and count unknowns separately. That would put a second, parallel notion into the overlay,
the mask and targeting.

### D4. The placeholder is its own buffer in both meshes

The mesh builders emit a full cube for each unknown position, using the missing-texture sprite from the block atlas.
The quads go into a dedicated placeholder buffer (in `GhostMesh`) or cell (in `GhostCells`, with a placeholder key
instead of a `Block`). Faces next to other placeholders or solid known blocks are culled, as for real blocks, to keep
large modded schematics cheap.

At draw time the placeholder buffer's opacity is the ghost opacity when `ghost.showUnknownBlocks` is on and the
in-hand filter is off, and 0 otherwise. That's the same per-block alpha hook `GhostCells.draw` already takes. So the
setting and the filter need no rebuild, and layers work through the existing layer ranges.

*Alternatives:* drawing placeholders like mistake boxes, in immediate mode per frame (that path is fine for a few
boxes, but not for hundreds of cells, and doesn't follow the slice rules); a registered placeholder block (that needs
the mod on the server).

### D5. Unknown summary per view, not per comparison

The overlay line and the wheel segment need the unknown types and positions in every state, also without a
comparison. `GhostView` computes the summary once from the model's palette counts after resolution (types, positions,
per raw ID). The material list's "unknown blocks" part counts CANT_CHECK positions per raw ID over the visible
layers, alongside the per-layer counting the list already does.

### D6. Details resolve by block ID with the version's data version

Cloud versions report block counts by ID only, plus `dataVersion`. The details run each ID through the resolver as
`{Name}` with no properties, which is enough to rename or mark unknown. Local entries do the same from their model.

### D7. Wheel and config

`HeavymaticaConfig` gets `ghost.showUnknownBlocks` (boolean, default true), with a setter that saves the file, like
`ghost.opacity`. `RadialMenu` gets `Entry.UNKNOWN_BLOCKS`, placed after Opacity in FOLLOWING, ANCHORED and LOCKED
(Opacity is only shown when locked) when the selected placement's summary has unknown types. It's not shown in the
capture wheel or without a selection.

## Risks / Trade-offs

- [The first DataFixer call for a data version is slow (rule building)] → Resolution happens where views are already
  built off the game thread, and results are cached per (version, entry). Thumbnails and details resolve the same way.
- [Leniency can hide a real difference: a defaulted property makes the ghost differ from the author's intent] → This
  only happens for values the current game doesn't accept, where there's no right answer. Comparison then flags what
  the world actually differs in.
- [Library details on the website still show raw names, so the game and the website differ for old schematics] →
  Accepted. The file is the truth, and the website stays as written.
- [A modded schematic with thousands of unknown cells adds many cubes] → Culling between placeholders, the existing
  ghost block limit, and hiding through the wheel.

## Migration Plan

None. Saved placements, swaps, the cache and the cloud are unchanged. Unknown blocks become visible after the update,
and the wheel can hide them.
