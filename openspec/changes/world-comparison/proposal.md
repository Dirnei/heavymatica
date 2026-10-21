# Proposal

## Why

A locked ghost shows the whole build, so once building starts it no longer tells the player what is left to place or
what went wrong. This is the core of the hand-build helper (roadmap step 4). A locked placement should compare itself
with the world: hide what is already built, flag every mistake by colour and show how far the build is.

## What Changes

- **World comparison while LOCKED.** Every cell of a locked placement is compared with the world block at its position.
  Comparison runs only while the placement is locked. An unlocked placement shows in full, as today.
- **What the player sees:**
  - **Matching blocks** are hidden.
  - **Missing blocks** (the world is air there) show as the normal ghost.
  - **Mistakes** are marked with a coloured box over the real block:
    - **red:** wrong block
    - **orange:** right block, wrong orientation
    - **cyan:** right block and orientation, wrong waterlogging (it needs water, or it has water it shouldn't)
    - **magenta:** a block where the schematic has air. Terrain, plants and fluids count too.
  - When a block is wrong in several ways, the colour shows the first thing to fix: red, then orange, then cyan.
- **"Orientation"** means the placement properties: facing, axis, rotation, half, slab type, door hinge and attach face.
  Other state differences (connections, power, age and so on) count as matching.
- **Not compared:** cells whose schematic block is `structure_void` or a block the game doesn't know. These never show
  a colour and don't count toward progress.
- **Layers:** comparison respects the layer setting. Matching blocks are hidden in the visible layers, and boxes show
  only in the visible layers. The in-hand filter applies to ghosts, never to boxes, so mistakes always show.
- **Staying current:** the client notices blocks placed and broken by the player at once, and other world changes
  (other players, explosions, chunks loading) within a few seconds. It does this without mixins.
- **Overlay**, for the selected locked placement:
  - overall progress, for example `62% (3104 / 5006)`
  - progress of the visible layers when a layer setting is on
  - a count per mistake colour
  - expected and found block for a marked block under the crosshair
- **Formats:** in a multi-region `.litematic` file, the cells between regions are read as `structure_void` instead of
  air. Comparison then doesn't flag the terrain there. Reading changes on the client and in the cloud together, and
  the shared conformance set is updated. Block counts don't change.

Out of scope: material list against the inventory, material swaps, server-side comparison and the machines addon's
Material Monitor.

## Capabilities

### New Capabilities

- `client/world-comparison`: comparing a locked placement with the world, covering:
  - the cell states and their colours
  - which block-state properties count
  - ignored cells
  - how it combines with layers and the in-hand filter
  - how quickly world changes show
  - how progress is counted

### Modified Capabilities

- `client/placement-remote`: the placement overlay gains the progress lines, counts per colour and the expected /
  found line for a locked placement.
- `schematic-formats`: in multi-region Litematica files, positions outside every region become `structure_void`
  instead of air.

## Impact

- **Mod (`src/mod/heavymatica`):**
  - the `placement` package: a per-placement comparison state, a budgeted world scanner, hiding matched cells in ghost
    drawing, and a coloured box overlay per section
  - the `remote` package: overlay lines
  - the `formats` package: Litematica reader
  - client config: scanner budget
- **Cloud (`src/cloud`):** the Litematica reader produces the same model change. Nothing changes in the API or in
  stored data.
- **Conformance (`src/conformance`):** the generator's region model and the expected output for the multi-region
  cases.
- **Rendering:** locked placements carry a per-cell mask. A spike decides between a GPU mask and per-placement index
  lists, see the design.
- No new saved fields. Comparison is derived from the world each session.
