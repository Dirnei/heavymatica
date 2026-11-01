# Spec Delta

## MODIFIED Requirements

### Requirement: Segments follow the state
The wheel SHALL show these segments for the selected placement's state and no others:
- no placement selected: Materials, greyed out, and Library
- FOLLOWING: Cancel, Materials and Library
- ANCHORED: Lock, Remove, Materials and Library
- LOCKED: Unlock, In-hand filter, Layers, Materials and Library

While a finished placement can be brought back, the wheel SHALL also show Bring back, first, in every state.

#### Scenario: Locked placement
- **WHEN** the selected placement is LOCKED and the player opens the wheel
- **THEN** it shows Unlock, In-hand filter, Layers, Materials and Library, and no Lock, Remove or Material list

#### Scenario: Nothing selected
- **WHEN** no placement is selected and the player opens the wheel
- **THEN** it shows Materials greyed out and Library

#### Scenario: Anchored placement
- **WHEN** the selected placement is ANCHORED and the player opens the wheel
- **THEN** it shows Lock, Remove, Materials and Library

#### Scenario: Just finished
- **WHEN** a placement finished 3 seconds ago, nothing is selected, and the player opens the wheel
- **THEN** it shows Bring back, Materials greyed out and Library

### Requirement: What the segments do
Library SHALL open the library. Cancel SHALL remove the following placement at once. Lock SHALL lock the anchored placement. In-hand filter SHALL toggle the locked placement's in-hand filter. Materials SHALL open the materials screen of the selected placement; greyed out, it SHALL do nothing and the wheel SHALL stay open. Remove, Unlock and Layers SHALL first ask, as their own requirements say. Bring back SHALL bring the most recently finished placement back at once and close the wheel; when the time to bring it back runs out while the wheel is open, picking it SHALL do nothing.

#### Scenario: Cancel a following placement
- **WHEN** the selected placement is FOLLOWING and the player picks Cancel
- **THEN** the placement is gone

#### Scenario: Material list
- **WHEN** the selected placement is LOCKED and the player picks Materials
- **THEN** the materials screen opens with the material list's counts

#### Scenario: Greyed out without a selection
- **WHEN** no placement is selected and the player opens the wheel and clicks Materials
- **THEN** no screen opens and the wheel stays open

#### Scenario: Bring back from the wheel
- **WHEN** a placement finished 5 seconds ago and the player picks Bring back
- **THEN** the placement is back, LOCKED and selected, and the wheel is closed
