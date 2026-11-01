# Spec Delta

## MODIFIED Requirements

### Requirement: Thumbnails show every visible block
An upload thumbnail SHALL show the same blocks a ghost of the schematic shows: block-entity blocks with their own model and texture, fluids, and blocks without geometry as their outline shape, with old schematics upgraded and known blocks resolved as for ghosts. Unknown blocks SHALL be left out of the thumbnail, whatever the setting for showing them.

#### Scenario: Bedroom
- **WHEN** a player uploads a schematic of a room with a bed, a chest and a small pool
- **THEN** the thumbnail shows the bed, the chest and the water

#### Scenario: Ghost and thumbnail agree
- **WHEN** a block is visible in a ghost of a schematic
- **THEN** it is also visible in that schematic's upload thumbnail, unless something in front of it covers it or it is an unknown block's placeholder

#### Scenario: Old schematic
- **WHEN** a player uploads a schematic from Minecraft 1.20.2 that contains `minecraft:grass`
- **THEN** the thumbnail shows short grass there

#### Scenario: Missing mod
- **WHEN** a player uploads a schematic with `create:shaft` while Create is not installed
- **THEN** the thumbnail shows no missing-texture cubes
