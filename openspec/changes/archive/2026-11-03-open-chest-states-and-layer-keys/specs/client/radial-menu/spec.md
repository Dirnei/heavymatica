## MODIFIED Requirements

### Requirement: Wheel actions as keys
Lock / unlock, Remove, In-hand filter, Materials and cycling the layer setting SHALL each be a key in the Heavymatica category with no default key. A bound key SHALL work only while a remote is held and no screen is open, in the same states as its segment, except the cycle key, which SHALL also work without the remote while the selected placement is LOCKED and no screen is open. The cycle key SHALL step the layer setting from off to from-to to follow feet and back, and the other way with Shift held.

#### Scenario: No default
- **WHEN** a player opens the controls screen with default bindings
- **THEN** the Heavymatica category lists the remove, lock / unlock, in-hand filter, materials and cycle layer mode keys as not bound

#### Scenario: Bound in-hand key
- **WHEN** a player binds the in-hand filter key to I and presses I with a locked placement selected
- **THEN** its in-hand filter toggles

#### Scenario: Bound cycle key without the remote
- **WHEN** a player binds the cycle layer mode key to L, holds oak planks and presses L with a locked placement selected
- **THEN** its layer setting steps to the next one

#### Scenario: Bound lock key without the remote
- **WHEN** a player binds the lock / unlock key to K, holds oak planks and presses K with an anchored placement selected
- **THEN** the placement stays anchored
