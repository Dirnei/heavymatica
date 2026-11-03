## MODIFIED Requirements

### Requirement: Target state in the HUD
The HUD SHALL show the target's state on its own line, in the comparison colours: missing; wrong block in red, naming the found block; wrong orientation in orange, with the found block's orientation, or, when only the open state of a door, trapdoor or fence gate differs, saying to open it or to close it; too many in orange, with the found and the planned amount; extra in magenta, for a two-item block on a planned empty base, naming the content to remove; wrong water in cyan, saying whether water is needed or must go; needs finishing in yellow, naming the step; not enough yet, with how many more are needed, or, for a two-item block on its empty base, naming the content to add, or, for a single chest where a half of a double chest is planned, saying that the other half is still needed; and not compared yet, muted, while the position is unknown.

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
- **THEN** the HUD shows a magenta line saying the dead bush must go

#### Scenario: Gate to open
- **WHEN** the target expects an open oak fence gate and the world has the same gate closed there
- **THEN** the HUD shows an orange line saying to open it

#### Scenario: Door to close
- **WHEN** the target expects a closed iron door and the world has the same door open there
- **THEN** the HUD shows an orange line saying to close it

#### Scenario: Chest waiting for its other half
- **WHEN** the target expects the left half of a double chest and the world has a single chest in the planned facing there
- **THEN** the HUD shows a line saying the other half of the chest is still needed

#### Scenario: Chest that should stay single
- **WHEN** the target expects a single chest and the world has the left half of a double chest there
- **THEN** the HUD shows an orange line naming the found chest type
