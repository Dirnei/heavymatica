# Proposal

## Why

Blocks the game can't resolve vanish from ghosts without a word. A block from a mod that isn't installed, a block from
an older Minecraft version whose name has changed (1.20.2's `minecraft:grass` is now `minecraft:short_grass`), and even
a known block with one stale property value (an old wall's `north=true`) all turn into air. They draw nothing, never
count, never show in the material list, and a placement can finish with holes in it. Litematica upgrades old schematics
with Minecraft's DataFixer, so files shared through the cloud will look broken in Heavymatica that look fine there.

## What Changes

- **Old schematics are upgraded in the game.** The client runs each palette entry through Minecraft's DataFixer from
  the file's data version to the game's, when it builds a ghost, a comparison, a thumbnail or the library details. The
  file, its content hash, uploads, exports and the cloud stay on the names as written.
- **Known blocks always resolve.** Properties a block doesn't have and values it doesn't accept fall back to the
  block's defaults instead of making the whole block unknown.
- **Unknown blocks stay visible.** A position whose block the game still doesn't know shows as Minecraft's
  purple-and-black missing-texture cube, in every placement state. The crosshair targets it, and the HUD names its ID.
- **Comparison says "can't check".** Such positions get their own state. They don't count toward progress, the overlay
  counts them, and a placement still finishes (sound and toast) when everything checkable is done; the toast mentions
  how many couldn't be checked.
- **The player is told and can fix it.** The overlay, the library details, the material list and the materials screen
  name unknown block types. Unknown rows in the materials screen can be swapped like any other, so a missing modded
  block can be replaced by hand. Saved swaps keep the block ID as written in the file.
- **Show or hide unknown blocks from the wheel.** A new client setting `ghost.showUnknownBlocks` (default on) is toggled
  by a wheel segment that appears only when the selected placement has unknown blocks. Hiding stops the drawing only;
  counts and list rows stay.
- Thumbnails rendered on upload use the same upgrade and leniency and leave unknown blocks out.

## Capabilities

### New Capabilities

- `client/block-resolution`: how the client turns a schematic's block states into game blocks: DataFixer upgrade,
  lenient properties, what counts as unknown, the missing-texture placeholder and the show/hide setting.

### Modified Capabilities

- `client/world-comparison`: unknown positions become "can't check" instead of ignored, and finishing tells how many
  couldn't be checked.
- `client/placement-remote`: the overlay line for unknown blocks.
- `client/ghost-targeting`: shown placeholders are targetable, and the HUD names the unknown block.
- `client/material-list`: an "unknown blocks" part.
- `client/material-swaps`: unknown rows in the materials screen, marked and swappable.
- `client/schematic-library`: the details show resolved block names and mark unknown ones.
- `client/schematic-import`: upload thumbnails use the same resolution and leave unknown blocks out.
- `client/radial-menu`: the Unknown blocks segment.

## Impact

- Mod only: `placement` (`GhostView`, `CellKeys`, `WorldComparison`, ghost drawing), `thumbnail`, `targeting`,
  `remote`, `ui`, `HeavymaticaConfig` and the language file.
- Uses the DataFixer that ships with Minecraft; no new dependencies.
- No change to the schematic formats, the conformance set, the cloud, the API or saved placements. Existing swaps keep
  working, as they are already keyed by the original block ID.
- Block-entity data is not upgraded: ghosts don't read it.
