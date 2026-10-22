# Design

## Context

For motivation see proposal.md, Why. For behaviour see `specs/client/world-comparison` and the deltas for
`client/placement-remote` and `schematic-formats`.

The current state this builds on:
- `GhostMeshes` caches geometry by (content hash, rotation, mirror). It doesn't depend on position, and placements of
  the same version share it. Drawing passes `placement min - camera` as the offset, so moving never rebuilds.
- `GhostCells`, used by locked placements with a layer setting or the in-hand filter:
  - one vertex buffer per block type, quads ordered by layer, with `layerStart[y]` offsets
  - drawing a layer range of a block type is one contiguous range
- `GhostView` gives the placed (rotated, mirrored) block state per local position. Palette entries the game doesn't
  know resolve to air through `SchematicView.resolve`. `structure_void` resolves to the structure void block, which
  has no model and isn't meshed.
- `GhostPipeline` is vanilla's block shader with translucent blending, depth test on and depth write off.
- NeoForge has no client event for single block changes. Block updates from the server arrive as packets that fire no
  event, and the project uses no mixins.
- Both readers fill a multi-region `.litematic` model with palette index 0, which is air, before applying the regions
  (`formats/LitematicaFormat.java`, `Formats/LitematicaFormat.cs`). The conformance generator's `model_from_regions`
  does the same.
- Unit tests run without a Minecraft bootstrap, so game-free logic is kept apart from code that touches
  `BlockState`.

## Goals / Non-Goals

**Goals:**
- Placing or breaking a block never re-tessellates a ghost. A change in the world only updates per-cell data.
- Meshes stay shared and position-free. Only locked placements pay for comparison.
- Every rule for classifying cells and counting progress is game-free and unit-tested.
- Cost per tick is bounded by a config value, whatever the placement size.

**Non-Goals:**
- Comparing block entity contents, such as chest items or sign text.
- Re-culling faces next to positions that became hidden. See Risks.
- Saving comparison results. Comparison starts again on lock and on join.

## Decisions

### D1. Comparison state per locked placement

A `Comparison` object is created when a placement locks and dropped when it unlocks, is removed, or the player leaves
the world or changes dimension. It holds:
- one byte per local position (rotated box, same indexing as `GhostView`) with the state from the spec: ignored,
  matching, missing, wrong block, wrong orientation, wrong water, extra, unknown
- per layer, two counts: positions counted for progress and positions matching
- totals per mistake state
- a flag per 16-block section saying "compared at least once", for the overlay's scanning line

Writing a state goes through one method that updates the counters by diffing the old and new state. That keeps
progress at O(1) per change, and layer progress is a sum over the visible layers.

The game-free part is the state array, the counters and `classify(expected, found)`. `classify` takes small records
(`expected`: ignored / air / block id + orientation key + waterlogged; `found`: air / block id + orientation key +
waterlogged) and returns the state, in the priority order from the spec. The game-facing part builds those records
from `BlockState`s.

Alternative: compare `BlockState` objects directly. Rejected: it isn't testable without a bootstrap, and it would
count fence connections and stair shapes as mistakes.

### D2. Expected states and ignored cells

The expected state comes from `GhostView`, so it is already rotated and mirrored. A position is ignored when its raw
palette string is `minecraft:structure_void` or doesn't parse. A per-placement palette flag records this, computed
next to `GhostView`'s palette. This keeps "unknown block" apart from real air, which `SchematicView.resolve` merges.

The orientation key is the tuple of values for a fixed list of `Property` objects: `FACING`, `HORIZONTAL_FACING`,
`FACING_HOPPER`, `VERTICAL_DIRECTION`, `AXIS`, `HORIZONTAL_AXIS`, `ROTATION_16`, `HALF`, `DOUBLE_BLOCK_HALF`,
`SLAB_TYPE`, `DOOR_HINGE`, `ATTACH_FACE` and `ORIENTATION`. It matches by property identity, not by name. Chests also
have a property named `type`, for single, left and right, and that one depends on neighbours. Waterlogging is
`WATERLOGGED` on its own. Expected keys are computed once per palette entry. Found keys are cached per world
`BlockState`, which is an identity map, so a comparison costs one lookup and a few int compares.

### D3. The scanner

One scanner per session runs in `ClientTickEvent.Post` over all comparisons in the current dimension. It has three
lanes:

```
 reach zone   every tick: cells within 6 blocks of the player's eyes          (~2.2k cells)
 near ring    sections within 32 blocks, round-robin, nearest first           (one pass per 3 s, at most 3/4)
 far ring     all other sections, round-robin, unknown sections first         (the rest)
```

- The reach zone covers the player's own placing and breaking, including the server correcting a predicted block.
  It needs no event: a block the player places is at most about 5 blocks away, and this zone is rechecked every tick.
- The near and far rings share the configurable budget `comparison.cellsPerTick` (default 20 000). The near ring
  takes what one pass every 60 ticks needs, capped at three quarters, and leaves the rest to the far ring, so a large
  placement finishes its first pass sooner. It holds at most about 80³ cells, which takes under 3 s at 15 000 cells
  per tick. That meets the spec's 5-second rule.
- "Unknown first" in the far ring covers chunks that load later. A section in an unloaded chunk stays unknown and is
  skipped cheaply with `level.hasChunk`. It is picked up as soon as its chunk is loaded.
- Cells are read section by section through the level's chunk sections, not with one `level.getBlockState` call per
  cell.

Alternatives:
- Listen to `PlayerInteractEvent.RightClickBlock` / `LeftClickBlock` for the player's own changes. Rejected: the
  events fire before the world changes, they miss corrections, and they need a delayed recheck queue anyway. The reach
  zone is simpler and catches more.
