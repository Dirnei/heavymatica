# Spec Delta

## MODIFIED Requirements

### Requirement: Default key layout
The placement keys SHALL default to: arrow keys to move horizontally, numpad + and numpad - to move up and down, R to rotate, M to mirror, and V to open the wheel. Remove, lock and unlock, the in-hand filter, cycling the layer setting and the materials screen (which also holds the material list) SHALL have no default key; they are on the wheel. Every placement key SHALL be rebindable in the controls screen under the Heavymatica category.

#### Scenario: Rebound key
- **WHEN** a player rebinds "move up" to Page Up
- **THEN** Page Up moves the selected placement up and numpad + no longer does

#### Scenario: Material list on the wheel
- **WHEN** a player holding the remote presses K with a locked placement selected and default bindings
- **THEN** no screen opens, and picking Materials on the wheel opens the materials screen with the material list's counts

## ADDED Requirements

### Requirement: Swap count in the overlay
While the overlay shows a placement that has swaps, it SHALL also show how many swaps it has, in every state.

#### Scenario: Swaps
- **WHEN** the selected placement swaps oak stairs and oak planks
- **THEN** the overlay says it has 2 swaps

#### Scenario: No swaps
- **WHEN** the selected placement has no swaps
- **THEN** the overlay shows no swap count
