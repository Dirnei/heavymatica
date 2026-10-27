# client/block-equivalents Specification

## Purpose
Lets planned blocks that are not placed as themselves, such as grass, dirt paths, stripped logs and concrete, be
built the way the game allows: the material list asks for the item that is really placed, and the world comparison
accepts equivalent blocks and marks blocks that only need a finishing step.

## Requirements

### Requirement: Equivalents table
The client SHALL keep a table of block equivalents. Each row SHALL name a planned block and give the item that places it, the blocks that count as the planned block, and the blocks that only need a finishing step together with that step: a shovel, a hoe, an axe or water. A planned block without a row SHALL be placed, counted and compared as itself.

#### Scenario: Block without a row
- **WHEN** a locked placement expects oak planks and the world has dirt there
- **THEN** that position is wrong block and the material list needs oak planks

#### Scenario: Podzol keeps its own item
- **WHEN** a locked placement still needs 5 podzol
- **THEN** its material list needs 5 podzol and no dirt

### Requirement: Dirt and grass rows
A planned grass block SHALL be placed with dirt, and dirt there SHALL count as the grass block. A planned dirt block SHALL be placed with dirt, and a grass block there SHALL count as the dirt.

#### Scenario: Dirt where grass is planned
- **WHEN** a locked placement expects a grass block and the world has dirt there
- **THEN** that position is matching

#### Scenario: Grass spread over planned dirt
- **WHEN** a locked placement expects dirt and the world has a grass block there
- **THEN** that position is matching

#### Scenario: Grass needs dirt
- **WHEN** a locked placement still needs 30 grass blocks and 10 dirt
- **THEN** its material list needs 40 dirt and no grass blocks

### Requirement: Dirt path and farmland rows
A planned dirt path SHALL be placed with dirt, and dirt, a grass block, podzol, mycelium, coarse dirt or rooted dirt there SHALL need a shovel. Planned farmland SHALL be placed with dirt, and dirt, a grass block or a dirt path there SHALL need a hoe.

#### Scenario: Path not made yet
- **WHEN** a locked placement expects a dirt path and the world has a grass block there
- **THEN** that position needs finishing with a shovel

#### Scenario: Podzol under a planned path
- **WHEN** a locked placement expects dirt paths and the world has podzol, mycelium, coarse dirt and rooted dirt at four of them
- **THEN** those four positions need finishing with a shovel

#### Scenario: Path where farmland is planned
- **WHEN** a locked placement expects farmland and the world has a dirt path there
- **THEN** that position needs finishing with a hoe

#### Scenario: Farmland dried out
- **WHEN** a locked placement expects farmland, the player tilled it, and it later turned back into dirt
- **THEN** that position needs finishing with a hoe, not wrong block

#### Scenario: Wrong finish
- **WHEN** a locked placement expects a dirt path and the world has farmland there
- **THEN** that position is wrong block

### Requirement: Stripped wood rows
Every stripped log, stripped wood, stripped stem, stripped hyphae and the stripped bamboo block SHALL be placed with its unstripped block of the same wood type, and that unstripped block there SHALL need an axe. This SHALL hold for every wood type, including those added by mods that name their blocks the same way.

#### Scenario: Unstripped log on the same axis
- **WHEN** a locked placement expects a stripped oak log on the x axis and the world has an oak log on the x axis
- **THEN** that position needs finishing with an axe

#### Scenario: Stripped bamboo
- **WHEN** a locked placement still needs 8 stripped bamboo blocks
- **THEN** its material list needs 8 bamboo blocks

#### Scenario: Other wood type
- **WHEN** a locked placement expects a stripped oak log and the world has a spruce log there
- **THEN** that position is wrong block

### Requirement: Concrete rows
Concrete of each of the 16 colours SHALL be placed with concrete powder of the same colour, and that powder there SHALL need water.

#### Scenario: Powder not hardened yet
- **WHEN** a locked placement expects white concrete and the world has white concrete powder there
- **THEN** that position needs finishing with water

#### Scenario: Wrong colour
- **WHEN** a locked placement expects white concrete and the world has red concrete powder there
- **THEN** that position is wrong block

#### Scenario: Concrete where powder is planned
- **WHEN** a locked placement expects white concrete powder and the world has white concrete there
- **THEN** that position is wrong block

### Requirement: Rows the game cannot use
A row SHALL be ignored for a planned block when the game does not know the block that places it or that block has no item; the planned block SHALL then be placed, counted and compared as itself. Blocks of a row the game does not know SHALL be left out of that row.

#### Scenario: Stripped log without an unstripped one
- **WHEN** a mod adds a stripped fir log but no fir log, and a locked placement still needs 4 stripped fir logs
- **THEN** its material list needs 4 stripped fir logs

### Requirement: Swaps come first
The table SHALL apply to the block a placement's swap produces, not to the schematic's original block.

#### Scenario: Swapped to a dirt path
- **WHEN** a locked placement swaps grass blocks to dirt paths and the world has a grass block at one of them
- **THEN** that position needs finishing with a shovel and the material list counts dirt for the swapped positions

