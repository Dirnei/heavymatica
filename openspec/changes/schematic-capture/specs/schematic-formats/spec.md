# Spec Delta

## ADDED Requirements

### Requirement: Litematica writer
Heavymatica SHALL write a canonical model as a `.litematic` file of Litematica format version 7 with a single region covering the whole model, its blocks, its block entities at their positions, no entities, and the model's Minecraft data version. The file SHALL record the schematic's name, the author's player name and the time it was created. Writing the same model with the same name, author and time SHALL give the same bytes.

#### Scenario: Single region
- **WHEN** a 12 x 9 x 9 model is written
- **THEN** the file holds one region of size 12 x 9 x 9 and records an enclosing size of 12 x 9 x 9

#### Scenario: Same input, same bytes
- **WHEN** the same model is written twice with the same name, author and time
- **THEN** both files are byte for byte identical

### Requirement: Written files read back the same
A file written by Heavymatica SHALL be accepted by the game client and the cloud service and read into a model with the same size, the same block state at every position, block entities at the same positions with the same IDs, and the same data version. The shared conformance set SHALL include files written by Heavymatica.

#### Scenario: Round trip
- **WHEN** a model with stairs, air, a modded block and a chest is written and the file is read again
- **THEN** the read model has the same size, palette digest, block entity positions and IDs, and data version

#### Scenario: Read by the cloud
- **WHEN** the cloud service reads the conformance file written by Heavymatica
- **THEN** it produces the result recorded for that file, as the game client does
