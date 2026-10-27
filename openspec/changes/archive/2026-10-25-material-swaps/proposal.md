# Proposal

## Why

Placing a schematic as it was saved is often not what the player wants: they have spruce instead of oak, or want
deepslate instead of stone bricks. "Swap materials" is one of the three core verbs on the roadmap and the last open
item of the placement UI (step 3). The player needs to change materials per placement without editing or
re-uploading the schematic.

## What Changes

- **Per-block swaps on a placement.**
  - A placement can replace any block type of its schematic with another block, for example oak stairs → spruce
    stairs.
  - Each swap covers every state of that block type.
  - Swaps don't follow families: swapping oak stairs leaves oak planks and oak slabs as they are.
- **States carry over by property name.** Properties the new block also has keep their value (facing, half,
  waterlogged and so on). Properties it lacks are dropped, and properties only it has take their default.
- **Swaps map original blocks only.** They never chain: oak → spruce plus spruce → birch does not turn oak into birch.
- **Everything follows the swapped blocks:** ghosts, the in-hand filter, the world comparison (including the expected
  block in the overlay) and the material list.
- **Swaps work in every state, locked included.**
  - On a locked placement the comparison starts over with the new blocks, so blocks already built in the old
    material turn red.
  - Resetting a swap brings them back.
- **One materials screen.**
  - A "Materials" segment on the remote's wheel opens a screen for the selected placement. It shows whenever a
    placement is selected, in every state, and greyed out with no selection. Like the other wheel actions it is also
    a rebindable key (works while the remote is held) with no default key.
  - The screen lists every block type of the schematic with its count and what it shows as. Each row has a picker
    and a reset. A search box narrows the rows.
  - While the placement is LOCKED, each row also shows the material list's needed, have and short for the item of
    the block it shows as, sorted by most short first, and the blocks no item places follow under "Can't carry".
    Unlocked, those columns are hidden and the rows are sorted by most used first.
  - The picker offers every block that has an item, with a search box. Blocks of the same kind are suggested first,
    for example other stairs for stairs.
- **The material list screen merges into it.** The separate material list screen, its "Material list" wheel
  segment and its "lock the placement first" message go away. The material list key opens the materials screen
  instead, keeps its binding, and with no selection says to select a placement first.
- **Overlay:** shows how many swaps the selected placement has, when it has any. The "more short" line points to
  the materials screen (its key, or the wheel's Materials segment).
- **Saving:** swaps are saved with the placement. Older placement files load without swaps. A swap to a block the
  game no longer knows, such as one from a removed mod, is ignored and shown as missing in the screen.

Out of scope:
- saving or sharing a set of swaps as a variant
- family-wide swaps
- swapping single positions
- picking a ghost block to jump to its row

## Capabilities

### New Capabilities
- `client/material-swaps`: what a swap is, how states carry over, what follows the swapped blocks, the materials
  screen (including the material list's counts on a locked placement) and the block picker.

### Modified Capabilities
- `client/schematic-placement`: a placement also carries its swaps, and saving includes them.
- `client/placement-remote`: the default key layout lists the materials screen among the actions without a default
  key, and a new requirement has the overlay show the number of swaps.
- `client/radial-menu`: the wheel shows Materials in every state (greyed out with no selection) instead of
  Material list on a locked placement, the material list key becomes the materials key, and the shortage pointer
  names the Materials segment.
- `client/material-list`: the material list screen is removed, since the materials screen replaces it, and the
  overlay's "more short" line names the materials key. Counting, sorting and summing the material list stay as
  they are.

## Impact

- **Mod code only (`src/mod/heavymatica`):**
  - the `placement` package: the placement record, the store, `GhostView`, the mesh cache key, comparison and
    controller
  - the `remote` package: the wheel segment (replacing Material list), the key and the overlay lines
  - the `ui` package: the materials screen, which replaces the material list screen, and the block picker
- **Saved data:** placement files gain an optional `swaps` field. Older files still load.
- **Comes first among the open changes.** `ghost-opacity-wheel` and `block-equivalents` build on this change's
  requirements, so archive it before them: `material-swaps` → `ghost-opacity-wheel` → `block-equivalents`.
- No cloud, API or server changes.
