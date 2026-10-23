# Spec Delta

## MODIFIED Requirements

### Requirement: Mistakes are targeted as the planned block
When the line of sight hits a real block at a position of a locked placement that is wrong block, wrong orientation, too many, wrong water, needs finishing or not enough yet, and that position is in a visible layer, the target SHALL be that position with its planned block, and the block found there SHALL be part of the target.

#### Scenario: Dirt where planks are planned
- **WHEN** the player looks at dirt at a visible position where a locked placement expects oak planks
- **THEN** the target is that position with oak planks planned and dirt found

#### Scenario: Extra block
- **WHEN** the player looks at a tall grass plant where a locked placement has air
- **THEN** there is no ghost target

#### Scenario: One of three candles
- **WHEN** the player looks at 1 white candle at a visible position where a locked placement expects 3 white candles
- **THEN** the target is that position with 3 white candles planned and 1 found

#### Scenario: Picking more candles
- **WHEN** a player carrying white candles middle-clicks that position within reach
- **THEN** the white candles are in the main hand afterwards

### Requirement: Target state in the HUD
The HUD SHALL show the target's state on its own line, in the comparison colours: missing; wrong block in red, naming the found block; wrong orientation in orange, with the found block's orientation; too many in orange, with the found and the planned amount; wrong water in cyan, saying whether water is needed or must go; needs finishing in yellow, naming the step; not enough yet, with how many more are needed; and not compared yet, muted, while the position is unknown.

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
