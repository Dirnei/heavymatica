# Spec Delta

## ADDED Requirements

### Requirement: Material list of a locked placement
The client SHALL keep a material list for the selected placement while it is LOCKED: per item, how many the placement still needs, how many the player has, and how many are short (needed minus had, never below 0). An unlocked placement SHALL have no material list.

#### Scenario: Short on planks
- **WHEN** the selected locked placement still needs 214 oak planks and the player carries 202
- **THEN** its material list shows oak planks with 214 needed, 202 had and 12 short

#### Scenario: Anchored placement
- **WHEN** the selected placement is ANCHORED
- **THEN** it has no material list

### Requirement: Positions that need an item
A position SHALL need its item when it is missing, wrong block or not compared yet. Matching, wrong orientation, wrong water, extra and ignored positions SHALL NOT need an item.

#### Scenario: Wrong block needs the item
- **WHEN** a locked placement expects oak planks at a position where the world has dirt
- **THEN** that position needs one oak planks

#### Scenario: Turned stairs need nothing
- **WHEN** a position expects oak stairs facing north and holds oak stairs facing east
- **THEN** that position needs no item

#### Scenario: Unloaded part
- **WHEN** part of a locked placement lies in a chunk that is not loaded
- **THEN** its non-air positions there need their items

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
A needed position SHALL count as the item that places its block, with these exceptions:
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
While the overlay shows a LOCKED placement, it SHALL show up to 3 items with the largest shortage, largest first, each with its shortage. When nothing is short, it SHALL say so. When more items are short, it SHALL say how many more and name the key that opens the material list.

#### Scenario: Several items short
- **WHEN** the selected locked placement is short 12 oak planks, 4 stone bricks, 2 glass and 1 torch
- **THEN** the overlay shows oak planks 12, stone bricks 4, glass 2 and that 1 more is short, with the material list key

#### Scenario: Nothing short
- **WHEN** the player carries everything the visible layers still need
- **THEN** the overlay says nothing is short

### Requirement: Material list screen
While a remote is held and the selected placement is LOCKED, the material list key SHALL open a screen listing every needed item with its needed, had and short counts, sorted by most short first, then by most needed, followed by the "can't carry" part. The screen SHALL update while it is open. When the selected placement is not locked, the key SHALL tell the player to lock it first.

#### Scenario: Open the list
- **WHEN** a player holding the remote presses K with a locked placement selected
- **THEN** a screen lists every needed item with needed, had and short, most short first

#### Scenario: Not locked
- **WHEN** a player holding the remote presses K with an anchored placement selected
- **THEN** no screen opens and a message says to lock the placement first

#### Scenario: Picking up items while it is open
- **WHEN** the material list screen is open and the player picks up 12 oak planks
- **THEN** the oak planks row shows 12 more had and 12 less short
