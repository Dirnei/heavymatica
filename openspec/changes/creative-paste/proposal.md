## Why

In creative single player, building a schematic block by block is busywork: players want the build in the world now, to
look at it, test redstone or keep building around it. Litematica pastes in creative; Heavymatica can do the same almost
for free, since in single player the client runs the world's server in the same process.

## What Changes

- **Paste:** in creative mode in single player (the player's own world, also when opened to LAN), an anchored or locked
  placement can be pasted into the world as its ghost shows it: with its rotation, mirror and material swaps, and with
  block-entity data such as chest contents and sign text. Unknown blocks are skipped. Entities are not part of the model
  and are not pasted.
- Three modes, picked on the wheel: **Replace all** (afterwards the area matches the plan, air included), **Only into air**
  (never removes a block) and **Skip air** (places the planned blocks, keeps the world where the plan has air).
- Pasting does not trigger neighbour updates, so sand doesn't fall, water doesn't flow and redstone stays as planned
  while the paste runs. Large pastes run over several ticks with progress in the overlay, so the game never freezes.
- **Undo paste:** the last paste can be undone while the player stays in that world; it restores every block and block
  entity the paste changed.
- **Wheel:** a Paste segment on anchored and locked placements opens a sub-wheel with the three modes; an Undo paste
  segment shows while an undo is available. Neither shows outside creative single player.
- Multiplayer paste stays with the machines addon's server companion (step 7).

## Capabilities

### New Capabilities

- `client/creative-paste`: pasting a placement into the world in creative single player, its modes, pacing and undo.

### Modified Capabilities

- `client/radial-menu`: Paste segment, paste mode sub-wheel and Undo paste segment.

## Impact

- Mod: a new `paste` package working on the integrated server (`Minecraft.getSingleplayerServer()`), using
  `placement/GhostView`'s resolved states and the model's block entities; `remote/RadialMenu` and `RemoteInput`, the
  placement overlay and the lang file.
- No cloud, web, format or dedicated-server changes.
