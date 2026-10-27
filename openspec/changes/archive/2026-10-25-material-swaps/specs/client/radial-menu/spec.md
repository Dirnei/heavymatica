# Spec Delta

## MODIFIED Requirements

### Requirement: Segments follow the state
The wheel SHALL show these segments for the selected placement's state and no others:
- no placement selected: Materials, greyed out, and Library
- FOLLOWING: Cancel, Materials and Library
- ANCHORED: Lock, Remove, Materials and Library
- LOCKED: Unlock, In-hand filter, Layers, Materials and Library

#### Scenario: Locked placement
- **WHEN** the selected placement is LOCKED and the player opens the wheel
- **THEN** it shows Unlock, In-hand filter, Layers, Materials and Library, and no Lock, Remove or Material list

#### Scenario: Nothing selected
- **WHEN** no placement is selected and the player opens the wheel
- **THEN** it shows Materials greyed out and Library

#### Scenario: Anchored placement
- **WHEN** the selected placement is ANCHORED and the player opens the wheel
- **THEN** it shows Lock, Remove, Materials and Library

### Requirement: What the segments do
Library SHALL open the library. Cancel SHALL remove the following placement at once. Lock SHALL lock the anchored placement. In-hand filter SHALL toggle the locked placement's in-hand filter. Materials SHALL open the materials screen of the selected placement; greyed out, it SHALL do nothing and the wheel SHALL stay open. Remove, Unlock and Layers SHALL first ask, as their own requirements say.

#### Scenario: Cancel a following placement
- **WHEN** the selected placement is FOLLOWING and the player picks Cancel
- **THEN** the placement is gone

#### Scenario: Material list
- **WHEN** the selected placement is LOCKED and the player picks Materials
- **THEN** the materials screen opens with the material list's counts

#### Scenario: Greyed out without a selection
- **WHEN** no placement is selected and the player opens the wheel and clicks Materials
- **THEN** no screen opens and the wheel stays open

### Requirement: Wheel actions as keys
Lock / unlock, Remove, In-hand filter, Materials and cycling the layer setting SHALL each be a key in the Heavymatica category with no default key. A bound key SHALL work only while a remote is held and no screen is open, in the same states as its segment. The cycle key SHALL step the layer setting from off to from-to to follow feet and back, and the other way with Shift held.

#### Scenario: No default
- **WHEN** a player opens the controls screen with default bindings
- **THEN** the Heavymatica category lists the remove, lock / unlock, in-hand filter, materials and cycle layer mode keys as not bound

#### Scenario: Bound in-hand key
- **WHEN** a player binds the in-hand filter key to I and presses I with a locked placement selected
- **THEN** its in-hand filter toggles

### Requirement: Shortage pointer to the wheel
When the overlay says that more items are short than it lists and the materials key is not bound, it SHALL name the wheel key and the Materials segment instead of a materials key.

#### Scenario: More short without a key
- **WHEN** the materials key is not bound and the selected locked placement is short of 5 items
- **THEN** the overlay's "more short" line names V and Materials
