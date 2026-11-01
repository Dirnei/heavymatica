# Spec Delta

## MODIFIED Requirements

### Requirement: Positions that are not compared
A position of a locked placement SHALL be ignored when its schematic block is `structure_void`, can't check when the block it shows is a block the game does not know, and unknown while it is in a chunk that is not loaded or has not been compared yet.

#### Scenario: Unloaded chunk
- **WHEN** part of a locked placement lies in a chunk that is not loaded
- **THEN** those positions are unknown

#### Scenario: Structure void
- **WHEN** the schematic has `structure_void` at a position where the world has grass
- **THEN** that position is ignored

#### Scenario: Block from a missing mod
- **WHEN** the schematic has `create:shaft` at a position, Create is not installed and the world has stone there
- **THEN** that position is can't check

#### Scenario: Upgraded block is compared
- **WHEN** a schematic from Minecraft 1.20.2 has `minecraft:grass` at a position where the world has short grass
- **THEN** that position is matching

### Requirement: Showing the states
For a locked placement:
- Matching and ignored positions SHALL NOT show a ghost.
- Missing, not enough yet and unknown positions SHALL show the normal ghost.
- Can't check positions SHALL show the unknown-block placeholder while unknown blocks are shown, and no box.
- Boxes around the block SHALL show: red for wrong block, orange for wrong orientation, too many and not enough yet, cyan for wrong water, yellow for needs finishing, magenta for extra. Only not enough yet SHALL keep its ghost.
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
- **THEN** the ghost of the 3 candles shows there with an orange box around the candles

#### Scenario: Single slab where a double slab is planned
- **WHEN** a locked placement expects a double stone slab and the world has a bottom stone slab there
- **THEN** the ghost of the double slab shows there with an orange box around the slab

#### Scenario: Four of three candles
- **WHEN** a locked placement expects white candles with 3 candles and the world has 4 there
- **THEN** an orange box shows around the candles and no ghost shows there

#### Scenario: Can't check
- **WHEN** a locked placement has `create:shaft` at a position, Create is not installed and the world has stone there
- **THEN** the missing-texture placeholder shows there and no box

### Requirement: Progress
A locked placement's progress SHALL be the number of matching positions whose schematic block is not air, divided by the number of positions that are neither air, ignored nor can't check in the schematic. Extra blocks SHALL NOT lower the progress. Unknown positions SHALL count as not done. Layer progress SHALL count the same way over the visible layers only.

#### Scenario: Half built
- **WHEN** a placement has 5006 non-air, non-ignored blocks and 3104 of them match
- **THEN** its progress is 62% (3104 / 5006)

#### Scenario: Extra blocks do not count
- **WHEN** the same placement also has 40 extra blocks
- **THEN** its progress is still 62%

#### Scenario: Visible layers
- **WHEN** the placement is in follow feet showing layers 0 to 4 and those layers have 500 counted blocks of which 440 match
- **THEN** its layer progress is 88%

#### Scenario: Can't check does not count
- **WHEN** a placement has 1000 non-air blocks, 40 of them can't check, and 480 of the rest match
- **THEN** its progress is 50% (480 / 960)

### Requirement: Finishing a placement
A locked placement SHALL finish when its progress becomes 100% while it has at least one counted position. On finishing, the client SHALL play a short success sound, show a toast with the placement's name that says how to bring it back, and remove the placement; when it was selected, no placement SHALL be selected afterwards. When the placement has can't check positions, the toast SHALL also say how many.

#### Scenario: Last block placed
- **WHEN** a locked placement has 200 counted positions, 199 match, and the player places the last right block
- **THEN** the success sound plays, a toast names the placement, and the placement is gone from the world's list

#### Scenario: Locking a finished build
- **WHEN** the player locks a placement over a build that already matches it everywhere
- **THEN** the placement finishes once every counted position has been compared

#### Scenario: Not finished while parts are unknown
- **WHEN** every compared position of a locked placement matches but part of it is in a chunk that is not loaded
- **THEN** the placement does not finish

#### Scenario: Extra blocks do not stop it
- **WHEN** every counted position matches and the footprint also has extra blocks
- **THEN** the placement finishes

#### Scenario: Empty schematic
- **WHEN** a locked placement has no counted positions
- **THEN** it does not finish

#### Scenario: Finished with blocks it can't check
- **WHEN** every counted position of a locked placement matches and 40 of its positions can't check
- **THEN** the success sound plays and the toast names the placement and says 40 blocks couldn't be checked

#### Scenario: Only blocks it can't check
- **WHEN** every non-air position of a locked placement can't check
- **THEN** it does not finish