#### Scenario: Swapped away from grass
- **WHEN** a locked placement swaps grass blocks to moss blocks and the world has dirt at one of them
- **THEN** that position is wrong block and the material list needs moss blocks

### Requirement: Needs finishing state
A position of a locked placement SHALL need finishing when the world holds a block that the table lists as needing a finishing step for the planned block there, with the same orientation and waterlogging. A block the table accepts as the planned block SHALL be compared as the planned block itself.

#### Scenario: Accepted block
- **WHEN** a locked placement expects dirt and the world has a grass block there
- **THEN** that position is matching, the same as with dirt

#### Scenario: Stripping keeps the axis
- **WHEN** a locked placement expects a stripped oak log on the y axis and the world has a stripped oak log on the y axis
- **THEN** that position is matching

### Requirement: Finishing is not done
Positions that need finishing SHALL NOT count as matching for the progress or the layer progress.

#### Scenario: Ten paths not made yet
- **WHEN** a placement has 100 counted blocks, 90 of them match and 10 need a shovel
- **THEN** its progress is 90%

### Requirement: Carried items toward a planned block
Need and have SHALL be kept per planned block, not per item. A carried item SHALL count toward a planned block's have when it places the planned block itself, the block the table places it with, a block the table accepts in its place, or a block the table lists as needing finishing into it.

#### Scenario: Path items for farmland
- **WHEN** a locked placement still needs 10 farmland and 10 dirt paths, and the player carries 20 dirt path items
- **THEN** both planned blocks have what they need and nothing is short

#### Scenario: Podzol for paths
- **WHEN** a locked placement still needs 12 dirt paths and the player carries 3 podzol, 3 mycelium, 3 coarse dirt and 3 rooted dirt
- **THEN** the dirt paths have 12 and nothing is short

#### Scenario: Concrete and powder for concrete
- **WHEN** a locked placement still needs 64 white concrete and the player carries 32 white concrete and 32 white concrete powder
- **THEN** the white concrete has 64 and nothing is short

#### Scenario: Concrete is not powder
- **WHEN** a locked placement still needs 64 white concrete powder and the player carries 64 white concrete and no powder
- **THEN** the white concrete powder has 0 and is 64 short

### Requirement: Sharing carried items
Each carried item SHALL count toward at most one planned block. Planned blocks SHALL take carried items one after another, those accepting the fewest kinds of item first, ties by block ID, each up to its need. A planned block SHALL take its own item first, then the item that places it, then the items of the blocks the table accepts in its place, then those of its finishing blocks, in table order. What a planned block was not given SHALL be its shortage.

#### Scenario: Powder is served first
- **WHEN** a locked placement still needs 64 white concrete and 64 white concrete powder, and the player carries 64 white concrete and 64 white concrete powder
- **THEN** the powder takes the 64 powder, the concrete takes the 64 concrete, and nothing is short

#### Scenario: Not enough for both
- **WHEN** a locked placement still needs 64 white concrete and 64 white concrete powder, and the player carries 96 white concrete powder and nothing else
- **THEN** the powder takes 64, the concrete takes the other 32 and is 32 short

#### Scenario: Dirt goes to dirt first
- **WHEN** a locked placement still needs 10 dirt and 10 dirt paths, and the player carries 10 dirt and 10 podzol
- **THEN** the dirt takes the 10 dirt, the dirt paths take the 10 podzol, and nothing is short

### Requirement: Needs per planned block in the materials screen
While the placement shown in the materials screen is LOCKED, each row SHALL show the needed and short counts of the planned block it shows as, and as had what that block was given plus the carried items of its placing item that no planned block took. Rows that show as the same planned block SHALL show the same counts; rows of different planned blocks SHALL NOT share counts because they share a placing item.

#### Scenario: Concrete and powder rows
- **WHEN** a locked placement still needs 64 white concrete and 64 white concrete powder, and the player carries 64 white concrete
- **THEN** the white concrete row shows 64 needed, 64 had and 0 short, and the white concrete powder row shows 64 needed, 0 had and 64 short

#### Scenario: Grass and path rows
- **WHEN** a locked placement still needs 10 grass blocks and 5 dirt paths, and the player carries 10 grass blocks and no dirt
- **THEN** the grass block row shows 10 needed, 10 had and 0 short, and the dirt path row shows 5 needed, 0 had and 5 short

### Requirement: Finish column in the materials screen
While the placement shown in the materials screen is LOCKED, each row SHALL show how many positions of the block it shows as need finishing in the visible layers, and with which step, when there are any.

#### Scenario: Paths in the screen
- **WHEN** a locked placement's visible layers have 15 dirt paths that need a shovel and 5 missing dirt paths, and 10 missing grass blocks
- **THEN** the dirt path row shows 15 to finish with a shovel and 5 needed, and the grass block row shows 10 needed and nothing to finish

#### Scenario: Not locked
- **WHEN** the materials screen shows an anchored placement with dirt paths
- **THEN** no finishing counts show
