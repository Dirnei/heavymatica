# Spec Delta

## ADDED Requirements

### Requirement: Unknown blocks segment
When the selected placement has unknown blocks, the wheel SHALL also show an Unknown blocks segment in every placement state, labelled with whether unknown blocks are shown or hidden. Picking it SHALL switch between shown and hidden, write the choice to `ghost.showUnknownBlocks` in the client config file, save the file and close the wheel. Without unknown blocks, the segment SHALL NOT show.

#### Scenario: Hide from the wheel
- **WHEN** the selected anchored placement has unknown blocks that are shown and the player picks Unknown blocks
- **THEN** the wheel closes, the placeholders disappear from every placement, and the client config holds `ghost.showUnknownBlocks = false`

#### Scenario: Show again
- **WHEN** unknown blocks are hidden and the player picks Unknown blocks on a placement that has some
- **THEN** the placeholders show again on every placement

#### Scenario: Kept after a restart
- **WHEN** the player hides unknown blocks and restarts the game
- **THEN** unknown blocks are still hidden

#### Scenario: No unknown blocks
- **WHEN** the selected placement's blocks are all known
- **THEN** the wheel shows no Unknown blocks segment

#### Scenario: Nothing selected
- **WHEN** no placement is selected
- **THEN** the wheel shows no Unknown blocks segment
