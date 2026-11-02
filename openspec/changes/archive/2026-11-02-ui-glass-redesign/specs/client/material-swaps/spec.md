## MODIFIED Requirements

### Requirement: Materials screen
While a remote is held, the wheel's Materials segment or the bound materials key SHALL open one screen for the selected placement in every state. It SHALL list every block type of the placement's schematic except air and structure void with how many positions use it and what it shows as. Each row SHALL name the block it shows as, in the accent colour when it is swapped, with the original block in a hover text. Clicking the name SHALL pick a replacement, and a swapped row SHALL offer an undo button that resets the swap, both at once. When no placement is selected, the materials key SHALL tell the player to select one.

#### Scenario: Open the screen
- **WHEN** a player holding the remote picks Materials on the wheel with the anchored `Castle Gate` selected
- **THEN** a screen lists `Castle Gate`'s block types with their counts and what each shows as, most used first, without needed, had or short counts

#### Scenario: No selection
- **WHEN** a player has bound the materials key to N and presses N while holding the remote with no placement selected
- **THEN** no screen opens and a message says to select a placement first

#### Scenario: Bound key on an anchored placement
- **WHEN** a player has bound the materials key to N and presses N while holding the remote with an anchored placement selected
- **THEN** the materials screen opens

#### Scenario: Swapped row
- **WHEN** a placement swaps oak planks to spruce planks and the player hovers that row's name
- **THEN** the row shows spruce planks in the accent colour with an undo button, and the hover text says it was swapped from oak planks

#### Scenario: Click to swap
- **WHEN** the player clicks the name of the cobblestone row
- **THEN** the block picker opens for cobblestone

#### Scenario: Undo a swap
- **WHEN** the player clicks the undo button of the spruce planks row
- **THEN** the row shows oak planks again and the placement shows oak planks

### Requirement: Needs on a locked placement
While the placement shown in the materials screen is LOCKED, each row SHALL also show, for the item that places the block the row shows as, how many its material list still needs, how many the player has and how many are short, with a shortage above 0 in red. Rows whose blocks show as the same item SHALL show the same counts. While the placement is not locked, the screen SHALL show none of these counts.

The counts SHALL be drawn as one bar per row over the item's planned positions in the visible layers: built (planned minus needed) in green, carried (had, up to what is needed) in blue and short in orange, with the short count beside the bar. A bar for the whole placement SHALL sum these over every item and show the totals.

#### Scenario: Locked placement
- **WHEN** the selected locked placement swaps stone bricks to deepslate bricks, still needs 800 deepslate bricks and 300 oak planks, and the player has 512 oak planks and no deepslate bricks
- **THEN** the stone bricks row shows deepslate bricks with 800 needed, 0 had and 800 short in red, listed above the oak planks row with 300 needed, 512 had and 0 short

#### Scenario: Shared item
- **WHEN** a locked placement swaps oak stairs to spruce stairs and also contains spruce stairs
- **THEN** both rows show the same needed, had and short counts for spruce stairs

#### Scenario: Picking up items while it is open
- **WHEN** the screen is open on a locked placement and the player picks up 12 oak planks
- **THEN** the oak planks row shows 12 more had and up to 12 less short

#### Scenario: Row bar
- **WHEN** the visible layers plan 96 oak planks, 58 are still needed and the player has 22
- **THEN** the oak planks bar shows 38 built in green, 22 carried in blue and 36 short in orange, with 36 beside it

#### Scenario: More carried than needed
- **WHEN** 4 torches are still needed and the player has 40
- **THEN** the torch bar's blue part covers only the 4 needed and the row shows no shortage

### Requirement: Materials screen order and can't carry
While the placement is LOCKED, the materials screen's rows SHALL be sorted by most short first, then most needed, then most used, and the blocks no item places SHALL follow in a separate "can't carry" part with how many positions still need them. While it is not locked, the rows SHALL be sorted by most used first and there SHALL be no "can't carry" part. Unknown block types SHALL follow last, in their own "unknown blocks" part, in every state.

#### Scenario: Water
- **WHEN** a locked placement still needs 12 water source blocks
- **THEN** the screen lists water 12 under "can't carry"

#### Scenario: Unknown blocks last
- **WHEN** an anchored placement has oak planks, cobblestone and 40 `create:shaft` while Create is not installed
- **THEN** oak planks and cobblestone are listed first and `create:shaft` follows under "unknown blocks"

### Requirement: Block picker
Picking a replacement SHALL offer every block that has an item, searchable by name. Blocks of the same kind as the original SHALL be listed first as suggestions. The original block itself SHALL be offered too, which resets the swap.

The picker SHALL open as a dialog over the materials screen, with the search box focused. Picking a block SHALL apply it and close the dialog at once, and Esc SHALL close it without a change.

#### Scenario: Suggestions for stairs
- **WHEN** the player picks a replacement for oak stairs
- **THEN** other stairs such as spruce stairs and stone brick stairs are listed first, followed by all other blocks

#### Scenario: Search
- **WHEN** the player types `deepslate` in the picker
- **THEN** only blocks whose name contains `deepslate` are listed

#### Scenario: Keep original
- **WHEN** a row is swapped to spruce planks and the player chooses Keep original in the picker
- **THEN** the swap is reset and the dialog closes

#### Scenario: Esc
- **WHEN** the player presses Esc in the picker
- **THEN** the dialog closes, the materials screen shows again and no swap changed

### Requirement: Unknown blocks in the materials screen
The materials screen SHALL list a block type the game doesn't know like any other, with its ID as the schematic shows it, its count and a mark that it is unknown. Its row SHALL pick a replacement as any row does; the block picker SHALL then offer no same-kind suggestions. Once swapped to a known block, the row SHALL show what it shows as without the mark.

#### Scenario: Unknown row
- **WHEN** a placement has 40 `create:shaft` positions while Create is not installed and the player opens the materials screen
- **THEN** a row lists `create:shaft` with 40 positions, marked as unknown

#### Scenario: Replace a missing modded block
- **WHEN** the player picks iron chain as the replacement in that row
- **THEN** the row shows iron chain without the unknown mark, and the placement shows iron chains where the shafts were

## ADDED Requirements

### Requirement: Carried counts in the block picker
Every block in the picker SHALL show how many of its item the player has, counted as the material list counts what a player has. When the player has at least as many as the row needs, the picker SHALL say it is enough. The row needs its count still needed while the placement is locked, and its full position count otherwise. The picker SHALL also say whether the chosen block is enough or how many would be short.

#### Scenario: Choose by what is carried
- **WHEN** a locked placement still needs 58 for the oak planks row and the player has 128 birch planks, 22 spruce planks and no jungle planks
- **THEN** birch planks shows 128 and enough, spruce planks shows 22, and jungle planks shows that none are carried

#### Scenario: Not locked
- **WHEN** an anchored placement has 96 oak planks and the player has 64 birch planks
- **THEN** birch planks shows 64 without enough
