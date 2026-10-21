# Proposal

## Why

A full ghost is good for positioning a build, but it gets in the way of building it: you can't see which layer comes next or where the block in your hand goes. Building by hand is a core use case, and roadmap step 4 (the hand-build helper) needs a notion of "the layer I'm building". This change lets the player lock a placed ghost and then slice it by layer and by the blocks they're holding.

## What Changes

- **Locking:** a third placement state, LOCKED. L locks an ANCHORED placement. Pressing L twice within 3 seconds unlocks it again. A locked placement can't be moved, rotated, mirrored, released or removed.
- **Layer setting** per placement, on the Y axis only, changeable only while locked:
  - **off:** the whole ghost.
  - **from-to:** a window of layers. The up / down arrows move the window. Numpad + / - grow or shrink it at the top.
  - **follow feet:** layers from the bottom up to the player's feet plus an offset (default +1, configurable).
  - The left / right arrows cycle between the three.
- **In-hand filter** per placement, toggled with I while locked. It shows only the block the player holds at normal opacity, and the other hotbar blocks dimmed. When the held item isn't a placeable block, only the hotbar blocks show, dimmed. It combines with the layer setting.
- **Display follows any held item:** layer slicing and the in-hand filter apply whatever the player holds. Only the keys still need the remote.
- **Overlay:**
  - a LOCKED badge
  - the current layer window or follow-feet layer, for example `Layers 4-6 of 12`
  - whether the in-hand filter is on
  - key hints for the locked state, including "press L again to unlock"
- **Saving:** the locked state, layer setting, layer window and in-hand flag are saved with the placement.
- **BREAKING (saved data):** placement files gain fields. Older files load as unlocked, layer setting off.

Out of scope: X and Z axis slicing, highlighting wrong or missing blocks and material lists (hand-build helper, step 4), and material swaps.

## Capabilities

### New Capabilities
- `client/placement-layers`: layer settings (off, from-to, follow feet), the in-hand filter, how they combine, and when they apply.

### Modified Capabilities
- `client/schematic-placement`: adds the LOCKED state and locking. Rotating, mirroring and removing no longer apply to locked placements. Saving includes the lock and layer settings.
- `client/placement-remote`: default keys gain L (lock) and I (in-hand), and the arrows and numpad + / - get layer meanings while locked. The overlay shows the LOCKED badge, the layer and in-hand state.

## Impact

- Mod code only (`src/mod/heavymatica`):
  - the `placement` package: state, controller, store, ghost meshes and renderer
  - the `remote` package: keys, input and overlay
  - client config: follow offset and hotbar opacity
- **Rendering:** ghost meshes are split by block type and layer, so slicing and filtering never rebuild. That means more, smaller meshes per placement.
- **Saved data:** the placement JSON gains fields. Older files still load.
- No cloud, API or server changes.
