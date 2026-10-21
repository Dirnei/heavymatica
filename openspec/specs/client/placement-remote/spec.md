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
The placement keys SHALL default to: arrow keys to move horizontally, numpad + and numpad - to move up and down, R to rotate, M to mirror and Delete to remove. Every placement key SHALL be rebindable in the controls screen under the Heavymatica category.

#### Scenario: Rebound key
- **WHEN** a player rebinds "move up" to Page Up
- **THEN** Page Up moves the selected placement up and numpad + no longer does

### Requirement: Right-click is taken while the remote is held
While a remote is in the main hand, Heavymatica SHALL handle right-click itself, and the click SHALL NOT be sent to the server or used on a block, entity or item. Left-click and the mouse wheel SHALL keep their normal behaviour.

#### Scenario: Right-click a chest with the remote
- **WHEN** a player holding the remote right-clicks a chest
- **THEN** the chest does not open and Heavymatica handles the click

#### Scenario: Left-click with the remote
- **WHEN** a player holding the remote left-clicks a block
- **THEN** the game behaves as without Heavymatica

### Requirement: Placement overlay
While a remote is in the main hand and no screen is open, the client SHALL show an overlay on the configured side of the screen (default left). It SHALL show the selected placement's name, version, a state badge (FOLLOWING or ANCHORED), position, rotation and mirror, or "No placement selected" when none is.

#### Scenario: Anchored placement
- **WHEN** a player holds the remote and the selected placement `Castle Gate` version 3 is anchored at 128, 64, -40 with rotation 90 and no mirror
- **THEN** the overlay shows `Castle Gate`, `v3`, an ANCHORED badge, `128, 64, -40`, `90` and mirror off

#### Scenario: Remote put away
- **WHEN** the player switches from the remote to another item
- **THEN** the overlay disappears

### Requirement: Overlay key hints
The overlay SHALL list hints for the actions available in the current state and only those. Each hint SHALL show the key the player currently has bound, not the default.

#### Scenario: Hints follow the state
- **WHEN** the selected placement is FOLLOWING
- **THEN** the hints show anchoring, rotating, mirroring and cancelling, and do not show moving with the arrow keys

#### Scenario: Hints show rebound keys
- **WHEN** a player has rebound "move up" to Page Up
- **THEN** the overlay's hint for moving up names Page Up

### Requirement: Compact overlay
The client SHALL offer a compact overlay setting that shows only the selected placement's name and state badge, without details and hints.

#### Scenario: Compact enabled
- **WHEN** compact overlay is on and the player holds the remote
- **THEN** the overlay shows the name and state badge only
