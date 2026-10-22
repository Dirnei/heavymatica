# Spec Delta

## ADDED Requirements

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
Every other position SHALL be in exactly one state, decided by the schematic block as placed (rotated and mirrored) and the world block there: matching (same block, orientation and waterlogging, or air in both), missing (a block in the schematic, air in the world), extra (air in the schematic, a block in the world), or one of the mistakes below.

#### Scenario: Missing
- **WHEN** a locked placement expects oak planks at a position where the world has air
- **THEN** that position is missing

#### Scenario: Air already empty
- **WHEN** the schematic has air at a position where the world has air
- **THEN** that position is matching

### Requirement: Mistake states
A position with a block in both the schematic and the world SHALL be wrong block when the blocks differ, wrong orientation when only the orientation differs, and wrong water when only the waterlogging differs.

#### Scenario: Wrong block
- **WHEN** a locked placement expects oak planks at a position where the world has dirt
- **THEN** that position is wrong block

### Requirement: Orientation properties
A block's orientation SHALL be its values for these block-state properties: facing (all variants), axis, rotation, half, slab type, door hinge and attach face. Waterlogging SHALL be compared on its own. Differences in any other property, such as connections, power, lit, age or stair shape, SHALL NOT make a position wrong.

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
When a world block differs in several ways, its state SHALL be the first that applies of wrong block, wrong orientation, wrong water.

#### Scenario: Wrong facing and not waterlogged
- **WHEN** the schematic has waterlogged oak stairs facing north and the world has dry oak stairs facing south
- **THEN** that position is wrong orientation

### Requirement: Showing the states
For a locked placement:
- Matching and ignored positions SHALL NOT show a ghost.
- Missing and unknown positions SHALL show the normal ghost.
- Positions in the other states SHALL show a box around the block in their colour: red for wrong block, orange for wrong orientation, cyan for wrong water, magenta for extra. No ghost SHALL show at those positions.
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
