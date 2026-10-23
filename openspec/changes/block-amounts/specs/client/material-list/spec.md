# Spec Delta

## MODIFIED Requirements

### Requirement: Positions that need an item
A position SHALL need its item when it is missing, wrong block or not compared yet. A position that is not enough yet SHALL need only the rest: its planned amount minus the amount in the world, in the same items. Matching, wrong orientation, too many, wrong water, needs finishing, extra and ignored positions SHALL NOT need an item.

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
