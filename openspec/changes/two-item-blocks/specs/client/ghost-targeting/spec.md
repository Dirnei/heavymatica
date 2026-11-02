## MODIFIED Requirements

### Requirement: Which item is picked
The candidate items for a planned block SHALL be, in this order: its own item, the item that places it, then the items the equivalents table accepts for it, in the order the material list takes them. For a two-item block the candidates SHALL be its base item while the position is missing, wrong block or not compared yet, and its content item while the position is not enough yet. The first candidate the player carries in the hotbar or main inventory SHALL be picked.

#### Scenario: Grass from dirt
- **WHEN** the player middle-clicks a grass block ghost, carrying dirt and no grass blocks
- **THEN** dirt is picked

#### Scenario: Own item first
- **WHEN** the player middle-clicks a grass block ghost, carrying both grass blocks and dirt
- **THEN** grass blocks are picked

#### Scenario: Swapped block
- **WHEN** a placement swaps oak planks to spruce planks and the player middle-clicks one of them, carrying both
- **THEN** spruce planks are picked

#### Scenario: Pot first
- **WHEN** the player middle-clicks a missing potted dead bush ghost, carrying flower pots and dead bushes
- **THEN** a flower pot is picked

#### Scenario: Plant once the pot stands
- **WHEN** the player middle-clicks a potted dead bush ghost over an empty flower pot, carrying flower pots and dead bushes
- **THEN** a dead bush is picked

#### Scenario: No plant carried
- **WHEN** a survival player without dead bushes middle-clicks a potted dead bush ghost over an empty flower pot
- **THEN** the action bar says `You have no Dead Bush` and the selected slot stays the same

### Requirement: Target state in the HUD
The HUD SHALL show the target's state on its own line, in the comparison colours: missing; wrong block in red, naming the found block; wrong orientation in orange, with the found block's orientation; too many in orange, with the found and the planned amount, or, for a two-item block on a planned empty base, naming the content to remove; wrong water in cyan, saying whether water is needed or must go; needs finishing in yellow, naming the step; not enough yet, with how many more are needed, or, for a two-item block on its empty base, naming the content to add; and not compared yet, muted, while the position is unknown.

#### Scenario: Wrong block
- **WHEN** the target expects oak planks and the world has dirt there
- **THEN** the HUD shows a red line naming dirt

#### Scenario: Needs a shovel
- **WHEN** the target expects a dirt path and the world has a grass block there
- **THEN** the HUD shows a yellow line saying it needs a shovel

#### Scenario: Just locked
- **WHEN** the player has just locked a placement and targets a position that has not been compared yet
- **THEN** the HUD shows the planned block and that it is not compared yet

#### Scenario: Two more candles
- **WHEN** the target expects 3 white candles and the world has 1 there
- **THEN** the HUD shows that 2 more are needed, 1 of 3

#### Scenario: Too many candles
- **WHEN** the target expects 3 white candles and the world has 4 there
- **THEN** the HUD shows an orange line saying there are 4 instead of 3

#### Scenario: Half a double slab
- **WHEN** the target expects a double stone slab and the world has a bottom stone slab there
- **THEN** the HUD shows that 1 more is needed, 1 of 2

#### Scenario: Pot without its plant
- **WHEN** the target expects a potted dead bush and the world has an empty flower pot there
- **THEN** the HUD shows a line saying a dead bush is still needed, in the same colour as the line for missing candles

#### Scenario: Plant in a planned empty pot
- **WHEN** the target expects an empty flower pot and the world has a potted dead bush there
- **THEN** the HUD shows an orange line saying the dead bush must go
