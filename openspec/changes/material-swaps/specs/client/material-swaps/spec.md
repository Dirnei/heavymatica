# Spec Delta

## ADDED Requirements

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
While a remote is held and a placement is selected, the wheel's Materials segment, or the materials key when bound, SHALL open a screen listing every block type of the selected placement's schematic except air and structure void, with how many positions use it and its current swap, most used first. Each row SHALL let the player pick a replacement or reset the swap. Changes SHALL apply at once. When no placement is selected, the materials key SHALL tell the player to select one.

#### Scenario: Open the screen
- **WHEN** a player holding the remote picks Materials on the wheel with `Castle Gate` selected
- **THEN** a screen lists `Castle Gate`'s block types with their counts, most used first

#### Scenario: No selection
- **WHEN** a player has bound the materials key to N and presses N while holding the remote with no placement selected
- **THEN** no screen opens and a message says to select a placement first

#### Scenario: Bound key
- **WHEN** a player has bound the materials key to N and presses N while holding the remote with a placement selected
- **THEN** the materials screen opens

### Requirement: Materials on the wheel
In addition to the segments the wheel shows for each state, it SHALL show a Materials segment whenever a placement is selected, whether FOLLOWING, ANCHORED or LOCKED. With no placement selected it SHALL show Materials greyed out, and picking it SHALL do nothing. The materials key SHALL have no default key.

#### Scenario: Anchored placement
- **WHEN** the selected placement is ANCHORED and the player opens the wheel
- **THEN** it shows Lock, Remove, Materials and Library

#### Scenario: Greyed out without a selection
- **WHEN** no placement is selected and the player opens the wheel and clicks Materials
- **THEN** Materials shows greyed out, no screen opens and the wheel stays open

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
