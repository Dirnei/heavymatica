# client/placement-remote Specification

## Purpose

Lets a player control schematic placements by holding a "remote": which items count as one, which keys and clicks Heavymatica takes while it is held, and the overlay that shows the current state and key hints.

## Requirements

### Requirement: What counts as a remote
An item SHALL count as a remote when it is the configured remote item (default `minecraft:stick`) or is in the item tag `heavymatica:remotes`. The core mod SHALL NOT register any item, so it keeps working on servers without Heavymatica.

#### Scenario: Default remote on a vanilla server
- **WHEN** a player on a vanilla server holds a stick in the main hand
- **THEN** Heavymatica treats it as the remote

#### Scenario: Configured remote
- **WHEN** the configured remote item is `minecraft:blaze_rod` and the player holds a stick
- **THEN** the stick is not a remote and a blaze rod is

#### Scenario: Tagged item
- **WHEN** the server's data puts an item in `heavymatica:remotes` and the player holds that item
- **THEN** Heavymatica treats it as the remote

### Requirement: Placement keys only while the remote is held
Heavymatica's placement keys SHALL act only while a remote is in the main hand and no screen is open.

#### Scenario: Remote not held
- **WHEN** a player holding a sword presses the move key
- **THEN** no placement changes

### Requirement: Default key layout
The placement keys SHALL default to: arrow keys to move horizontally, numpad + and numpad - to move up and down, R to rotate, M to mirror, and V to open the wheel. Remove, lock and unlock, the in-hand filter, cycling the layer setting and the materials screen (which also holds the material list) SHALL have no default key; they are on the wheel. Every placement key SHALL be rebindable in the controls screen under the Heavymatica category.

#### Scenario: Rebound key
- **WHEN** a player rebinds "move up" to Page Up
- **THEN** Page Up moves the selected placement up and numpad + no longer does

#### Scenario: Material list on the wheel
- **WHEN** a player holding the remote presses K with a locked placement selected and default bindings
- **THEN** no screen opens, and picking Materials on the wheel opens the materials screen with the material list's counts

### Requirement: Arrow keys on a locked placement
While the selected placement is LOCKED, the up and down arrow keys and numpad + / - SHALL control its layers instead of moving it, and the left and right arrow keys SHALL do nothing.

#### Scenario: Arrows on a locked placement
- **WHEN** the selected placement is LOCKED in from-to and the player presses the up arrow
- **THEN** the layer window moves up and the placement stays where it is

#### Scenario: Left arrow on a locked placement
- **WHEN** the selected placement is LOCKED and the player presses the left arrow
- **THEN** its layer setting and position do not change

### Requirement: Right-click is taken while the remote is held
While a remote is in the main hand, Heavymatica SHALL handle right-click itself, and the click SHALL NOT be sent to the server or used on a block, entity or item. Left-click and the mouse wheel SHALL keep their normal behaviour.

#### Scenario: Right-click a chest with the remote
- **WHEN** a player holding the remote right-clicks a chest
- **THEN** the chest does not open and Heavymatica handles the click

#### Scenario: Left-click with the remote
- **WHEN** a player holding the remote left-clicks a block
- **THEN** the game behaves as without Heavymatica

### Requirement: Placement overlay
While a remote is in the main hand and no screen is open, the client SHALL show an overlay on the configured side of the screen (default left) with the selected placement's name, version, a state badge (FOLLOWING, ANCHORED or LOCKED), position, rotation and mirror, or "No placement selected" when none is. For a LOCKED placement it SHALL also show the layer setting with its layers and the placement's height, whether the in-hand filter is on, and the shortage lines of its material list.

#### Scenario: Anchored placement
- **WHEN** a player holds the remote and the selected placement `Castle Gate` version 3 is anchored at 128, 64, -40 with rotation 90 and no mirror
- **THEN** the overlay shows `Castle Gate`, `v3`, an ANCHORED badge, `128, 64, -40`, `90` and mirror off

#### Scenario: Remote put away
- **WHEN** the player switches from the remote to another item
- **THEN** the overlay disappears

#### Scenario: Locked placement with layers
- **WHEN** the selected 12-layer placement is LOCKED with layers 4 to 6 and the in-hand filter on
- **THEN** the overlay shows a LOCKED badge, `Layers 4-6 of 12` and that the in-hand filter is on

#### Scenario: Shortages
- **WHEN** the selected locked placement is short 12 oak planks
- **THEN** the overlay shows oak planks with 12 short

### Requirement: Comparison in the overlay
For a LOCKED selected placement, the overlay SHALL also show:
- its progress in percent with matching and counted blocks, or that it is still scanning until every loaded position has been compared once
- its layer progress, when the layer setting is not off
- the number of positions per state: wrong block, wrong orientation, wrong water and extra
- when the crosshair targets a position of the placement in one of those states, the expected and the found block

The crosshair's target SHALL be the ghost target when there is one, and otherwise the real block the crosshair hits.

