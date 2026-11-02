## Why

The Unknown blocks toggle sits on the wheel's main ring in every placement state. It's a rarely used switch, and it
takes a ring slot next to the everyday actions. Options is where the wheel keeps its other switches.

## What Changes

- Unknown blocks moves from the main ring into the Options sub-wheel, before Mistakes.
- Following and anchored placements get an Options segment after Opacity, but only when the placement has unknown
  blocks. Their Options sub-wheel holds Unknown blocks alone, since the other options apply only to a locked placement.
- A locked placement keeps Options as before, now with Unknown blocks in it when the placement has some.
- The Options hint now lists examples, so it fits every state.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client/radial-menu`: the Unknown blocks segment moves into Options, and Options appears for following and anchored
  placements that have unknown blocks.

## Impact

- `remote/RadialMenu`: main ring and Options page segments. `RadialMenuTest` cases updated.
- `en_us.json`: the Options hint.
- What toggling unknown blocks does stays the same.
