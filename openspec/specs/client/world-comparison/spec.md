# client/world-comparison Specification

## Purpose

Lets a locked placement compare itself with the world: it hides what is already built, flags every mistake by colour and shows how far the build is.

## Requirements

### Requirement: Comparison runs while locked
The client SHALL compare a placement with the world only while the placement is LOCKED, in the player's current dimension. FOLLOWING and ANCHORED placements SHALL show their full ghost with no colour marks.

#### Scenario: Locking starts the comparison
- **WHEN** a player locks a placement whose bottom layer is already built
- **THEN** the bottom layer's ghost disappears and the layers above still show

#### Scenario: Unlocking stops it
- **WHEN** the player unlocks that placement
- **THEN** the whole ghost shows again, including the bottom layer, and no colour marks show

### Requirement: Positions that are not compared
A position of a locked placement SHALL be ignored when its schematic block is `structure_void` or a block the game does not know, and unknown while it is in a chunk that is not loaded or has not been compared yet.

#### Scenario: Unloaded chunk
- **WHEN** part of a locked placement lies in a chunk that is not loaded
- **THEN** those positions are unknown

#### Scenario: Structure void
- **WHEN** the schematic has `structure_void` at a position where the world has grass
- **THEN** that position is ignored

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

### Requirement: Comparison combines with layers
Ghosts and boxes SHALL show only in the layers the placement's layer setting makes visible. The in-hand filter SHALL apply to ghosts only; boxes SHALL show whatever the player holds.

#### Scenario: Follow feet keeps mistakes below
- **WHEN** a locked placement is in follow feet, the player stands on layer 6, and layer 2 has a wrong block
- **THEN** the red box on layer 2 shows and the matching blocks of layers 0 to 6 do not

#### Scenario: From-to window
- **WHEN** a locked placement shows layers 4 to 6 and layer 2 has a wrong block
- **THEN** no box shows on layer 2

#### Scenario: In-hand filter does not hide mistakes
- **WHEN** the in-hand filter is on, the player holds oak planks, and a stone position in a visible layer has dirt
- **THEN** the red box shows at that position

### Requirement: Own block changes show at once
When the player places or breaks a block inside a locked placement, the positions around it SHALL be compared again within one second.

#### Scenario: Placing the right block
- **WHEN** the player places oak planks where a locked placement's ghost shows oak planks
- **THEN** the ghost at that position disappears within one second

#### Scenario: Breaking a placed block
- **WHEN** the player breaks a matching block of a locked placement
- **THEN** the ghost shows there again within one second

### Requirement: Other world changes are noticed
The client SHALL keep comparing every loaded position of a locked placement again in the background, closest to the player first, with a configurable number of positions per tick. A change the player did not make SHALL show within 5 seconds for positions within 32 blocks of the player when the default budget is used. Positions in chunks that load later SHALL be compared once loaded.

#### Scenario: Another player builds
- **WHEN** another player places the right block in a locked placement 10 blocks from the player
- **THEN** the ghost at that position disappears within 5 seconds

#### Scenario: Chunk loads
- **WHEN** part of a locked placement is in a chunk that loads after the placement was locked
- **THEN** that part is compared after the chunk loads and its ghosts and boxes update

### Requirement: Progress
A locked placement's progress SHALL be the number of matching positions whose schematic block is not air, divided by the number of positions that are neither air nor ignored in the schematic. Extra blocks SHALL NOT lower the progress. Unknown positions SHALL count as not done. Layer progress SHALL count the same way over the visible layers only.

#### Scenario: Half built
- **WHEN** a placement has 5006 non-air, non-ignored blocks and 3104 of them match
- **THEN** its progress is 62% (3104 / 5006)

#### Scenario: Extra blocks do not count
- **WHEN** the same placement also has 40 extra blocks
- **THEN** its progress is still 62%

#### Scenario: Visible layers
- **WHEN** the placement is in follow feet showing layers 0 to 4 and those layers have 500 counted blocks of which 440 match
- **THEN** its layer progress is 88%

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
