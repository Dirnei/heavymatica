# Spec Delta

## MODIFIED Requirements

### Requirement: Segments follow the state
The wheel SHALL show these segments for the selected placement's state and no others:
- no placement selected: Materials, greyed out, Opacity and Library
- FOLLOWING: Cancel, Materials, Opacity and Library
- ANCHORED: Lock, Remove, Materials, Opacity and Library
- LOCKED: Unlock, In-hand filter, Layers, Materials, Opacity and Library

#### Scenario: Locked placement
- **WHEN** the selected placement is LOCKED and the player opens the wheel
- **THEN** it shows Unlock, In-hand filter, Layers, Materials, Opacity and Library, and no Lock, Remove or Material list

#### Scenario: Nothing selected
- **WHEN** no placement is selected and the player opens the wheel
- **THEN** it shows Materials greyed out, Opacity and Library

#### Scenario: Anchored placement
- **WHEN** the selected placement is ANCHORED and the player opens the wheel
- **THEN** it shows Lock, Remove, Materials, Opacity and Library
