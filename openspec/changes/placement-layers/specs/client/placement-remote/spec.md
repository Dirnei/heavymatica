# Spec Delta

## MODIFIED Requirements

### Requirement: Default key layout
The placement keys SHALL default to: arrow keys to move horizontally, numpad + and numpad - to move up and down, R to rotate, M to mirror, Delete to remove, L to lock and unlock, and I to toggle the in-hand filter. While the selected placement is LOCKED, the arrow keys and numpad + / - SHALL control its layers instead of moving it. Every placement key SHALL be rebindable in the controls screen under the Heavymatica category.

#### Scenario: Rebound key
- **WHEN** a player rebinds "move up" to Page Up
- **THEN** Page Up moves the selected placement up and numpad + no longer does

#### Scenario: Arrows on a locked placement
- **WHEN** the selected placement is LOCKED in from-to and the player presses the up arrow
- **THEN** the layer window moves up and the placement stays where it is

### Requirement: Placement overlay
While a remote is in the main hand and no screen is open, the client SHALL show an overlay on the configured side of the screen (default left). It SHALL show the selected placement's name, version, a state badge (FOLLOWING, ANCHORED or LOCKED), position, rotation and mirror, or "No placement selected" when none is. For a LOCKED placement it SHALL also show the layer setting with its layers and the placement's height (for example `Layers 4-6 of 12` or `Follow feet +1: 0-4 of 12`), and whether the in-hand filter is on.

#### Scenario: Anchored placement
- **WHEN** a player holds the remote and the selected placement `Castle Gate` version 3 is anchored at 128, 64, -40 with rotation 90 and no mirror
- **THEN** the overlay shows `Castle Gate`, `v3`, an ANCHORED badge, `128, 64, -40`, `90` and mirror off

#### Scenario: Remote put away
- **WHEN** the player switches from the remote to another item
- **THEN** the overlay disappears

#### Scenario: Locked placement with layers
- **WHEN** the selected 12-layer placement is LOCKED with layers 4 to 6 and the in-hand filter on
- **THEN** the overlay shows a LOCKED badge, `Layers 4-6 of 12` and that the in-hand filter is on
