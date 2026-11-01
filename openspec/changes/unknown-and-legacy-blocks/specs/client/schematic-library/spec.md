# Spec Delta

## ADDED Requirements

### Requirement: Resolved names in the details
The block counts in the entry details SHALL name each block as the game resolves it for that version, upgraded to the game's version, and SHALL mark block IDs the game doesn't know as unknown. When the chosen version has unknown blocks, the details SHALL say how many block types are unknown.

#### Scenario: Old schematic
- **WHEN** a player opens the details of a version saved in Minecraft 1.20.2 that contains `minecraft:grass`
- **THEN** its block counts list short grass, not marked unknown

#### Scenario: Missing mod
- **WHEN** a player opens the details of a version with `create:shaft` and `create:cogwheel` while Create is not installed
- **THEN** both rows are marked unknown and the details say 2 block types are unknown
