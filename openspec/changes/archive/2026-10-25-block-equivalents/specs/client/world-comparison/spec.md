# Spec Delta

## MODIFIED Requirements

### Requirement: Cell states
Every other position SHALL be in exactly one state, decided by the schematic block as placed (rotated and mirrored) and the world block there: matching (same block, or one the equivalents table accepts in its place, with the same orientation and waterlogging, or air in both), missing (a block in the schematic, air in the world), extra (air in the schematic, a block in the world), needs finishing, or one of the mistakes below.

#### Scenario: Missing
- **WHEN** a locked placement expects oak planks at a position where the world has air
- **THEN** that position is missing

#### Scenario: Air already empty
- **WHEN** the schematic has air at a position where the world has air
- **THEN** that position is matching

### Requirement: Mistake states
A position with a block in both the schematic and the world SHALL be wrong block when the blocks differ and the equivalents table neither accepts the world block in place of the planned block nor lists it as needing a finishing step, wrong orientation when only the orientation differs, and wrong water when only the waterlogging differs.

#### Scenario: Wrong block
- **WHEN** a locked placement expects oak planks at a position where the world has dirt
- **THEN** that position is wrong block

### Requirement: The first fix decides the state
When a world block differs in several ways, its state SHALL be the first that applies of wrong block, wrong orientation, wrong water, needs finishing.

#### Scenario: Wrong facing and not waterlogged
- **WHEN** the schematic has waterlogged oak stairs facing north and the world has dry oak stairs facing south
- **THEN** that position is wrong orientation

#### Scenario: Log on the wrong axis
- **WHEN** a locked placement expects a stripped oak log on the y axis and the world has an oak log on the x axis
- **THEN** that position is wrong orientation

### Requirement: Showing the states
For a locked placement:
- Matching and ignored positions SHALL NOT show a ghost.
- Missing and unknown positions SHALL show the normal ghost.
- Positions in the other states SHALL show a box around the block in their colour: red for wrong block, orange for wrong orientation, cyan for wrong water, yellow for needs finishing, magenta for extra. No ghost SHALL show at those positions.
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
