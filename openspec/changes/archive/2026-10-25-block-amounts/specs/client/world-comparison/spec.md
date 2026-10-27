# Spec Delta

## MODIFIED Requirements

### Requirement: Cell states
Every other position SHALL be in exactly one state, decided by the schematic block as placed (rotated and mirrored) and the world block there: matching (same block, or one the equivalents table accepts in its place, with the same orientation, amount and waterlogging, or air in both), missing (a block in the schematic, air in the world), extra (air in the schematic, a block in the world), not enough yet, needs finishing, or one of the mistakes below.

#### Scenario: Missing
- **WHEN** a locked placement expects oak planks at a position where the world has air
- **THEN** that position is missing

#### Scenario: Air already empty
- **WHEN** the schematic has air at a position where the world has air
- **THEN** that position is matching

### Requirement: Mistake states
A position with a block in both the schematic and the world SHALL be wrong block when the blocks differ and the equivalents table neither accepts the world block in place of the planned block nor lists it as needing a finishing step, wrong orientation when only the orientation differs, too many when the world block has a larger amount than planned, and wrong water when only the waterlogging differs.

#### Scenario: Wrong block
- **WHEN** a locked placement expects oak planks at a position where the world has dirt
- **THEN** that position is wrong block

#### Scenario: Too many candles
- **WHEN** a locked placement expects white candles with 3 candles and the world has white candles with 4 candles
- **THEN** that position is too many

### Requirement: Orientation properties
A block's orientation SHALL be its values for these block-state properties: facing (all variants), axis, rotation, half, door hinge and attach face, and for a single slab whether it is the top or the bottom slab. Amount and waterlogging SHALL be compared on their own. Differences in any other property, such as connections, power, lit, age or stair shape, SHALL NOT make a position wrong.

#### Scenario: Stairs facing the wrong way
- **WHEN** the schematic has oak stairs facing north and the world has oak stairs facing east
- **THEN** that position is wrong orientation

#### Scenario: Fence connections differ
- **WHEN** the schematic's oak fence connects east and west and the world's oak fence at that position connects only east
- **THEN** that position is matching

#### Scenario: Upside-down slab
- **WHEN** the schematic has a bottom stone slab and the world has a top stone slab
- **THEN** that position is wrong orientation

### Requirement: The first fix decides the state
When a world block differs in several ways, its state SHALL be the first that applies of wrong block, wrong orientation, too many, not enough yet, wrong water, needs finishing.

#### Scenario: Wrong facing and not waterlogged
- **WHEN** the schematic has waterlogged oak stairs facing north and the world has dry oak stairs facing south
- **THEN** that position is wrong orientation

#### Scenario: Log on the wrong axis
- **WHEN** a locked placement expects a stripped oak log on the y axis and the world has an oak log on the x axis
- **THEN** that position is wrong orientation

#### Scenario: Waterlogged half of a double slab
- **WHEN** the schematic has a double oak slab and the world has a waterlogged bottom oak slab there
- **THEN** that position is not enough yet

#### Scenario: Too many and dry
- **WHEN** the schematic has 2 waterlogged sea pickles and the world has 3 dry sea pickles there
- **THEN** that position is too many

### Requirement: Showing the states
For a locked placement:
- Matching and ignored positions SHALL NOT show a ghost.
- Missing, not enough yet and unknown positions SHALL show the normal ghost.
- Positions in the other states SHALL show a box around the block in their colour: red for wrong block, orange for wrong orientation and too many, cyan for wrong water, yellow for needs finishing, magenta for extra. No ghost SHALL show at those positions.
- Boxes SHALL be hidden by real blocks in front of them, like ghosts.

#### Scenario: Extra block in the footprint
- **WHEN** a tall grass plant stands where a locked placement has air
- **THEN** a magenta box shows around the grass

#### Scenario: Needs water
- **WHEN** the schematic has a waterlogged oak slab and the world has a dry oak slab there
- **THEN** a cyan box shows around the slab

#### Scenario: Box behind a wall
- **WHEN** a red box is on the far side of a solid wall from the player
- **THEN** the box does not show through the wall

#### Scenario: Yellow box
- **WHEN** a locked placement expects a dirt path and the world has dirt there
- **THEN** a yellow box shows around the dirt and no ghost shows there

#### Scenario: Outside the visible layers
- **WHEN** a locked placement shows layers 4 to 6 and layer 2 has a position that needs finishing
- **THEN** no yellow box shows on layer 2

#### Scenario: One of three candles
- **WHEN** a locked placement expects white candles with 3 candles and the world has 1 white candle there
- **THEN** the ghost of the 3 candles shows there and no box

#### Scenario: Four of three candles
- **WHEN** a locked placement expects white candles with 3 candles and the world has 4 there
- **THEN** an orange box shows around the candles and no ghost shows there

## ADDED Requirements

### Requirement: Block amounts
A block's amount SHALL be its number of candles, sea pickles or snow layers, 1 for a single slab, 2 for a double slab, and 1 for any other block. A position whose block matches in all else SHALL be not enough yet when the world block's amount is smaller than planned. A single slab, top or bottom, where a double slab is planned SHALL be not enough yet. Not enough yet SHALL NOT count as done for progress.

#### Scenario: One of three candles
- **WHEN** a locked placement expects white candles with 3 candles and the world has 1 white candle there
- **THEN** that position is not enough yet

#### Scenario: Snow layers
- **WHEN** a locked placement expects snow with 5 layers and the world has snow with 2 layers there
- **THEN** that position is not enough yet

#### Scenario: Top slab under a planned double slab
- **WHEN** a locked placement expects a double stone slab and the world has a top stone slab there
- **THEN** that position is not enough yet

#### Scenario: Double slab where a single is planned
- **WHEN** a locked placement expects a bottom stone slab and the world has a double stone slab there
- **THEN** that position is too many

#### Scenario: Not done yet
- **WHEN** a placement has 10 counted blocks, 6 match and 1 is a double slab with only its bottom slab placed
- **THEN** its progress is 60%
