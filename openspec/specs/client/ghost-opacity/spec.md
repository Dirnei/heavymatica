# client/ghost-opacity Specification

## Purpose
Lets the player set how see-through placement ghosts are, as one global client setting that is switched in steps
from the remote's wheel and kept across restarts.

## Requirements

### Requirement: Global ghost opacity
The client setting `ghost.opacity` SHALL set the opacity of every placement ghost, from 0.05 to 1, with a default of 0.4. It SHALL NOT depend on the placement. A change SHALL show on every ghost at once, and the in-hand filter's dimmed hotbar blocks SHALL follow it as their configured share of it.

#### Scenario: New install
- **WHEN** a player starts the game without a Heavymatica client config
- **THEN** ghosts are drawn at 40% opacity

#### Scenario: Existing config
- **WHEN** the client config holds `ghost.opacity = 0.45` from an older version
- **THEN** ghosts are drawn at 45% opacity

#### Scenario: Live change
- **WHEN** two placements are shown and the opacity changes from 0.4 to 0.8
- **THEN** both ghosts are drawn at 80% opacity in the next frame, without a restart

### Requirement: Opacity steps setting
The client setting `ghost.opacitySteps` SHALL list the opacities the wheel offers, by default 0.2, 0.4, 0.6, 0.8 and 1. Values outside 0.05 to 1 SHALL be dropped, duplicates removed and the rest sorted from low to high. When fewer than 2 or more than 8 steps remain, the default steps SHALL be used instead.

#### Scenario: Unsorted with an invalid value
- **WHEN** `ghost.opacitySteps` is `[0.9, 0.3, 1.5, 0.3]`
- **THEN** the wheel offers 30% and 90%

#### Scenario: Too few steps
- **WHEN** `ghost.opacitySteps` is `[0.5, 2.0]`
- **THEN** the wheel offers 20%, 40%, 60%, 80% and 100%

#### Scenario: Too many steps
- **WHEN** `ghost.opacitySteps` lists 9 different values between 0.1 and 0.9
- **THEN** the wheel offers 20%, 40%, 60%, 80% and 100%

### Requirement: Opacity sub-wheel
Picking Opacity SHALL swap the wheel's segments to one per opacity step, from low to high, each labelled as a whole percentage such as `40%`. Picking a step SHALL set the ghost opacity to it and close the wheel. Esc or the centre SHALL close the wheel and change nothing. In HOLD mode, releasing the wheel key on Opacity SHALL leave the sub-wheel open, and a click SHALL pick.

#### Scenario: Pick a step
- **WHEN** the opacity is 0.4 and the player picks Opacity, then 80%
- **THEN** the wheel closes and the ghosts are drawn at 80% opacity

#### Scenario: Release on Opacity
- **WHEN** in HOLD mode the player holds V, points at Opacity and releases V
- **THEN** the wheel stays open showing 20%, 40%, 60%, 80% and 100%

#### Scenario: Leave from the centre
- **WHEN** the player picks Opacity and then clicks the centre
- **THEN** the wheel closes and the opacity is unchanged

### Requirement: Current step marked
The opacity sub-wheel SHALL mark the step equal to the current opacity, as the layers sub-wheel marks the current layer setting. A step SHALL count as equal when it differs from the current opacity by less than 0.001. When no step is equal, no step SHALL be marked.

#### Scenario: Current step
- **WHEN** the opacity is 0.4 and the player picks Opacity
- **THEN** 40% is marked and no other step is

#### Scenario: No matching step
- **WHEN** the opacity is 0.45 and the steps are the default ones
- **THEN** no step is marked

### Requirement: Picked opacity is saved
Picking a step SHALL write it to `ghost.opacity` in the client config file and save the file, so the opacity stays after the game restarts.

#### Scenario: Restart
- **WHEN** the player picks 60% on the wheel and restarts the game
- **THEN** the client config holds `ghost.opacity = 0.6` and ghosts are drawn at 60% opacity
