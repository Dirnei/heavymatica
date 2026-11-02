## MODIFIED Requirements

### Requirement: Comparison in the overlay
For a LOCKED selected placement, the overlay SHALL also show:
- its progress in percent with matching and counted blocks, or that it is still scanning until every loaded position has been compared once
- its layer progress, when the layer setting is not off
- the number of positions per state: wrong block, wrong orientation, wrong water and extra

A state whose count is 0 SHALL NOT be shown. The progress SHALL also be drawn as a bar of the placement's material list: built in green, carried in blue and short in orange, as the materials screen draws it.

The overlay SHALL NOT name the block under the crosshair, its expected or found block, or what it needs; the targeting HUD shows that.

#### Scenario: Progress and mistakes
- **WHEN** the selected locked placement has 3104 of 5006 blocks matching, 12 wrong blocks, 4 wrong orientations, 2 wrong water and 3 extra
- **THEN** the overlay shows `62%`, `3104 / 5006` and the counts 12, 4, 2 and 3 in red, orange, cyan and magenta

#### Scenario: Just locked
- **WHEN** the player has just locked a placement and not every loaded position has been compared yet
- **THEN** the overlay says the placement is scanning instead of showing a percentage

#### Scenario: Anchored placement shows no progress
- **WHEN** the selected placement is ANCHORED
- **THEN** the overlay shows no progress and no counts

#### Scenario: Looking at a mistake
- **WHEN** the crosshair targets a position of the selected locked placement that expects oak stairs facing north and holds oak stairs facing east
- **THEN** the overlay's lines stay the same as when looking elsewhere, and the targeting HUD shows the found stairs' facing

#### Scenario: Ghost in front of a mistake
- **WHEN** a missing ghost of the selected locked placement stands between the player and a wrong block of the same placement
- **THEN** the overlay shows no expected and found lines

#### Scenario: Looking at an extra block
- **WHEN** the crosshair hits a tall grass plant where the selected locked placement has air
- **THEN** the overlay shows no expected and found lines, and the targeting HUD names the tall grass as extra

#### Scenario: Zero counts left out
- **WHEN** the selected locked placement has 7 wrong blocks and no wrong orientations, wrong water or extra blocks
- **THEN** the overlay shows 7 wrong in red and no other mistake count

#### Scenario: Progress bar
- **WHEN** the selected locked placement plans 214 blocks in its visible layers, 67 are built, the player carries 96 of those still needed and 51 are short
- **THEN** the overlay's bar shows 67 in green, 96 in blue and 51 in orange

### Requirement: Finishing in the overlay
For a LOCKED selected placement, the overlay SHALL also show, in yellow, how many positions in the visible layers need each finishing step, for every step that any visible position needs. These lines SHALL follow the layer setting, while the counts per mistake state stay over the whole placement.

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
- **THEN** the overlay shows only its finishing counts, and the targeting HUD says the block needs water

### Requirement: Amounts in the overlay
For a LOCKED selected placement, the overlay SHALL count too many positions in orange next to the other mistake counts.

#### Scenario: Too many count
- **WHEN** the selected locked placement has 2 positions with too many candles
- **THEN** the overlay shows 2 too many in orange

#### Scenario: Looking at too many candles
- **WHEN** the crosshair targets a position of the selected locked placement that expects 3 white candles and holds 4
- **THEN** the overlay shows no line about that position, and the targeting HUD says there are 4 instead of 3

#### Scenario: Looking at one of three candles
- **WHEN** the crosshair targets a position of the selected locked placement that expects 3 white candles and holds 1
- **THEN** the overlay shows no line about that position, and the targeting HUD says 2 more are needed
