# Design

## Context

For motivation see proposal.md, Why. For behaviour see `specs/client/placement-layers` and the deltas for `client/schematic-placement` and `client/placement-remote`.

The current state this builds on:
- `Placement` is an immutable record. Its state is FOLLOWING or ANCHORED. Position is a doubled pivot, and bounds come from `PlacementTransform`.
- `PlacementController` holds the input rules and has no game dependencies. `RemoteInput` turns keys and clicks into controller calls. Moves repeat while a key is held.
- `GhostMeshes` keeps one mesh per (content hash, rotation, mirror), built off-thread by `GhostMeshBuilder` from a `GhostView`. Building skips fully enclosed blocks and culls faces against neighbours.
- `GhostRenderer` draws each mesh in one call with the ghost opacity as colour modulator, within a block budget.
- `PlacementStore` saves anchored placements as JSON (`version: 1`).
- `PlacementOverlay` builds its lines from the selected placement's state.

## Goals / Non-Goals

**Goals:**
- Changing the layer window, the held item or the hotbar never triggers a rebuild. Each of these is a choice of what to draw.
- Slices never show holes: what is hidden is treated as air while meshing.
- Layer rules (window, follow feet, clamping) stay game-free and unit-tested, like the rest of the controller.

**Non-Goals:**
- Hiding faces between two shown layers or block types. A sliced ghost may show more internal faces than the full one, which is accepted for translucent ghosts.
- X and Z slicing.

## Decisions

### D1. Model

`Placement.State` gains `LOCKED`. `Placement` gains two fields:
- `Layers layers`: a record of mode (`OFF`, `FROM_TO`, `FOLLOW`), `bottom`, `top` and `followOffset`. `bottom = -1` means the window has never been set. `followOffset` defaults to 1 and is limited to 0..16.
- `boolean inHand`.

Both default to `OFF` / `false`, and both survive unlocking, so locking again restores the same view (spec "Unlocking shows the whole ghost"). They take effect only while LOCKED.

Alternative: keep layer state in a separate map keyed by placement ID. Rejected: it would need its own saving and removal rules, while the placement record already covers both.

### D2. Controller additions

New `PlacementController` operations, all acting on the selected placement only:
- `lock()`: ANCHORED to LOCKED.
- `unlock(now)`: two presses within 3 s, like `remove`.
- `cycleLayers(direction, feetLayer)`: switches between OFF, FROM_TO and FOLLOW. The first switch to FROM_TO sets a one-layer window at `clamp(feetLayer)`.
- `moveWindow(direction)`: keeps the window size, stops at 0 and at height - 1.
- `resizeWindow(direction)`: in FROM_TO the top only, never below the bottom or above height - 1. In FOLLOW it changes `followOffset` instead.
- `toggleInHand()`.

Existing rules change:
- `rotate`, `mirror`, `move`, `vertical` and the release path of `use` do nothing for a LOCKED placement.
- `remove` does nothing for a LOCKED placement.

The two-press logic is shared: a pending action (REMOVE or UNLOCK) with a placement ID and a deadline. Selecting another placement clears it, and the overlay reads it to show "press again".

`Layers.visible(placement, feetLayer)` returns the inclusive range of shown layers, or all layers when OFF or not LOCKED. FOLLOW gives `0 .. clamp(feetLayer + followOffset)`. Showing everything below the feet fits the planned world comparison, which hides blocks that are already built correctly. The feet layer is `floor(player y) - placement.y`, computed by the caller each frame.

`RemoteInput` routes the arrow and numpad keys by state. LOCKED goes to the layer operations: up/down moves the window with key repeat, left/right cycles the mode, and + / - resizes. All other states go to the existing movement. L and I are new key mappings in the existing `HOLDING_REMOTE` context.

L is also vanilla's advancements key. While the remote is held, presses of other bindings on the same key as a remote key are dropped at the start of the tick, before vanilla handles them, so L locks without opening the advancements screen.

### D3. Cell meshes: split by block and layer

