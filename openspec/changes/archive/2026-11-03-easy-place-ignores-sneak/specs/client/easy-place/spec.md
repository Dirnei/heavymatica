## MODIFIED Requirements

### Requirement: When Easy Place acts
A main-hand right-click SHALL be an Easy Place click when Easy Place is on, no screen is open, the player is not a
spectator, and there is a ghost target within block-interaction reach that is missing or not enough yet, while the main
hand holds one of the target's candidate items as ghost picking takes them, whether or not the player sneaks. The
remote is never a candidate. Every other right-click SHALL behave as without Easy Place, subject to placement
restriction. When the click Easy Place sends needs a different sneak state than the player's, Easy Place SHALL tell the
server that sneak state for that click only and the player's real one right after.

#### Scenario: Matching item on a ghost
- **WHEN** Easy Place is on, the player holds oak stairs and right-clicks a ghost of oak stairs within reach where the world has air
- **THEN** it is an Easy Place click

#### Scenario: Different item
- **WHEN** Easy Place is on, the player holds cobblestone and right-clicks a ghost of oak stairs
- **THEN** it is not an Easy Place click and the right-click behaves as in vanilla, subject to placement restriction

#### Scenario: Sneaking
- **WHEN** Easy Place is on and the player sneaks while right-clicking a ghost with the matching item
- **THEN** it is an Easy Place click and the planned block is placed

#### Scenario: Sneaking onto a double chest
- **WHEN** Easy Place is on, the world has the left half of a planned double chest as a single chest, and the player sneaks while right-clicking the ghost of the right half with a chest
- **THEN** the two chests connect into the planned double chest

#### Scenario: Out of reach
- **WHEN** Easy Place is on and the targeted ghost is beyond block-interaction reach
- **THEN** the right-click behaves as in vanilla

#### Scenario: Matching block already there
- **WHEN** Easy Place is on and the line of sight hits a real block that matches its planned block
- **THEN** it is not an Easy Place click, since a matching position is never a ghost target