- Mixins into the client chunk update handling. Rejected: the project avoids mixins.

### D4. Hiding matched cells: GPU mask (chosen after the spike)

Every locked placement draws from `GhostCells`, so `usesCells` becomes "locked". The cells already split by block
type and layer, so hiding happens at that level. Task 1 was a spike that decided between:

- **C, GPU mask (preferred).**
  - Ghost vertices carry their cell index, in a custom vertex format or in the UV2 slot. Ghosts are always full
    bright, so UV2 holds no information.
  - Each comparison owns a mask with one bit per cell, set while the ghost shows.
  - A custom ghost vertex shader, based on vanilla's block shader, `texelFetch`es the mask and collapses the vertices
    of hidden cells.
  - Scanner changes mark a dirty byte range, uploaded once per frame.
  - Meshes stay shared, and a change costs a byte.
- **B, per-placement index lists (fallback).**
  - At build time, record each cell's quad range in its block-type buffer.
  - Each comparison keeps one index buffer per block type, listing the quads of shown cells, still ordered by layer
    with its own `layerStart`.
  - A change rebuilds the affected block type's index list, at most once per few ticks. That is CPU work in ints, no
    re-tessellation.

The spike passes if, in `runClient`, a 64³ locked placement can hide and show single cells within a frame with no
visible frame-time cost. If blaze3d in 26.1 can't sample a texture in the vertex stage of a custom pipeline, or
can't update one in part, B is used. The specs are the same either way.

Outcome: C. Blaze3d 26.1 has uniform texel buffers (`UniformType.TEXEL_BUFFER`), which vanilla's cloud shader
already reads in the vertex stage, and `CommandEncoder.writeToBuffer` takes a slice of a buffer. The mask is an
`RED8I` texel buffer of one bit per cell (2 MiB at the volume limit). The cell index goes into UV2, so `GhostCells`
writes its vertices itself instead of through `putBakedQuad`, which would mix light emission into UV2.

Alternative: rebuild meshes per section with the world baked in, the way Litematica does. Rejected: it gives up mesh
sharing and the "moving never rebuilds" property, and it re-tessellates on every placed block.

### D5. Mistake boxes

Mistake states show as boxes, not tinted ghosts. A ghost inside a real block fails the depth test and would never be
seen.
- Each comparison keeps a box mesh per 16³ local section.
- A box is a cube inflated by 0.005 with translucent faces in the state's colour. The colour is a constant per state,
  defined next to the outline colours.
- Quads are ordered by layer, with per-layer offsets, the same as `GhostCells`. The visible layer range is a draw
  range.
- A section's box mesh is rebuilt on the render thread when the scanner changes a mistake state in it, or in a
  neighbouring cell across the section border.
- Faces between two boxes of the same state are left out, so a run of mistakes costs its surface, not its volume.
  That matters for terrain or water inside a placement, which can be millions of extra cells.
- They draw after the ghosts, through a position-colour pipeline with the same depth settings as the ghost pipeline.
  Real blocks in front hide them, and they don't hide each other.
- The in-hand filter is not applied to boxes.

### D6. Overlay

`PlacementOverlay` reads the selected placement's comparison:
- progress, or `Scanning...` until every loaded section has been compared once
- layer progress from the per-layer counts over `Layers.visible`
- the four totals in their box colours
- for the crosshair target (`minecraft.hitResult`, a block hit inside the placement's bounds), the found block and
  the expected block, each shown as name plus orientation values, when that cell is in a mistake state

No new keys.

### D7. Multi-region gaps become structure voids

The Java and C# Litematica readers fill the block array with a "not covered" marker. After applying all regions,
they replace the remaining markers with the palette index of `minecraft:structure_void`, adding it to the palette
only when a gap exists. Single-region files, and multi-region files without gaps, keep the same palette and blocks.
The conformance generator's `model_from_regions` produces `structure_void` for gaps, and the expected files are
regenerated. Block counts already leave out `structure_void`, so library, cloud and quota numbers don't change.

## Risks / Trade-offs

- [blaze3d may not allow vertex-stage texture sampling or partial texture updates in a custom pipeline] → the spike
  in task 1 decides before anything else depends on it. B needs nothing new from the GPU API.
- [Faces culled against a neighbour of the same type in the same layer can open a see-through gap. Example: the
  neighbour is wrong glass instead of stone, so a hidden face behind the glass is missing] → accepted. The red box
  marks the spot anyway, and building the right block closes the gap.
- [Memory: up to 16 MiB for the state array, plus the mask texture, per locked placement at the 16.7M-block volume
  limit] → only locked placements have a comparison, and they are dropped on unlock.
- [Magenta noise from terrain and fluids] → by choice, see the proposal. Faces between boxes of the same state are
  left out, so a solid run of mistakes costs its surface.
- [Box faces are culled against neighbours in hidden layers too, so the top layer of a from-to window can show boxes
  without a top face where the layer above has the same mistake] → accepted for translucent boxes. Keeping those
  faces would make vertical runs cost their volume again.
- [Client prediction: a placed block can show matching for a moment and then be reverted by the server] → the reach
  zone sees the revert on the next tick.
- [Changing the model of multi-region files changes their placements' ghosts] → structure voids don't render, the same
  as air. The only visible difference is that comparison ignores the gaps.

## Migration Plan

Placement files are unchanged. Cached `.litematic` files are parsed again on load, so existing placements of
multi-region schematics pick up the voids without migration. Client and cloud readers must ship together for the
conformance set to pass. They live in the same repo and are tested by the same suite.

## Open Questions

- Exact box opacity and the default `comparison.cellsPerTick`. Both are tuned in `runClient` against the spec timings.