#### Scenario: Progress and mistakes
- **WHEN** the selected locked placement has 3104 of 5006 blocks matching, 12 wrong blocks, 4 wrong orientations, 2 wrong water and 3 extra
- **THEN** the overlay shows `62%`, `3104 / 5006` and the counts 12, 4, 2 and 3 in red, orange, cyan and magenta

#### Scenario: Just locked
- **WHEN** the player has just locked a placement and not every loaded position has been compared yet
- **THEN** the overlay says the placement is scanning instead of showing a percentage

#### Scenario: Looking at a mistake
- **WHEN** the crosshair targets a position of the selected locked placement that expects oak stairs facing north and holds oak stairs facing east
- **THEN** the overlay shows the expected and the found stairs with their facing

#### Scenario: Anchored placement shows no progress
- **WHEN** the selected placement is ANCHORED
- **THEN** the overlay shows no progress and no counts

#### Scenario: Ghost in front of a mistake
- **WHEN** a missing ghost of the selected locked placement stands between the player and a wrong block of the same placement
- **THEN** the overlay shows no expected and found lines, because the target is the missing ghost

#### Scenario: Looking at an extra block
- **WHEN** the crosshair hits a tall grass plant where the selected locked placement has air
- **THEN** the overlay shows air as expected and the tall grass as found

### Requirement: Overlay key hints
The overlay SHALL list hints for the actions available in the current state and only those. Each hint SHALL show the key the player currently has bound, not the default. It SHALL always show the wheel key as "menu". An action that is on the wheel SHALL only get a hint when the player has bound a key for it.

#### Scenario: Hints follow the state
- **WHEN** the selected placement is FOLLOWING
- **THEN** the hints show anchoring, rotating, mirroring and the menu, and do not show moving with the arrow keys

#### Scenario: Hints show rebound keys
- **WHEN** a player has rebound "move up" to Page Up
- **THEN** the overlay's hint for moving up names Page Up

#### Scenario: Wheel actions without keys
- **WHEN** the selected placement is ANCHORED and the player has default bindings
- **THEN** the hints show `V menu` and no hints for lock or remove

#### Scenario: Bound wheel action
- **WHEN** the player has bound the lock / unlock key to L and the selected placement is ANCHORED
- **THEN** the hints also show L for lock

### Requirement: Compact overlay
The client SHALL offer a compact overlay setting that shows only the selected placement's name and state badge, without details and hints.

#### Scenario: Compact enabled
- **WHEN** compact overlay is on and the player holds the remote
- **THEN** the overlay shows the name and state badge only

### Requirement: Swap count in the overlay
While the overlay shows a placement that has swaps, it SHALL also show how many swaps it has, in every state.

#### Scenario: Swaps
- **WHEN** the selected placement swaps oak stairs and oak planks
- **THEN** the overlay says it has 2 swaps

#### Scenario: No swaps
- **WHEN** the selected placement has no swaps
- **THEN** the overlay shows no swap count

### Requirement: Finishing in the overlay
For a LOCKED selected placement, the overlay SHALL also show, in yellow, how many positions in the visible layers need each finishing step, for every step that any visible position needs. These lines SHALL follow the layer setting, while the counts per mistake state stay over the whole placement. When the crosshair targets a position of the placement that needs finishing, the overlay SHALL show the expected and the found block and the step. The crosshair's target SHALL be the ghost target when there is one, and otherwise the real block the crosshair hits.

#### Scenario: Finishing steps
- **WHEN** the visible layers of the selected locked placement have 3 dirt paths on dirt and 2 stripped oak logs on oak logs
- **THEN** the overlay shows in yellow that 3 blocks need a shovel and 2 blocks need an axe, and no line for a hoe or water

#### Scenario: Nothing to finish
- **WHEN** no position in the visible layers of the selected locked placement needs finishing
- **THEN** the overlay shows no finishing line

#### Scenario: Finishing follows the visible layers
- **WHEN** the selected locked placement shows layers 4 to 6, and layer 2 has 3 dirt paths on dirt and 2 wrong blocks
- **THEN** the overlay shows no shovel line and still counts the 2 wrong blocks in red

#### Scenario: Looking at a block to finish
- **WHEN** the crosshair targets a position of the selected locked placement that expects white concrete and holds white concrete powder
- **THEN** the overlay shows white concrete as expected, white concrete powder as found, and that it needs water

### Requirement: Amounts in the overlay
For a LOCKED selected placement, the overlay SHALL count too many positions in orange next to the other mistake counts. When the crosshair targets a position of the placement that is too many, it SHALL show the expected and found block and how many there are instead of how many are planned. When it targets a position that is not enough yet, it SHALL show the expected and found block and how many more are needed.

#### Scenario: Too many count
- **WHEN** the selected locked placement has 2 positions with too many candles
- **THEN** the overlay shows 2 too many in orange

#### Scenario: Looking at too many candles
- **WHEN** the crosshair targets a position of the selected locked placement that expects 3 white candles and holds 4
- **THEN** the overlay shows the expected and found candles and that there are 4 instead of 3

#### Scenario: Looking at one of three candles
- **WHEN** the crosshair targets a position of the selected locked placement that expects 3 white candles and holds 1
- **THEN** the overlay shows the expected and found candles and that 2 more are needed
