# Proposal

## Why

Mistake boxes show through real blocks. On a large build, every buried mistake and the back of every box glows through
the blocks in front of it, so the view turns into stacked, layered colour that is hard to read. The mistake pointer now
finds mistakes that are out of sight, so the see-through boxes are no longer needed.

## What Changes

- Mistake boxes only show where the player can see them directly. A box behind a real block no longer shows, not even
  faintly.
- A box follows the outline of the block that is there, so a box around leaf litter is flat instead of a full cube.
- Ghosts no longer hide boxes, so standing inside a ghost structure keeps the boxes around you visible.
- Box colours, which states get a box, the layer filter and the mistake pointer stay as they are.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client/world-comparison`: the "Showing the states" requirement no longer lets boxes show through real blocks; the
  "Box behind a wall" scenario is reversed.

## Impact

- Client rendering of mistake boxes in the placement package: the box drawing and the render pipeline used for boxes
  hidden behind blocks.
- No change to the comparison, the cloud, settings or saved data.
