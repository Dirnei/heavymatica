# schematic-formats Specification

## Purpose

Defines which schematic files Heavymatica accepts, how each one maps to a single canonical model, and the validation rules that the game client and the cloud service both apply, so both always agree on what a file contains.

## Requirements

### Requirement: Supported formats
Heavymatica SHALL accept Litematica `.litematic` files, Sponge `.schem` files of format versions 2 and 3, and vanilla structure `.nbt` files. It SHALL detect the format from the file content, not from the file name.

#### Scenario: Wrong extension
- **WHEN** a valid Sponge schematic is supplied under the name `castle.nbt`
- **THEN** it is read as a Sponge schematic

#### Scenario: Unsupported file
- **WHEN** a file is supplied that is none of the supported formats
- **THEN** it is rejected as an unsupported format

### Requirement: Canonical model
Every accepted file SHALL be read into one canonical model consisting of: a size (width, height, length), a block state at every position, block entity data per position, and the Minecraft data version if the file records one.

#### Scenario: Same structure, different formats
- **WHEN** the same structure is saved as `.litematic`, `.schem` and `.nbt` and each file is read
- **THEN** all three produce the same size, the same block state at every position, and the same block entity data

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

### Requirement: Vanilla structure palettes
For a structure `.nbt` file that has several palettes, the first palette SHALL be used.

#### Scenario: Several palettes
- **WHEN** a structure file with three palettes is read
- **THEN** the blocks come from the first palette only

### Requirement: Unknown and modded blocks are kept
Block states from any namespace, including blocks the reader does not recognize, SHALL be kept as written, with their properties.

#### Scenario: Modded block
- **WHEN** a file contains `servus_foundation:utility_duct[north=true]`
- **THEN** the model contains that block state unchanged at its position

### Requirement: Entities are not part of the model
Entities stored in a file, such as item frames or armor stands, SHALL be left out of the canonical model, and the reader SHALL report how many were left out.

#### Scenario: File with entities
- **WHEN** a file containing 3 entities is read
- **THEN** the model has no entities and the result reports 3 entities skipped

### Requirement: Missing data version
A file that records no Minecraft data version SHALL still be accepted, and its data version SHALL be reported as unknown.

#### Scenario: Old file
- **WHEN** a file without a data version is read
- **THEN** it is accepted and its data version is reported as unknown

### Requirement: Size limits
A file SHALL be rejected when it is larger than 16 MiB, when it decompresses to more than 128 MiB, when any dimension exceeds 1024 blocks, or when its volume exceeds 16,777,216 blocks. Reading SHALL stop as soon as a limit is crossed.

#### Scenario: Oversized dimension
- **WHEN** a file declares a width of 2000 blocks
- **THEN** it is rejected as too large without its block data being read

#### Scenario: Decompression bomb
- **WHEN** a 1 MiB file would decompress to 4 GiB
- **THEN** reading stops at 128 MiB of output and the file is rejected

### Requirement: Structural validation
A file SHALL be rejected when its block data does not match its declared size, when it refers to a palette entry that does not exist, or when its NBT is malformed or truncated.

#### Scenario: Palette index out of range
- **WHEN** a file's block data refers to palette index 40 while the palette has 12 entries
- **THEN** the file is rejected as invalid

### Requirement: Block counts
Block counts per block type SHALL count every position except air (`minecraft:air`, `minecraft:cave_air`, `minecraft:void_air`) and `minecraft:structure_void`, and SHALL group block states by block ID, ignoring properties.

#### Scenario: Stairs facing different ways
- **WHEN** a model contains 4 oak stairs facing north and 6 facing east, plus air
- **THEN** the counts report 10 `minecraft:oak_stairs` and nothing for air

### Requirement: Client and service agree
The game client and the cloud service SHALL produce the same accept or reject result, the same model and the same block counts for every file in the shared conformance set of sample files.

#### Scenario: Conformance set
- **WHEN** every file in the conformance set is read by both the client and the service
- **THEN** both give identical results for each file