A second mesh kind, `GhostCells`, holds one GPU buffer per block, where block means block identity with any state. Each buffer's quads are ordered by layer, and an index table gives where each layer starts:

```
 GhostCells (sha256, rotation, mirror)
   oak_planks  : buffer, layerStart[0..h]   -> draw layers a..b = one indexed range
   oak_stairs  : buffer, layerStart[0..h]
   cobblestone : ...
```

Building is one pass over the placed view, y outermost. Each block is tessellated against a cell view that answers air for every position outside its own cell (same block and same layer). Hidden-block culling and face culling then behave as if everything else were air, which is the spec's "never hides geometry wrongly". Quads go to that block's `BufferBuilder`. Since y is outermost, each builder receives its quads in layer order, and the builder records the vertex count where each layer starts.

Drawing a layer range of one block is one `drawIndexed(firstIndex, indexCount)` call. All draws of a placement share one render pass. Their uniforms and the index buffer are written before the pass opens, because 26.1 forbids writes while a pass is open. A frame costs at most one call per shown block type, typically 5 to 50.

`GhostCells` is built off-thread the first time a placement is LOCKED with layers or in-hand on. It is cached by key and freed by the same sweep as the full meshes. Until it is ready, the full mesh draws.

Alternatives:
- Rebuild one clipped mesh when the window changes. Rejected: stepping layers on large builds would lag, and every hotbar scroll would rebuild.
- Clip in a custom shader. Rejected: it can't undo culling, so faces would be missing at slice edges, and it would need a shader of our own.

### D4. Drawing

`GhostRenderer` picks the mesh per placement:
- Not LOCKED, or LOCKED with layers OFF and in-hand off: the full mesh, as today.
- Otherwise `GhostCells`. For each block in it:
  - in-hand off: normal opacity
  - in-hand on and the block matches the main-hand item: normal opacity
  - in-hand on and it matches another hotbar item: normal opacity x the hotbar factor
  - otherwise: skipped

  It draws the visible layer range at that opacity.

An item matches when it is a `BlockItem` whose block equals the cell's block. The hotbar is inventory slots 0 to 8, read each frame. The display uses the player's real items, not the remote check, so it works whatever is held.

LOCKED gets its own outline colour (cyan). The budget still counts the placement's full mesh block count, which is an upper bound for any slice.

### D5. Saving

The placement JSON gains optional fields: `locked` (Boolean), `layers` (`{ mode, bottom, top }`) and `inHand` (Boolean). Missing fields read as ANCHORED, OFF and false, so older files load unchanged and the format version stays 1. LOCKED placements are saved like ANCHORED ones. FOLLOWING placements are still never written.

### D6. Overlay and config

Overlay:
- The LOCKED badge.
- A layer line: `Layers 4-6 of 12`, `Follow feet +1: 0-4 of 12` or `Layers: all`.
- An in-hand line: `In hand: on` or `In hand: off`. The overlay only shows while the remote is held, so naming the held block would always say the remote.
- Hints for LOCKED: arrows up/down for the window, arrows left/right for the mode, numpad + / - for its size, I for in-hand and L for unlock (twice). The pending-unlock note replaces the hints, like the remove note.

New client config entries:
- `layers.hotbarOpacity`: default 0.33, a factor of the ghost opacity, range 0.05 to 1.

## Risks / Trade-offs

- [Cell meshes use more memory than the full mesh, with more boundary faces] → They're built only for locked placements that use layers or in-hand. They're freed when unused and count against nothing new. If memory becomes a problem, build them per window instead.
- [Many draw calls for schematics with many block types] → At most one call per shown block type. In-hand mode cuts this to the hotbar's blocks.
- [Internal faces between shown layers make sliced ghosts look busier] → Accepted (Non-Goals). The full mesh is unchanged for unlocked ghosts.
- [Items that place a different block than their name suggests (for example redstone dust and redstone wire)] → Matching uses the block the item places (`BlockItem.getBlock`), so these match correctly. Items with no block never match.

## Migration Plan

None beyond D5. Older placement files load as unlocked with layers off. Downgrading the mod ignores the new fields.
