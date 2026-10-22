# Spec Delta

## MODIFIED Requirements

### Requirement: Comparison in the overlay
For a LOCKED selected placement, the overlay SHALL also show:
- its progress in percent with matching and counted blocks, or that it is still scanning until every loaded position has been compared once
- its layer progress, when the layer setting is not off
- the number of positions per state: wrong block, wrong orientation, wrong water and extra
- when the crosshair targets a position of the placement in one of those states, the expected and the found block

The crosshair's target SHALL be the ghost target when there is one, and otherwise the real block the crosshair hits.

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

### Requirement: Finishing in the overlay
For a LOCKED selected placement, the overlay SHALL also show, in yellow, how many positions in the visible layers need each finishing step, for every step that any visible position needs. These lines SHALL follow the layer setting, while the counts per mistake state stay over the whole placement. When the crosshair targets a position of the placement that needs finishing, the overlay SHALL show the expected and the found block and the step. The crosshair's target SHALL be the ghost target when there is one, and otherwise the real block the crosshair hits.

#### Scenario: Finishing steps
- **WHEN** the visible layers of the selected locked placement have 3 dirt paths on dirt and 2 stripped oak logs on oak logs
- **THEN** the overlay shows in yellow that 3 blocks need a shovel and 2 blocks need an axe, and no line for a hoe or water

#### Scenario: Nothing to finish
- **WHEN** no position in the visible layers of the selected locked placement needs finishing
- **THEN** the overlay shows no finishing line

#### Scenario: Finishing follows the visible layers
- **WHEN** the selected locked placement shows layers 4 to 6, and layer 2 has 3 dirt paths on dirt and 2 wrong blocks
- **THEN** the overlay shows no shovel line and still counts the 2 wrong blocks in red

#### Scenario: Looking at a block to finish
- **WHEN** the crosshair targets a position of the selected locked placement that expects white concrete and holds white concrete powder
- **THEN** the overlay shows white concrete as expected, white concrete powder as found, and that it needs water
