## ADDED Requirements

### Requirement: Easy Place leaves the open state to the player
Easy Place SHALL judge the block a click would place without its open state: a door, trapdoor or fence gate that the click places closed where the plan has it open, or open where the plan has it closed, SHALL be accepted as long as it matches in all else. The comparison then shows the position as wrong orientation until the player opens or closes it.

#### Scenario: Planned open gate
- **WHEN** a ghost plans an open oak fence gate facing east and the player Easy Place clicks it with an oak fence gate
- **THEN** a closed oak fence gate facing east is placed, and the position shows as wrong orientation until the player opens it

#### Scenario: Planned open trapdoor
- **WHEN** a ghost plans an open top spruce trapdoor facing north and the player Easy Place clicks it with a spruce trapdoor
- **THEN** a closed top spruce trapdoor facing north is placed

### Requirement: Easy Place connects chests as planned
An Easy Place click on a chest ghost SHALL place a chest that the comparison accepts: a single chest where a single chest is planned, and a single chest or the planned half where a half of a double chest is planned. When no click without sneaking gives an accepted chest, for example because the game would connect a planned single chest to a neighbouring chest with the same facing, Easy Place SHALL send the click while sneaking, telling the server that the player sneaks only for that click, as a vanilla client can. A single chest where a half is planned SHALL NOT be an Easy Place target; a right-click on it SHALL behave as without Easy Place.

#### Scenario: Single chest beside a chest
- **WHEN** a ghost plans a single chest facing north next to a single chest facing north that the plan keeps single, and the player Easy Place clicks it with a chest
- **THEN** a single chest facing north is placed and the neighbouring chest stays single

#### Scenario: Completing a double chest
- **WHEN** a ghost plans the right half of a double chest facing north, the world has a single chest facing north at the planned left half, and the player Easy Place clicks it with a chest
- **THEN** the two chests connect into the planned double chest

#### Scenario: First half of a double chest
- **WHEN** a ghost plans the left half of a double chest facing north with air at the other half, and the player Easy Place clicks it with a chest
- **THEN** a single chest facing north is placed

#### Scenario: Vanilla server
- **WHEN** the player Easy Place clicks a planned single chest beside a same-facing chest on a dedicated server without Heavymatica
- **THEN** a single chest is placed, as in single player
