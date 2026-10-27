# Spec Delta

## Purpose

Lets a player point at the ghost of a locked placement as if it were a real block: see which block belongs there and
how the world differs, and pick its item with middle click, on any server.

## ADDED Requirements

### Requirement: Targetable ghost cells
A position SHALL be targetable only when it is drawn: it belongs to a LOCKED placement in the player's dimension, selected or not, lies in the layers its layer setting shows, and either shows a ghost that passes the in-hand filter (dimmed counts) or shows a red, orange, cyan or yellow box. Matching, ignored, air and extra positions, and every position of FOLLOWING and ANCHORED placements, SHALL NOT be targetable.

#### Scenario: Missing block in a visible layer
- **WHEN** a locked placement shows layers 0 to 4 and expects oak planks on layer 2 where the world has air
- **THEN** that position is targetable

#### Scenario: Outside the visible layers
- **WHEN** a locked placement shows layers 4 to 6 and expects oak planks on layer 2 where the world has air
- **THEN** that position is not targetable

#### Scenario: Hidden by the in-hand filter
- **WHEN** the in-hand filter is on, the player holds oak planks, no cobblestone is in the hotbar, and a visible position expects cobblestone where the world has air
- **THEN** that position is not targetable

#### Scenario: Dimmed by the in-hand filter
- **WHEN** the in-hand filter is on, the player holds oak planks with oak stairs in the hotbar, and a visible position expects oak stairs where the world has air
- **THEN** that position is targetable

#### Scenario: Already built
- **WHEN** a locked placement expects oak planks at a position where the world has oak planks
- **THEN** that position is not targetable

#### Scenario: Anchored placement
- **WHEN** a placement is ANCHORED and the player looks at its ghost
- **THEN** none of its positions is targetable

### Requirement: Ghost target under the crosshair
The client SHALL find the ghost target by following the player's line of sight from the eye, position by position, up to the targeting range. It SHALL stop at the first position that is targetable or where the line of sight hits a real block. A targetable position SHALL be the target even when a real block is there; a real block anywhere else SHALL end the search without a target. Positions the player's own body takes up SHALL be passed over.

#### Scenario: Standing in a ghost
- **WHEN** the player stands in a missing ghost block at their feet and looks straight ahead at a missing ghost block 2 blocks away
- **THEN** the ghost block 2 blocks away is the target

#### Scenario: Ghost in front of a wall
- **WHEN** the player looks at a missing ghost block that stands 3 blocks in front of a stone wall
- **THEN** the ghost block is the target

#### Scenario: Wall in front of a ghost
- **WHEN** a stone wall that belongs to no placement stands between the player and a missing ghost block
- **THEN** there is no ghost target

#### Scenario: Nearest ghost wins
- **WHEN** the line of sight passes two targetable positions, 4 and 7 blocks away
- **THEN** the position 4 blocks away is the target

#### Scenario: Through matching blocks
- **WHEN** the line of sight passes through air at positions that already match and then reaches a missing ghost block
- **THEN** the missing ghost block is the target

### Requirement: Mistakes are targeted as the planned block
When the line of sight hits a real block at a position of a locked placement that is wrong block, wrong orientation, wrong water or needs finishing, and that position is in a visible layer, the target SHALL be that position with its planned block, and the block found there SHALL be part of the target.

#### Scenario: Dirt where planks are planned
- **WHEN** the player looks at dirt at a visible position where a locked placement expects oak planks
- **THEN** the target is that position with oak planks planned and dirt found

#### Scenario: Extra block
- **WHEN** the player looks at a tall grass plant where a locked placement has air
- **THEN** there is no ghost target

### Requirement: Overlapping placements
When one position is targetable in more than one placement, the selected placement's position SHALL be the target.

#### Scenario: Selected placement first
- **WHEN** locked placements `Gate` and `Wall` both have a missing ghost at the position under the crosshair and `Wall` is selected
- **THEN** the target is `Wall`'s position

### Requirement: The target follows the swaps
The target's planned block SHALL be the block the placement shows there after its material swaps, rotation and mirror.

#### Scenario: Swapped stairs
- **WHEN** a locked placement swaps oak stairs to spruce stairs and the player targets one of them, facing north after rotation
- **THEN** the target's planned block is spruce stairs facing north

### Requirement: Targeting HUD
While there is a ghost target and no screen is open, the client SHALL show a small HUD below the crosshair with the planned block's item icon and name, and the values of its orientation properties and waterlogging. A planned block that no item places SHALL show its name without an icon.

#### Scenario: Stairs
- **WHEN** the target's planned block is oak stairs facing north, bottom half
- **THEN** the HUD shows the oak stairs icon, `Oak Stairs`, and facing north and half bottom

#### Scenario: No target
- **WHEN** the player looks at the sky
- **THEN** no targeting HUD shows

#### Scenario: Screen open
- **WHEN** the player opens the inventory while looking at a ghost
- **THEN** no targeting HUD shows

### Requirement: Target state in the HUD
The HUD SHALL show the target's state on its own line, in the comparison colours: missing; wrong block in red, naming the found block; wrong orientation in orange, with the found block's orientation; wrong water in cyan, saying whether water is needed or must go; needs finishing in yellow, naming the step; and not compared yet, muted, while the position is unknown.

#### Scenario: Wrong block
- **WHEN** the target expects oak planks and the world has dirt there
- **THEN** the HUD shows a red line naming dirt

#### Scenario: Needs a shovel
- **WHEN** the target expects a dirt path and the world has a grass block there
- **THEN** the HUD shows a yellow line saying it needs a shovel

#### Scenario: Just locked
- **WHEN** the player has just locked a placement and targets a position that has not been compared yet
- **THEN** the HUD shows the planned block and that it is not compared yet

