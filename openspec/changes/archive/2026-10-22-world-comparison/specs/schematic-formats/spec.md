# Spec Delta

## MODIFIED Requirements

### Requirement: Multi-region Litematica files
A `.litematic` file with several regions SHALL be read as one model covering the bounding box of all regions. Where regions overlap, the region listed later in the file SHALL win. Positions outside every region SHALL be `minecraft:structure_void`, since no region says what belongs there; air saved inside a region SHALL stay air.

#### Scenario: Two regions
- **WHEN** a `.litematic` file has two non-overlapping regions
- **THEN** the model's size is their combined bounding box, each region's blocks sit at their file-relative positions, and positions outside both regions are `minecraft:structure_void`

#### Scenario: Air inside a region
- **WHEN** a region of a two-region `.litematic` file stores air at some of its positions
- **THEN** those positions are air in the model

#### Scenario: Gap does not change block counts
- **WHEN** the block counts of a two-region `.litematic` file are computed
- **THEN** they contain only the blocks of the regions and nothing for the gap
