# Spec Delta

## ADDED Requirements

### Requirement: Thumbnails show every visible block
An upload thumbnail SHALL show the same blocks a ghost of the schematic shows: block-entity blocks with their own model and texture, fluids, and blocks without geometry as their outline shape.

#### Scenario: Bedroom
- **WHEN** a player uploads a schematic of a room with a bed, a chest and a small pool
- **THEN** the thumbnail shows the bed, the chest and the water

#### Scenario: Ghost and thumbnail agree
- **WHEN** a block is visible in a ghost of a schematic
- **THEN** it is also visible in that schematic's upload thumbnail, unless something in front of it covers it
