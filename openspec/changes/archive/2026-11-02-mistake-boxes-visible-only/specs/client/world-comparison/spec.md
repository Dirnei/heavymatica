## MODIFIED Requirements

### Requirement: Showing the states
For a locked placement:
- Matching and ignored positions SHALL NOT show a ghost.
- Missing, not enough yet and unknown positions SHALL show the normal ghost.
- Can't check positions SHALL show the unknown-block placeholder while unknown blocks are shown, and no box.
- Boxes around the block SHALL show: red for wrong block, orange for wrong orientation, too many and not enough yet, cyan for wrong water, yellow for needs finishing, magenta for extra. Only not enough yet SHALL keep its ghost.
- Boxes SHALL show only where the player sees them directly. A box, or part of a box, behind a real block SHALL NOT show.
- A box SHALL follow the outline of the block found at its position, such as a flat box around leaf litter or two boxes around stairs. A block that fills its whole position, or has no outline, SHALL get a full cube. Ghosts SHALL NOT hide a box.

#### Scenario: Extra block in the footprint
- **WHEN** a tall grass plant stands where a locked placement has air
- **THEN** a magenta box shows around the grass

#### Scenario: Needs water
- **WHEN** the schematic has a waterlogged oak slab and the world has a dry oak slab there
- **THEN** a cyan box shows around the slab

#### Scenario: Box behind a wall
- **WHEN** a red box is on the far side of a solid wall from the player
- **THEN** the box does not show through the wall

#### Scenario: Box follows a flat block
- **WHEN** a locked placement expects coarse dirt where the world has leaf litter
- **THEN** a flat red box shows around the leaf litter, not a full cube

#### Scenario: Box among ghosts
- **WHEN** the player stands among missing ghosts of the selected locked placement and a wrong block is in plain view beyond them
- **THEN** the wrong block's red box shows through the ghosts

#### Scenario: Mistake inside a mistake region
- **WHEN** a locked placement has a 5 by 5 by 5 cube of wrong blocks and the player looks at it from outside
- **THEN** only the outside of the cube shows red, and no box inside the cube shows through its outer blocks

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

#### Scenario: Can't check
- **WHEN** a locked placement has `create:shaft` at a position, Create is not installed and the world has stone there
- **THEN** the missing-texture placeholder shows there and no box
