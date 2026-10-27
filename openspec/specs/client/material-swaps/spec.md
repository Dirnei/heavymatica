# client/material-swaps Specification

## Purpose
Lets a player replace block types of a schematic with other blocks per placement, without editing or re-uploading
the schematic, and shows a placement's blocks and, while it is locked, its material list in one materials screen.

## Requirements

### Requirement: Per-block swaps
A placement SHALL hold a set of swaps, each from a block type of its schematic to another block. A swap SHALL apply to every position whose schematic block is of that type, in every state. Block types without a swap SHALL stay as they are, also when they share a material with a swapped one. Air and structure void SHALL NOT be swappable.

#### Scenario: Stairs only
- **WHEN** a placement of a schematic with oak planks and oak stairs swaps oak stairs to spruce stairs
- **THEN** its stairs show as spruce stairs and its planks still show as oak planks

### Requirement: States carry over by property name
A swapped position SHALL keep every block-state property that the new block also has, with the same value, when that value is allowed for the new block. Properties the new block lacks SHALL be dropped, and properties only the new block has SHALL take its default value.

#### Scenario: Stairs to stairs
- **WHEN** oak stairs facing east, upside down and waterlogged are swapped to spruce stairs
- **THEN** the position shows spruce stairs facing east, upside down and waterlogged

#### Scenario: Stairs to a full block
- **WHEN** oak stairs facing east are swapped to spruce planks
- **THEN** the position shows spruce planks

### Requirement: Swaps do not chain
A swap SHALL only apply to the schematic's original blocks. The block a swap produces SHALL NOT be swapped again by another swap of the same placement.

#### Scenario: Two swaps
- **WHEN** a schematic has oak planks and spruce planks and the placement swaps oak planks to spruce planks and spruce planks to birch planks
- **THEN** the former oak planks show as spruce planks and the former spruce planks show as birch planks

### Requirement: Everything follows the swaps
Ghosts, the in-hand filter, the world comparison, the expected block shown in the overlay and the material list SHALL all use the swapped blocks.

#### Scenario: Comparison after a swap
- **WHEN** a locked placement whose oak stairs are built swaps oak stairs to spruce stairs
- **THEN** the built oak stairs become wrong block and the material list needs spruce stairs

#### Scenario: In-hand filter
- **WHEN** a placement swaps oak stairs to spruce stairs, the in-hand filter is on and the player holds spruce stairs
- **THEN** the swapped stairs show at normal opacity

### Requirement: Swaps in every state
Swaps SHALL be changeable while the placement is FOLLOWING, ANCHORED or LOCKED. Changing a swap of a locked placement SHALL start its comparison over with the new blocks.

#### Scenario: Reset on a locked placement
- **WHEN** a locked placement swapped oak stairs to spruce stairs and the player resets that swap
- **THEN** the placement shows oak stairs again and built oak stairs match again

### Requirement: Materials screen
While a remote is held, the wheel's Materials segment or the bound materials key SHALL open one screen for the selected placement in every state. It SHALL list every block type of the placement's schematic except air and structure void with how many positions use it and what it shows as. Each row SHALL pick a replacement or reset the swap, at once. When no placement is selected, the materials key SHALL tell the player to select one.

#### Scenario: Open the screen
- **WHEN** a player holding the remote picks Materials on the wheel with the anchored `Castle Gate` selected
- **THEN** a screen lists `Castle Gate`'s block types with their counts and what each shows as, most used first, without needed, had or short counts

#### Scenario: No selection
- **WHEN** a player has bound the materials key to N and presses N while holding the remote with no placement selected
- **THEN** no screen opens and a message says to select a placement first

#### Scenario: Bound key on an anchored placement
- **WHEN** a player has bound the materials key to N and presses N while holding the remote with an anchored placement selected
- **THEN** the materials screen opens

### Requirement: Materials screen search and updates
A search box in the materials screen SHALL narrow the rows by the name or ID of the block or of what it shows as. While the schematic is still loading, the screen SHALL say so. The screen SHALL update while it is open and SHALL NOT pause the game.

#### Scenario: Search
- **WHEN** the screen lists oak stairs swapped to spruce stairs, oak planks and stone bricks, and the player types `spruce`
- **THEN** only the oak stairs row is listed

### Requirement: Needs on a locked placement
While the placement shown in the materials screen is LOCKED, each row SHALL also show, for the item that places the block the row shows as, how many its material list still needs, how many the player has and how many are short, with a shortage above 0 in red. Rows whose blocks show as the same item SHALL show the same counts. While the placement is not locked, the screen SHALL show none of these counts.

#### Scenario: Locked placement
- **WHEN** the selected locked placement swaps stone bricks to deepslate bricks, still needs 800 deepslate bricks and 300 oak planks, and the player has 512 oak planks and no deepslate bricks
- **THEN** the stone bricks row shows deepslate bricks with 800 needed, 0 had and 800 short in red, listed above the oak planks row with 300 needed, 512 had and 0 short

#### Scenario: Shared item
- **WHEN** a locked placement swaps oak stairs to spruce stairs and also contains spruce stairs
- **THEN** both rows show the same needed, had and short counts for spruce stairs

#### Scenario: Picking up items while it is open
- **WHEN** the screen is open on a locked placement and the player picks up 12 oak planks
- **THEN** the oak planks row shows 12 more had and up to 12 less short

### Requirement: Materials screen order and can't carry
While the placement is LOCKED, the materials screen's rows SHALL be sorted by most short first, then most needed, then most used, and the blocks no item places SHALL follow in a separate "can't carry" part with how many positions still need them. While it is not locked, the rows SHALL be sorted by most used first and there SHALL be no "can't carry" part.

#### Scenario: Water
- **WHEN** a locked placement still needs 12 water source blocks
- **THEN** the screen lists water 12 under "can't carry"

### Requirement: Block picker
Picking a replacement SHALL offer every block that has an item, searchable by name. Blocks of the same kind as the original SHALL be listed first as suggestions. The original block itself SHALL be offered too, which resets the swap.

#### Scenario: Suggestions for stairs
- **WHEN** the player picks a replacement for oak stairs
- **THEN** other stairs such as spruce stairs and stone brick stairs are listed first, followed by all other blocks

#### Scenario: Search
- **WHEN** the player types `deepslate` in the picker
- **THEN** only blocks whose name contains `deepslate` are listed

### Requirement: Unknown swap targets
A saved swap whose target block the game does not know SHALL be ignored, so the original block shows. The materials screen SHALL mark it as missing and keep it until the player changes or resets it.

#### Scenario: Mod removed
- **WHEN** a placement swaps oak planks to a block from a mod that is no longer installed
- **THEN** the placement shows oak planks and the materials screen marks that swap as missing
