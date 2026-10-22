# Spec Delta

## MODIFIED Requirements

### Requirement: Default key layout
The placement keys SHALL default to: arrow keys to move horizontally, numpad + and numpad - to move up and down, R to rotate, M to mirror, and V to open the wheel. Remove, lock and unlock, the in-hand filter, the material list, cycling the layer setting and the materials screen SHALL have no default key; they are on the wheel. While the selected placement is LOCKED, the up and down arrow keys and numpad + / - SHALL control its layers instead of moving it, and the left and right arrow keys SHALL do nothing. Every placement key SHALL be rebindable in the controls screen under the Heavymatica category.

#### Scenario: Rebound key
- **WHEN** a player rebinds "move up" to Page Up
- **THEN** Page Up moves the selected placement up and numpad + no longer does

#### Scenario: Arrows on a locked placement
- **WHEN** the selected placement is LOCKED in from-to and the player presses the up arrow
- **THEN** the layer window moves up and the placement stays where it is

#### Scenario: Material list on the wheel
- **WHEN** a player holding the remote presses K with a locked placement selected and default bindings
- **THEN** no material list opens, and picking Material list on the wheel opens it

#### Scenario: Left arrow on a locked placement
- **WHEN** the selected placement is LOCKED and the player presses the left arrow
- **THEN** its layer setting and position do not change

#### Scenario: Materials on the wheel
- **WHEN** a player holding the remote presses N with a placement selected and default bindings
- **THEN** no materials screen opens, and picking Materials on the wheel opens it

### Requirement: Placement overlay
While a remote is in the main hand and no screen is open, the client SHALL show an overlay on the configured side of the screen (default left). It SHALL show the selected placement's name, version, a state badge (FOLLOWING, ANCHORED or LOCKED), position, rotation, mirror and the number of swaps when it has any, or "No placement selected" when none is. For a LOCKED placement it SHALL also show:
- the layer setting with its layers and the placement's height
- whether the in-hand filter is on
- its progress in percent with matching and counted blocks, or that it is still scanning until every loaded position has been compared once
- its layer progress, when the layer setting is not off
- the number of positions per state: wrong block, wrong orientation, wrong water and extra
- when the crosshair targets a position of the placement in one of those states, the expected and the found block
- the shortage lines of its material list

#### Scenario: Anchored placement
- **WHEN** a player holds the remote and the selected placement `Castle Gate` version 3 is anchored at 128, 64, -40 with rotation 90 and no mirror
- **THEN** the overlay shows `Castle Gate`, `v3`, an ANCHORED badge, `128, 64, -40`, `90` and mirror off

#### Scenario: Remote put away
- **WHEN** the player switches from the remote to another item
- **THEN** the overlay disappears

#### Scenario: Locked placement with layers
- **WHEN** the selected 12-layer placement is LOCKED with layers 4 to 6 and the in-hand filter on
- **THEN** the overlay shows a LOCKED badge, `Layers 4-6 of 12` and that the in-hand filter is on

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

#### Scenario: Shortages
- **WHEN** the selected locked placement is short 12 oak planks
- **THEN** the overlay shows oak planks with 12 short

#### Scenario: Swaps
- **WHEN** the selected placement swaps oak stairs and oak planks
- **THEN** the overlay says it has 2 swaps
