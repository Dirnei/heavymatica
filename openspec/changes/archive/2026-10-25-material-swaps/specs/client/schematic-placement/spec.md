# Spec Delta

## MODIFIED Requirements

### Requirement: A placement pins a version
A placement SHALL refer to exactly one schematic version (a cloud version by schematic, version number and content hash, or a local entry by content hash), together with a position, rotation (0, 90, 180 or 270), mirror (none, left-right or front-back), dimension and its block swaps.

#### Scenario: New version uploaded later
- **WHEN** a player has a placement of version 2 and later uploads version 3 of the same schematic
- **THEN** the placement still shows version 2

#### Scenario: Swaps stay with the placement
- **WHEN** two placements of the same version exist and one swaps oak stairs to spruce stairs
- **THEN** only that placement shows spruce stairs

### Requirement: Anchored placements persist
The client SHALL save anchored and locked placements per world (singleplayer) or server address (multiplayer) and restore them when the player joins again, including whether each is locked, its layer setting, layer window, in-hand flag and swaps. FOLLOWING placements SHALL NOT be saved. Placements SHALL stay private to the player's client. Placements saved before locking existed SHALL load as ANCHORED with the layer setting off. Placements saved before swaps existed SHALL load without swaps.

#### Scenario: Rejoin a server
- **WHEN** a player anchors two placements on `play.example.net`, disconnects and joins again
- **THEN** both placements are back, unselected, at the same positions, rotations and mirrors

#### Scenario: Different server
- **WHEN** the same player joins `other.example.net`
- **THEN** the placements from `play.example.net` are not shown

#### Scenario: Locked placement with layers
- **WHEN** a player locks a placement, sets its window to layers 4 to 6 with the in-hand filter on, and joins again
- **THEN** the placement is LOCKED with layers 4 to 6 and the in-hand filter on

#### Scenario: Swaps saved
- **WHEN** a player swaps oak stairs to spruce stairs on an anchored placement and joins again
- **THEN** the placement still shows spruce stairs
