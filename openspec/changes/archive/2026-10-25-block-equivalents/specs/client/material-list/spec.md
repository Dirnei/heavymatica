# Spec Delta

## MODIFIED Requirements

### Requirement: Positions that need an item
A position SHALL need its item when it is missing, wrong block or not compared yet. Matching, wrong orientation, wrong water, needs finishing, extra and ignored positions SHALL NOT need an item.

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

### Requirement: Blocks become items
A needed position SHALL count as the item that places its block, or as the item the equivalents table gives for its block when it has a row, and blocks placed with the same item SHALL add up in one row. The exceptions:
- a block made of two positions (doors, beds, tall plants) SHALL count once, at its lower or foot position
- a double slab SHALL count as two of its slab
- candles, sea pickles and snow layers SHALL count as many items as the block state says

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

## ADDED Requirements

### Requirement: Had follows the planned blocks
An item's had SHALL be the carried items given to the planned blocks it places, shared out as `client/block-equivalents` "Sharing carried items" says, plus its own carried items that no planned block took. Its short SHALL be what those planned blocks were not given, so no carried item counts twice.

#### Scenario: Silk-touched grass
- **WHEN** a locked placement still needs 30 grass blocks and 10 dirt, and the player carries 10 dirt and 30 grass blocks
- **THEN** its material list shows dirt with 40 needed, 40 had and 0 short

#### Scenario: Concrete already hardened
- **WHEN** a locked placement still needs 64 white concrete and the player carries 64 white concrete
- **THEN** its material list shows white concrete powder with 64 needed, 64 had and 0 short

#### Scenario: Concrete is not powder
- **WHEN** a locked placement still needs 64 white concrete powder and the player carries 64 white concrete and no powder
- **THEN** its material list shows white concrete powder with 64 needed, 0 had and 64 short

#### Scenario: Path items for farmland
- **WHEN** a locked placement still needs 10 farmland and the player carries 10 dirt path items and no dirt
- **THEN** its material list shows dirt with 10 needed, 10 had and 0 short

#### Scenario: Spare planks
- **WHEN** a locked placement still needs 300 oak planks and the player carries 512
- **THEN** its material list shows oak planks with 300 needed, 512 had and 0 short
