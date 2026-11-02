## MODIFIED Requirements

### Requirement: Segments follow the state
The wheel SHALL show these segments for the selected placement's state and no others:
- no placement selected: Materials, greyed out, and Library
- FOLLOWING: Cancel, Materials and Library
- ANCHORED: Lock, Remove, Materials and Library
- LOCKED: Unlock, Options, Layers, Materials and Library

#### Scenario: Locked placement
- **WHEN** the selected placement is LOCKED and the player opens the wheel
- **THEN** it shows Unlock, Options, Layers, Materials and Library, and no Lock, Remove, In-hand filter or Material list

#### Scenario: Nothing selected
- **WHEN** no placement is selected and the player opens the wheel
- **THEN** it shows Materials greyed out and Library

#### Scenario: Anchored placement
- **WHEN** the selected placement is ANCHORED and the player opens the wheel
- **THEN** it shows Lock, Remove, Materials and Library

### Requirement: What the segments do
Library SHALL open the library. Cancel SHALL remove the following placement at once. Lock SHALL lock the anchored placement. Options SHALL open the Options sub-wheel. Materials SHALL open the materials screen of the selected placement; greyed out, it SHALL do nothing and the wheel SHALL stay open. Remove, Unlock and Layers SHALL first ask, as their own requirements say.

#### Scenario: Cancel a following placement
- **WHEN** the selected placement is FOLLOWING and the player picks Cancel
- **THEN** the placement is gone

#### Scenario: Material list
- **WHEN** the selected placement is LOCKED and the player picks Materials
- **THEN** the materials screen opens with the material list's counts

#### Scenario: Greyed out without a selection
- **WHEN** no placement is selected and the player opens the wheel and clicks Materials
- **THEN** no screen opens and the wheel stays open

## ADDED Requirements

### Requirement: Options sub-wheel
Picking Options SHALL swap the wheel's segments to In-hand filter, Easy Place and Placement restriction, each marked
while it is on: the selected locked placement's in-hand filter, and the Easy Place and placement restriction client
settings. Picking one SHALL toggle it and close the wheel. Esc or the centre SHALL close the wheel and change nothing. In
HOLD mode, releasing the wheel key on Options SHALL leave the sub-wheel open, and a click SHALL pick.

#### Scenario: Turn Easy Place on
- **WHEN** Easy Place is off and the player picks Options, then Easy Place
- **THEN** Easy Place is on, the wheel is closed and the action bar says Easy Place is on

#### Scenario: Toggle the in-hand filter
- **WHEN** the selected locked placement's in-hand filter is off and the player picks Options, then In-hand filter
- **THEN** its in-hand filter is on

#### Scenario: Marks show the current settings
- **WHEN** placement restriction is on, Easy Place is off, the in-hand filter is off, and the player picks Options
- **THEN** only Placement restriction is marked

#### Scenario: Close without a change
- **WHEN** the player picks Options and then presses Esc
- **THEN** the wheel closes and no setting changes
