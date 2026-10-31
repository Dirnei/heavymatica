# Spec Delta

## MODIFIED Requirements

### Requirement: Removing needs a second press
An anchored placement SHALL only be removed after a confirmation: on the wheel, Remove asks Yes / Cancel; a bound remove key SHALL remove it only when pressed a second time within 3 seconds, and after the first press the overlay SHALL say to press again to remove. A FOLLOWING placement SHALL be removed at once (the wheel's Cancel, or the first press of the remove key), since it was never anchored. A LOCKED placement SHALL NOT be removed by the player; it has to be unlocked first. The only way a LOCKED placement is removed is by finishing it.

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

#### Scenario: Finished locked placement
- **WHEN** a locked placement reaches 100% progress
- **THEN** it is removed without being unlocked
