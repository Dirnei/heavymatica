# Spec Delta

## MODIFIED Requirements

### Requirement: Removing needs a second press
An anchored placement SHALL only be removed after a confirmation: on the wheel, Remove asks Yes / Cancel; a bound remove key SHALL remove it only when pressed a second time within 3 seconds, and after the first press the overlay SHALL say to press again to remove. A FOLLOWING placement SHALL be removed at once (the wheel's Cancel, or the first press of the remove key), since it was never anchored. A LOCKED placement SHALL NOT be removed; it has to be unlocked first.

#### Scenario: Remove anchored placement
- **WHEN** the player has bound the remove key to Delete and presses Delete twice within 3 seconds with an anchored placement selected
- **THEN** the placement is gone and no placement is selected

#### Scenario: Single press
- **WHEN** the player presses the bound remove key once and waits 5 seconds
- **THEN** the placement is still there

#### Scenario: Locked placement
- **WHEN** the player presses the bound remove key twice with a locked placement selected
- **THEN** the placement is still there and still LOCKED

#### Scenario: Remove from the wheel
- **WHEN** the player picks Remove on the wheel with an anchored placement selected and then Yes
- **THEN** the placement is gone and no placement is selected

### Requirement: Locking
While a remote is held and the selected placement is ANCHORED, the wheel's Lock segment, or the lock / unlock key when bound, SHALL put it in LOCKED. A locked placement SHALL stay where it is and SHALL NOT be moved, raised, lowered, rotated, mirrored, released or removed.

#### Scenario: Lock
- **WHEN** the selected placement is ANCHORED and the player picks Lock on the wheel
- **THEN** it is LOCKED

#### Scenario: Locked placement ignores move keys
- **WHEN** the selected placement is LOCKED and the player presses R, M or sneak + right-click
- **THEN** its position, rotation, mirror and state do not change

#### Scenario: Following placement cannot lock
- **WHEN** the selected placement is FOLLOWING and the player presses the bound lock / unlock key
- **THEN** it stays FOLLOWING

### Requirement: Unlocking needs a second press
While a remote is held and the selected placement is LOCKED, it SHALL only be unlocked, back to ANCHORED, after a confirmation: on the wheel, Unlock asks Yes / Cancel; a bound lock / unlock key SHALL unlock it only when pressed a second time within 3 seconds, and after the first press the overlay SHALL say to press again to unlock.

#### Scenario: Unlock
- **WHEN** the player has bound the lock / unlock key to L and presses L twice within 3 seconds with a locked placement selected
- **THEN** the placement is ANCHORED

#### Scenario: Single press
- **WHEN** the player presses the bound lock / unlock key once on a locked placement and waits 5 seconds
- **THEN** the placement is still LOCKED

#### Scenario: Unlock cancelled on the wheel
- **WHEN** the player picks Unlock on the wheel with a locked placement selected and then Cancel
- **THEN** the placement is still LOCKED