### Requirement: Other placements in the HUD
When the target belongs to a placement that is not selected, the HUD SHALL also show that placement's name.

#### Scenario: Unselected placement
- **WHEN** `Gate` is selected and the player targets a ghost of the locked placement `Tower`
- **THEN** the HUD shows `Tower`

#### Scenario: Selected placement
- **WHEN** the player targets a ghost of the selected placement
- **THEN** the HUD shows no placement name

### Requirement: Targeting range
The targeting range SHALL be a client setting, default 32 blocks, from 0 to 128; 0 SHALL turn the HUD off. Ghost picking SHALL still find targets within reach when the range is smaller. When the target is beyond the player's block-interaction reach, the HUD SHALL add a muted hint that it is out of reach.

#### Scenario: Far ghost
- **WHEN** the player looks at a missing ghost 20 blocks away with the default range
- **THEN** the HUD shows it with an out of reach hint

#### Scenario: Beyond the range
- **WHEN** the player looks at a missing ghost 40 blocks away with the default range
- **THEN** no targeting HUD shows

#### Scenario: HUD off
- **WHEN** the range is 0 and the player looks at a missing ghost 3 blocks away
- **THEN** no targeting HUD shows and middle click still picks it

### Requirement: Jade shows the ghost target
When Jade is installed, Jade's tooltip SHALL show the ghost target as its planned block, with a Heavymatica line giving its state as in the HUD, and the targeting HUD SHALL NOT show. Without Jade, nothing SHALL depend on it.

#### Scenario: Jade installed
- **WHEN** Jade is installed and the player looks at a missing ghost of oak stairs in front of a stone wall
- **THEN** Jade's tooltip shows oak stairs and that they are missing, and the targeting HUD does not show

#### Scenario: Jade not installed
- **WHEN** Jade is not installed
- **THEN** the mod loads and the targeting HUD shows the ghost target

### Requirement: Middle click picks the ghost
Pick block SHALL pick the ghost target, on the client and on any server, when the ghost pick client setting is on (the default), the player is not sneaking, and the target is within block-interaction reach. In every other case, and when no item places the planned block, pick block SHALL behave as without Heavymatica.

#### Scenario: Ghost within reach
- **WHEN** a player carrying oak stairs middle-clicks a missing oak stairs ghost 3 blocks away on a vanilla server
- **THEN** the oak stairs are in the main hand afterwards

#### Scenario: Sneaking
- **WHEN** the player sneaks and middle-clicks a ghost in front of a stone wall
- **THEN** vanilla's pick block runs on the stone wall

#### Scenario: Out of reach
- **WHEN** the player middle-clicks a ghost 10 blocks away
- **THEN** vanilla's pick block runs

#### Scenario: Setting off
- **WHEN** the ghost pick setting is off and the player middle-clicks a ghost within reach
- **THEN** vanilla's pick block runs

### Requirement: Which item is picked
The candidate items for a planned block SHALL be, in this order: its own item, the item that places it, then the items the equivalents table accepts for it, in the order the material list takes them. The first candidate the player carries in the hotbar or main inventory SHALL be picked.

#### Scenario: Grass from dirt
- **WHEN** the player middle-clicks a grass block ghost, carrying dirt and no grass blocks
- **THEN** dirt is picked

#### Scenario: Own item first
- **WHEN** the player middle-clicks a grass block ghost, carrying both grass blocks and dirt
- **THEN** grass blocks are picked

#### Scenario: Swapped block
- **WHEN** a placement swaps oak planks to spruce planks and the player middle-clicks one of them, carrying both
- **THEN** spruce planks are picked

### Requirement: Picking from the inventory
When the picked item is in the hotbar, its slot SHALL be selected, the selected slot first and then the lowest. Otherwise it SHALL be swapped from the main inventory into the hotbar slot vanilla's pick block would use, and that slot selected. No item SHALL be lost or duplicated.

#### Scenario: In the hotbar
- **WHEN** oak stairs are in hotbar slot 6 and the player middle-clicks an oak stairs ghost
- **THEN** slot 6 is selected and the inventory is unchanged

#### Scenario: In the main inventory
- **WHEN** oak stairs are only in the main inventory, hotbar slot 3 is empty, and the player middle-clicks an oak stairs ghost
- **THEN** the oak stairs move to slot 3 and slot 3 is selected

### Requirement: Picking in creative
In creative mode, when no candidate is in the hotbar or main inventory, the planned block's first candidate SHALL be put into the hotbar slot vanilla's creative pick block would use, and that slot selected.

#### Scenario: Creative, not carried
- **WHEN** a creative player with no oak stairs middle-clicks an oak stairs ghost
- **THEN** oak stairs are in the main hand

#### Scenario: Creative, carried
- **WHEN** a creative player has oak stairs in hotbar slot 2 and middle-clicks an oak stairs ghost
- **THEN** slot 2 is selected and no other slot changes

### Requirement: Items only in containers
When no candidate is in the hotbar or main inventory but one is inside a carried shulker box or bundle, and the player is not in creative, pick block SHALL show an action-bar message naming the item and the kind of container, and SHALL NOT change the inventory.

#### Scenario: Stairs in a shulker box
- **WHEN** a survival player's only oak stairs are in a carried shulker box and the player middle-clicks an oak stairs ghost
- **THEN** the action bar says the oak stairs are in a shulker box and the inventory is unchanged

### Requirement: Items not carried
When the player is not in creative and carries no candidate at all, pick block SHALL show an action-bar message naming the planned block's first candidate, and SHALL NOT change the inventory.

#### Scenario: No stairs
- **WHEN** a survival player without oak stairs middle-clicks an oak stairs ghost
- **THEN** the action bar says `You have no Oak Stairs` and the selected slot stays the same
