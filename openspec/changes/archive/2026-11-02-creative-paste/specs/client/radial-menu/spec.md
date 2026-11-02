## ADDED Requirements

### Requirement: Paste segment
While paste is available, the wheel SHALL show a Paste segment after Materials when the selected placement is ANCHORED
or LOCKED. It SHALL be greyed out while a paste or undo is running; a greyed-out Paste SHALL do nothing and keep the
wheel open. Picking it SHALL swap the wheel's segments to the paste sub-wheel. In HOLD mode, releasing the wheel key on
Paste SHALL leave the sub-wheel open, and a click SHALL pick.

#### Scenario: Locked placement in creative
- **WHEN** the player is in creative single player with a locked placement selected and opens the wheel
- **THEN** it shows Paste after Materials

#### Scenario: Survival
- **WHEN** the player is in survival single player with a locked placement selected and opens the wheel
- **THEN** it shows no Paste

#### Scenario: Following placement
- **WHEN** the player is in creative single player with a following placement selected and opens the wheel
- **THEN** it shows no Paste

### Requirement: Paste sub-wheel
The paste sub-wheel SHALL show Replace all, Only into air and Skip air. Picking one SHALL start a paste of the selected
placement in that mode and close the wheel. Esc or the centre SHALL close the wheel and paste nothing.

#### Scenario: Paste only into air
- **WHEN** the player picks Paste, then Only into air
- **THEN** a paste in that mode starts and the wheel closes

#### Scenario: Close without pasting
- **WHEN** the player picks Paste and then presses Esc
- **THEN** the wheel closes and nothing is pasted

### Requirement: Undo paste segment
While paste is available and a paste can be undone, the wheel SHALL show an Undo paste segment as its last segment, in
every placement state, also with no placement selected. It SHALL be greyed out while a paste or undo is running. Picking
it SHALL undo the last paste and close the wheel.

#### Scenario: After a paste
- **WHEN** the player has pasted and opens the wheel with nothing selected
- **THEN** it shows Undo paste as its last segment

#### Scenario: Nothing to undo
- **WHEN** the player has not pasted since joining the world and opens the wheel
- **THEN** it shows no Undo paste
