## MODIFIED Requirements

### Requirement: Positions that need an item
A position SHALL need its item when it is missing, wrong block or not compared yet. A position that is not enough yet SHALL need only the rest: its planned amount minus the amount in the world, in the same items, or, for a two-item block on its empty base, only the content item. Matching, wrong orientation, too many, wrong water, needs finishing, extra and ignored positions SHALL NOT need an item.

#### Scenario: Wrong block needs the item
- **WHEN** a locked placement expects oak planks at a position where the world has dirt
- **THEN** that position needs one oak planks

#### Scenario: Turned stairs need nothing
- **WHEN** a position expects oak stairs facing north and holds oak stairs facing east
- **THEN** that position needs no item

#### Scenario: Unloaded part
- **WHEN** part of a locked placement lies in a chunk that is not loaded
- **THEN** its non-air positions there need their items

#### Scenario: Dirt laid, path not made
- **WHEN** a locked placement has 20 dirt paths, 15 have dirt and need a shovel and 5 are missing
- **THEN** its material list needs 5 dirt

#### Scenario: One of three candles
- **WHEN** a position expects white candles with 3 candles and holds 1 white candle
- **THEN** that position needs two white candles

#### Scenario: Half a double slab
- **WHEN** a position expects a double stone slab and holds a bottom stone slab
- **THEN** that position needs one stone slab

#### Scenario: Too many candles
- **WHEN** a position expects white candles with 3 candles and holds 4
- **THEN** that position needs no item

#### Scenario: Adding a candle
- **WHEN** a position expects white candles with 3 candles, holds 1, and the player adds a second candle
- **THEN** that position needs one white candle instead of two

#### Scenario: Pot placed, plant missing
- **WHEN** a position expects a potted dead bush and holds an empty flower pot
- **THEN** that position needs one dead bush and no flower pot

#### Scenario: Other plant in the pot
- **WHEN** a position expects a potted dead bush and holds a potted poppy
- **THEN** that position needs one flower pot and one dead bush

### Requirement: Blocks become items
A needed position SHALL count as the item that places its block, or as the item the equivalents table gives for its block when it has a row, and blocks placed with the same item SHALL add up in one row. The exceptions:
- a block made of two positions (doors, beds, tall plants) SHALL count once, at its lower or foot position
- a double slab SHALL count as two of its slab
- candles, sea pickles and snow layers SHALL count as many items as the block state says
- a two-item block SHALL count as one item of its base and one of its content, each adding up with the same item elsewhere in the placement; it SHALL NOT be listed under "can't carry"

#### Scenario: Door
- **WHEN** a locked placement still needs an oak door (lower and upper half)
- **THEN** its material list needs one oak door

#### Scenario: Double slab
- **WHEN** a position needs a double stone slab
- **THEN** it counts as two stone slabs

#### Scenario: Candles
- **WHEN** a position needs white candles with 3 candles
- **THEN** it counts as three white candles

#### Scenario: Paths and farmland
- **WHEN** a locked placement still needs 6 dirt paths, 4 farmland and 2 grass blocks
- **THEN** its material list needs 12 dirt

#### Scenario: Potted plants
- **WHEN** a locked placement still needs 2 potted dead bushes, 1 potted poppy and 1 empty flower pot
- **THEN** its material list needs 4 flower pots, 2 dead bushes and 1 poppy, and nothing under "can't carry"

#### Scenario: Water cauldrons
- **WHEN** a locked placement still needs 3 water cauldrons
- **THEN** its material list needs 3 cauldrons and 3 water buckets
