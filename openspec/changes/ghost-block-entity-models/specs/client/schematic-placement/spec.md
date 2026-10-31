# Spec Delta

## ADDED Requirements

### Requirement: Block-entity blocks in ghosts
The ghost SHALL show blocks that the game draws with a block-entity renderer (chests, beds, shulker boxes, signs, banners, heads, decorated pots, bells, conduits, copper golem statues and the like) with their own model and texture, facing and split as their block state says. Without block-entity data a ghost SHALL show the block's default look.

#### Scenario: Bed
- **WHEN** a placement contains a red bed whose head faces north
- **THEN** the ghost shows a red bed with its head and foot in those two positions, facing north

#### Scenario: Double chest
- **WHEN** a placement contains a double chest
- **THEN** the ghost shows one double chest across both positions, with the chest texture

#### Scenario: Sign without its text
- **WHEN** a placement contains an oak wall sign whose block-entity data holds text
- **THEN** the ghost shows a blank oak wall sign on the wall it hangs on

#### Scenario: Rotated placement
- **WHEN** a placement containing a north-facing chest is rotated 90 degrees clockwise
- **THEN** the ghost shows the chest facing east

#### Scenario: Layer view
- **WHEN** a locked placement shows only the layer that contains a bed
- **THEN** the ghost shows the bed in that layer and nothing of the layers hidden by the layer setting

### Requirement: Fluids in ghosts
The ghost SHALL show water and lava, including the fluid inside waterlogged blocks, as translucent fluid surfaces. Tinted blocks and fluids SHALL use plains colours, since a schematic has no biome.

#### Scenario: Water source
- **WHEN** a placement contains a pool of water
- **THEN** the ghost shows the pool as blue water

#### Scenario: Waterlogged stairs
- **WHEN** a placement contains waterlogged stairs
- **THEN** the ghost shows the stairs and the water in them

#### Scenario: Grass and leaves
- **WHEN** a placement contains grass blocks and oak leaves
- **THEN** the grass shows plains grass green and the leaves plains foliage green

### Requirement: Blocks without geometry stay visible
A block that is not air and yields neither model, block-entity nor fluid geometry SHALL be shown as its outline shape in its map colour. Blocks that are invisible in the world (barriers, light blocks, structure voids) SHALL stay hidden.

#### Scenario: Modded block without a display model
- **WHEN** a placement contains a block from a mod that draws it only with its own block-entity renderer and gives it no display model
- **THEN** the ghost shows that block's outline shape in its map colour

#### Scenario: Barrier
- **WHEN** a placement contains a barrier
- **THEN** the ghost shows nothing at its position
