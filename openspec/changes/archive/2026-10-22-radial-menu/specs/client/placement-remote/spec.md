# Spec Delta

## MODIFIED Requirements

### Requirement: Default key layout
The placement keys SHALL default to: arrow keys to move horizontally, numpad + and numpad - to move up and down, R to rotate, M to mirror, and V to open the wheel. Remove, lock and unlock, the in-hand filter, the material list and cycling the layer setting SHALL have no default key; they are on the wheel. While the selected placement is LOCKED, the up and down arrow keys and numpad + / - SHALL control its layers instead of moving it, and the left and right arrow keys SHALL do nothing. Every placement key SHALL be rebindable in the controls screen under the Heavymatica category.

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
