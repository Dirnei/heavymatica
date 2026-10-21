# Proposal

## Why

The library can find and download schematics, but they can't be put into the world yet. Placement is the first thing a player does with a schematic, and the simple UI is Heavymatica's main selling point. This change makes placing, moving and managing ghosts work with a held "remote" and a few keys. It also has to work on any server, vanilla included, because the core mod is client-only.

## What Changes

- **Remote:** a configurable vanilla item (default `minecraft:stick`) or any item in the `heavymatica:remotes` item tag counts as the remote. The core mod registers no items. The later `heavymatica_machines` addon can add a real Remote item by adding it to the tag.
- **Keys only while the remote is held:** Heavymatica's placement keys live in their own key-conflict context, so they're active only while a remote is in the main hand. While the remote is held, Heavymatica takes right-click, and the click never reaches the server.
- **Placements as ghosts:** a placement pins a schematic version (cloud or local) at a position, rotation, mirror and dimension, and renders as a translucent ghost of block models. A world can hold many placements; exactly one at most is selected.
- **Two states:**
  - FOLLOWING: the ghost follows the crosshair. Right-click anchors it.
  - ANCHORED: the ghost stays in place. Sneak + right-click releases it back to FOLLOWING.
- **Moving the selected placement:**
  - Arrow keys move it relative to the camera's facing (Shift moves 10 blocks).
  - Numpad + / - move it up and down.
  - R rotates clockwise, Shift + R counter-clockwise.
  - M cycles the mirror setting.
  - Delete removes the placement after a second press to confirm.
  - All of these keys can be rebound.
- **Selecting:** right-clicking a ghost selects it. Right-clicking where no ghost is hit opens the library.
- **Library "Place" action:** places the chosen version as a new selected placement in FOLLOWING.
- **Overlay:** a HUD panel on the left or right side (configurable), shown while the remote is held. It shows the selected placement's name, version, state badge, position, rotation and mirror, plus hints for the keys that work in the current state, using the keys the player actually bound. A compact option shows only the badge.
- **Persistence:** anchored placements are saved on the client per world or server address and come back on rejoin. A placement whose version is not available offline shows as a box outline and reports that it is unavailable.

Out of scope: material swaps and variants, layer views, dragging ghosts with the mouse, an on-screen control pad, the hand-build helper (progress, wrong-block highlighting), sharing placements with other players and any server-side behaviour.

## Capabilities

### New Capabilities
- `client/placement-remote`: what counts as a remote, when Heavymatica takes keys and clicks, the key layout, and the placement overlay.
- `client/schematic-placement`: placements and their states (FOLLOWING / ANCHORED), selection, movement, rotation, mirroring, removal, ghost rendering and client-side persistence.

### Modified Capabilities
- `client/schematic-library`: adds a Place action on a version in the library that starts a new placement.

## Impact

- Code in the `heavymatica` mod only (`src/mod/heavymatica`): new `placement/` and `remote/` packages, a GUI layer, a world render hook, new key mappings, new client config entries and a Place button in `LibraryScreen`.
- Data: the new `heavymatica/placements/` directory in the game directory holds one file per world or server.
- No cloud or API changes. No server-side code, and no registry entries, so vanilla servers keep working.
- Rendering: ghosts add translucent geometry to every frame. Large schematics are limited by a configurable block budget.
- If Litematica is also installed and uses the same tool item, both mods react to it. The remote item is configurable for that reason.
