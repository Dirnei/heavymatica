## MODIFIED Requirements

### Requirement: Comparison in the overlay
For a LOCKED selected placement, the overlay SHALL also show:
- its progress in percent with matching and counted blocks, or that it is still scanning until every loaded position has been compared once
- its layer progress, when the layer setting is not off
- the number of positions per state: wrong block, wrong orientation, wrong water and extra
- when the crosshair targets a position of the placement in one of those states, the expected and the found block

The crosshair's target SHALL be the ghost target when there is one, and otherwise the real block the crosshair hits.

A state whose count is 0 SHALL NOT be shown. The progress SHALL also be drawn as a bar of the placement's material list: built in green, carried in blue and short in orange, as the materials screen draws it.

#### Scenario: Progress and mistakes
- **WHEN** the selected locked placement has 3104 of 5006 blocks matching, 12 wrong blocks, 4 wrong orientations, 2 wrong water and 3 extra
- **THEN** the overlay shows `62%`, `3104 / 5006` and the counts 12, 4, 2 and 3 in red, orange, cyan and magenta

#### Scenario: Just locked
- **WHEN** the player has just locked a placement and not every loaded position has been compared yet
- **THEN** the overlay says the placement is scanning instead of showing a percentage

#### Scenario: Looking at a mistake
- **WHEN** the crosshair targets a position of the selected locked placement that expects oak stairs facing north and holds oak stairs facing east
- **THEN** the overlay shows the expected and the found stairs with their facing

#### Scenario: Anchored placement shows no progress
- **WHEN** the selected placement is ANCHORED
- **THEN** the overlay shows no progress and no counts

#### Scenario: Ghost in front of a mistake
- **WHEN** a missing ghost of the selected locked placement stands between the player and a wrong block of the same placement
- **THEN** the overlay shows no expected and found lines, because the target is the missing ghost

#### Scenario: Looking at an extra block
- **WHEN** the crosshair hits a tall grass plant where the selected locked placement has air
- **THEN** the overlay shows air as expected and the tall grass as found

#### Scenario: Zero counts left out
- **WHEN** the selected locked placement has 7 wrong blocks and no wrong orientations, wrong water or extra blocks
- **THEN** the overlay shows 7 wrong in red and no other mistake count

#### Scenario: Progress bar
- **WHEN** the selected locked placement plans 214 blocks in its visible layers, 67 are built, the player carries 96 of those still needed and 51 are short
- **THEN** the overlay's bar shows 67 in green, 96 in blue and 51 in orange
