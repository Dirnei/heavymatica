# Spec Delta

## ADDED Requirements

### Requirement: Capture segment
Outside capture mode, the wheel SHALL show Capture after its other segments when no placement is selected or the selected placement is ANCHORED or LOCKED. It SHALL NOT show Capture while the selected placement is FOLLOWING. Picking Capture SHALL close the wheel and start capture mode.

#### Scenario: Nothing selected
- **WHEN** no placement is selected and the player opens the wheel
- **THEN** it shows Capture as the last segment

#### Scenario: Following placement
- **WHEN** the selected placement is FOLLOWING and the player opens the wheel
- **THEN** it does not show Capture

### Requirement: Capture wheel
In capture mode, the wheel SHALL show only Capture, Swap corner, Reset and Cancel. Capture and Swap corner SHALL be greyed out until both corners are set, and Reset until a corner is set; a greyed-out segment SHALL do nothing and keep the wheel open. Capture SHALL capture the box, Swap corner SHALL make the other corner active, Reset SHALL clear both corners and stay in capture mode, and Cancel SHALL end capture mode.

#### Scenario: Before any corner
- **WHEN** capture mode is on with no corners and the player opens the wheel
- **THEN** it shows Capture, Swap corner and Reset greyed out, and Cancel

#### Scenario: Reset
- **WHEN** both corners are set and the player picks Reset
- **THEN** capture mode is on with no corners

#### Scenario: Cancel
- **WHEN** the player picks Cancel in capture mode
- **THEN** capture mode is off and the remote's keys work on placements again
