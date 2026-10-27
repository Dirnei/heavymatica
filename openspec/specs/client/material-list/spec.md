# client/material-list Specification

## Purpose

Shows what a locked placement still needs against what the player carries, so a player building by hand knows which items to fetch before starting a layer.

## Requirements

### Requirement: Material list of a locked placement
The client SHALL keep a material list for the selected placement while it is LOCKED: per item, how many the placement still needs, how many the player has, and how many are short (needed minus had, never below 0). An unlocked placement SHALL have no material list.

#### Scenario: Short on planks
- **WHEN** the selected locked placement still needs 214 oak planks and the player carries 202
- **THEN** its material list shows oak planks with 214 needed, 202 had and 12 short

#### Scenario: Anchored placement
- **WHEN** the selected placement is ANCHORED
- **THEN** it has no material list

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

### Requirement: The list follows the layer setting
Only positions in the layers the placement's layer setting makes visible SHALL count. The in-hand filter SHALL NOT change the list.

#### Scenario: From-to window
- **WHEN** a locked placement shows layers 4 to 6
- **THEN** its material list counts only what layers 4 to 6 still need

#### Scenario: Follow feet while climbing
- **WHEN** a locked placement is in follow feet and the player climbs from layer 3 to layer 4
- **THEN** the material list grows by what the newly visible layer still needs

#### Scenario: In-hand filter on
- **WHEN** the in-hand filter is on and the player holds oak planks
- **THEN** the material list still lists every item the visible layers need

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

### Requirement: Can't carry
Needed positions whose block no item places (such as water, lava, fire, portals or piston heads) SHALL be listed in a separate "can't carry" part with their block and count. They SHALL NOT show as shortages.

#### Scenario: Water source
- **WHEN** a locked placement still needs 4 water source blocks
- **THEN** the material list shows water 4 under "can't carry" and no shortage for it

### Requirement: What the player has
The items a player has SHALL be the sum over the main inventory, the hotbar, the offhand, the contents of carried shulker boxes and bundles, and the contents of any other carried item that exposes NeoForge's item capability. Items inside those containers SHALL be counted once, also when containers are nested. Containers in the world SHALL NOT count.

#### Scenario: Planks in a shulker box
- **WHEN** the player carries 10 oak planks loose and a shulker box with 64 oak planks
- **THEN** the material list has 74 oak planks

#### Scenario: Chest nearby
- **WHEN** a chest next to the player holds 64 oak planks and the player carries none
- **THEN** the material list has 0 oak planks

### Requirement: Shortage lines in the overlay
While the overlay shows a LOCKED placement, it SHALL show up to 3 items with the largest shortage, largest first, each with its shortage. When nothing is short, it SHALL say so. When more items are short, it SHALL say how many more and name the materials key, which opens the materials screen.

#### Scenario: Several items short
- **WHEN** the selected locked placement is short 12 oak planks, 4 stone bricks, 2 glass and 1 torch
- **THEN** the overlay shows oak planks 12, stone bricks 4, glass 2 and that 1 more is short, with the materials key

#### Scenario: Nothing short
- **WHEN** the player carries everything the visible layers still need
- **THEN** the overlay says nothing is short

#### Scenario: More short with a key
- **WHEN** the materials key is bound to K and the selected locked placement is short of 5 items
- **THEN** the overlay's "more short" line names K and Materials

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
