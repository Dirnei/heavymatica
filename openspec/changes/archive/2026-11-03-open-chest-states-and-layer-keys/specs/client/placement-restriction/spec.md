## ADDED Requirements

### Requirement: The open state is not judged
Placement restriction SHALL judge the block a right-click would place without its open state, so a door, trapdoor or fence gate is never refused only because it would be placed open or closed against the plan. A chest's type SHALL be judged: a chest the game would connect where the plan keeps it single SHALL be refused, and a single chest where a half of a double chest is planned SHALL be allowed.

#### Scenario: Closed gate where an open one is planned
- **WHEN** placement restriction is on and the player places an oak fence gate with the planned facing where the plan has it open
- **THEN** the gate is placed

#### Scenario: Chest that would connect
- **WHEN** placement restriction is on, the plan has a single chest facing north next to a chest facing north, and the player places a chest there without sneaking so the game would connect them
- **THEN** nothing is placed

#### Scenario: Chest placed while sneaking
- **WHEN** the same player sneaks while placing that chest facing north
- **THEN** the single chest is placed

#### Scenario: First half of a double chest
- **WHEN** placement restriction is on and the player places a single chest facing north where the plan has the left half of a double chest facing north
- **THEN** the chest is placed
