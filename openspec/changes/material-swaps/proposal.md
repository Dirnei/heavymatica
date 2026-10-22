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
- **Materials screen.**
  - A new "Materials" segment on the remote's wheel opens a screen for the selected placement. It shows whenever a
    placement is selected, in every state, and greyed out with no selection. Like the other wheel actions it is also
    a rebindable key (works while the remote is held) with no default key.
  - The screen lists every block type of the schematic with its count and its current swap. Each row has a picker
    and a reset.
  - The picker offers every block that has an item, with a search box. Blocks of the same kind are suggested first,
    for example other stairs for stairs.
- **Overlay:** shows how many swaps the selected placement has, when it has any.
- **Saving:** swaps are saved with the placement. Older placement files load without swaps. A swap to a block the
  game no longer knows, such as one from a removed mod, is ignored and shown as missing in the screen.

Out of scope:
- saving or sharing a set of swaps as a variant
- family-wide swaps
- swapping single positions
- picking a ghost block to jump to its row

## Capabilities

### New Capabilities
- `client/material-swaps`: what a swap is, how states carry over, what follows the swapped blocks, the Materials
  segment on the wheel, the materials screen and the block picker.

### Modified Capabilities
- `client/schematic-placement`: a placement also carries its swaps, and saving includes them.
- `client/placement-remote`: the default key layout lists the materials screen among the actions without a default
  key, and the overlay shows the number of swaps.
- `client/radial-menu` is not modified as a delta, since it only exists in the unarchived `radial-menu` change. The
  Materials segment is specified in `client/material-swaps` as an addition to the wheel's segment lists.

## Impact

- **Mod code only (`src/mod/heavymatica`):**
  - the `placement` package: the placement record, the store, `GhostView`, the mesh cache key, comparison and
    controller
  - the `remote` package: the wheel segment, the key and the overlay line
  - the `ui` package: the materials screen and the block picker
- **Saved data:** placement files gain an optional `swaps` field. Older files still load.
- **Depends on `world-comparison`, `material-list` and `radial-menu`.** All three modify the key layout or overlay
  requirements, and this delta builds on their text. Archive order: `world-comparison` → `material-list` →
  `radial-menu` → `material-swaps`.
- No cloud, API or server changes.
