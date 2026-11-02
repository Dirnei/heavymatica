## MODIFIED Requirements

### Requirement: Mistake states
A position with a block in both the schematic and the world SHALL be wrong block when the blocks differ and the equivalents table neither accepts the world block in place of the planned block nor lists it as needing a finishing step, and the world block is neither the empty base of a planned two-item block nor a two-item block on the planned empty base. It SHALL be wrong orientation when only the orientation differs, too many when the world block has a larger amount than planned, and wrong water when only the waterlogging differs.

#### Scenario: Wrong block
- **WHEN** a locked placement expects oak planks at a position where the world has dirt
- **THEN** that position is wrong block

#### Scenario: Too many candles
- **WHEN** a locked placement expects white candles with 3 candles and the world has white candles with 4 candles
- **THEN** that position is too many

#### Scenario: Other plant in the pot
- **WHEN** a locked placement expects a potted dead bush and the world has a potted poppy there
- **THEN** that position is wrong block

### Requirement: Showing the states
For a locked placement:
- Matching and ignored positions SHALL NOT show a ghost.
- Missing, not enough yet and unknown positions SHALL show the normal ghost.
- Boxes around the block SHALL show: red for wrong block, orange for wrong orientation, too many and not enough yet, cyan for wrong water, yellow for needs finishing, magenta for extra. Only not enough yet SHALL keep its ghost.
- Boxes SHALL show through real blocks in front of them, weaker than where nothing is in front, so a box behind a wall is still found and a box in plain sight stands out.

#### Scenario: Extra block in the footprint
- **WHEN** a tall grass plant stands where a locked placement has air
- **THEN** a magenta box shows around the grass

#### Scenario: Needs water
- **WHEN** the schematic has a waterlogged oak slab and the world has a dry oak slab there
- **THEN** a cyan box shows around the slab

#### Scenario: Box behind a wall
- **WHEN** a red box is on the far side of a solid wall from the player
- **THEN** the box shows through the wall, weaker than the same box seen without the wall

#### Scenario: Yellow box
- **WHEN** a locked placement expects a dirt path and the world has dirt there
- **THEN** a yellow box shows around the dirt and no ghost shows there

#### Scenario: Outside the visible layers
- **WHEN** a locked placement shows layers 4 to 6 and layer 2 has a position that needs finishing
- **THEN** no yellow box shows on layer 2

#### Scenario: One of three candles
- **WHEN** a locked placement expects white candles with 3 candles and the world has 1 white candle there
- **THEN** the ghost of the 3 candles shows there with an orange box around the candles

#### Scenario: Single slab where a double slab is planned
- **WHEN** a locked placement expects a double stone slab and the world has a bottom stone slab there
- **THEN** the ghost of the double slab shows there with an orange box around the slab

#### Scenario: Four of three candles
- **WHEN** a locked placement expects white candles with 3 candles and the world has 4 there
- **THEN** an orange box shows around the candles and no ghost shows there

#### Scenario: Empty pot
- **WHEN** a locked placement expects a potted dead bush and the world has an empty flower pot there
- **THEN** the ghost of the potted dead bush shows there with an orange box around the pot

## ADDED Requirements

### Requirement: Two-item blocks in the comparison
A position whose planned block is a two-item block SHALL be not enough yet when the world holds the empty base block there, in the planned orientation. A position whose planned block is the empty base of a two-item block SHALL be too many when the world holds a two-item block on that base. A two-item block with a different content than planned SHALL be wrong block. The water level of a cauldron SHALL NOT be compared. Not enough yet SHALL NOT count as done for progress.

#### Scenario: Empty pot under a planned potted plant
- **WHEN** a locked placement expects a potted dead bush and the world has an empty flower pot there
- **THEN** that position is not enough yet

#### Scenario: Cake without its candle
- **WHEN** a locked placement expects a red candle cake and the world has an uneaten cake there
- **THEN** that position is not enough yet

#### Scenario: Eaten cake
- **WHEN** a locked placement expects a red candle cake and the world has a cake with a slice eaten there
- **THEN** that position is wrong block

#### Scenario: Empty cauldron
- **WHEN** a locked placement expects a water cauldron and the world has an empty cauldron there
- **THEN** that position is not enough yet

#### Scenario: Water level
- **WHEN** a locked placement expects a full water cauldron and the world has a water cauldron at level 1 there
- **THEN** that position is matching

#### Scenario: Plant in a planned empty pot
- **WHEN** a locked placement expects an empty flower pot and the world has a potted dead bush there
- **THEN** that position is too many

#### Scenario: Pot only
- **WHEN** a placement has 10 counted blocks, 9 match and the tenth is a potted dead bush with only its empty pot placed
- **THEN** its progress is 90%
