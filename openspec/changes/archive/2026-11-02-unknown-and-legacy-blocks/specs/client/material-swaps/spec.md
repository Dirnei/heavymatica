# Spec Delta

## ADDED Requirements

### Requirement: Unknown blocks in the materials screen
The materials screen SHALL list a block type the game doesn't know like any other, with its ID as the schematic shows it, its count and a mark that it is unknown. Its row SHALL pick a replacement as any row does; the block picker SHALL then offer no same-kind suggestions. Once swapped to a known block, the row SHALL show what it shows as without the mark.

#### Scenario: Unknown row
- **WHEN** a placement has 40 `create:shaft` positions while Create is not installed and the player opens the materials screen
- **THEN** a row lists `create:shaft` with 40 positions, marked as unknown

#### Scenario: Replace a missing modded block
- **WHEN** the player picks iron chain as the replacement in that row
- **THEN** the row shows iron chain without the unknown mark, and the placement shows iron chains where the shafts were

### Requirement: Upgraded blocks in the materials screen
A row of a block that the schematic's upgrade renames SHALL show what it shows as under its current name, while its swap stays saved for the block ID as written in the file.

#### Scenario: Old grass
- **WHEN** a schematic from Minecraft 1.20.2 contains `minecraft:grass` and the player opens the materials screen
- **THEN** its row shows short grass and is not marked unknown
