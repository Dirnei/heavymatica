## MODIFIED Requirements

### Requirement: Unknown blocks segment
When the selected placement has unknown blocks, the wheel SHALL also show an Unknown blocks segment in every placement state, labelled with whether unknown blocks are shown or hidden. Picking it SHALL switch between shown and hidden, write the choice to `ghost.showUnknownBlocks` in the client config file, save the file and close the wheel. Without unknown blocks, the segment SHALL NOT show.

The segment SHALL be in the Options sub-wheel, not on the main ring. While the selected placement is FOLLOWING or ANCHORED and has unknown blocks, the main ring SHALL show an Options segment after Opacity, whose sub-wheel holds only Unknown blocks.

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

#### Scenario: Anchored placement with unknown blocks
- **WHEN** the selected anchored placement has unknown blocks and the player opens the wheel
- **THEN** the main ring shows Options after Opacity and no Unknown blocks segment, and picking Options shows Unknown blocks alone

#### Scenario: Anchored placement without unknown blocks
- **WHEN** the selected anchored placement's blocks are all known
- **THEN** the main ring shows no Options segment

### Requirement: Options sub-wheel
Picking Options SHALL swap the wheel's segments to In-hand filter, Easy Place and Placement restriction, each marked
while it is on: the selected locked placement's in-hand filter, and the Easy Place and placement restriction client
settings. Picking one SHALL toggle it and close the wheel. Esc or the centre SHALL close the wheel and change nothing. In
HOLD mode, releasing the wheel key on Options SHALL leave the sub-wheel open, and a click SHALL pick.

When the selected locked placement has unknown blocks, Unknown blocks SHALL follow Placement restriction. In-hand filter, Easy Place and Placement restriction SHALL show only for a locked placement.

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

#### Scenario: Locked placement with unknown blocks
- **WHEN** the selected locked placement has unknown blocks and the player picks Options
- **THEN** the sub-wheel shows In-hand filter, Easy Place, Placement restriction, Unknown blocks and Mistakes, in that order
