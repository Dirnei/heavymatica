# Spec Delta

## Purpose

Turns the block states a schematic file stores into the game's blocks: old schematics are upgraded to the game's
version, known blocks always resolve, and blocks the game still doesn't know stay visible and can be shown or hidden.

## ADDED Requirements

### Requirement: Old schematics are upgraded
When a schematic records a Minecraft data version older than the game's, the client SHALL upgrade every block state of it to the game's version, the way the game upgrades old worlds, before using it for ghosts, comparison, targeting, material counts, the materials screen, thumbnails and the library details. A schematic with no data version, the game's version or a newer one SHALL be used as written.

#### Scenario: Renamed plant
- **WHEN** a schematic saved in Minecraft 1.20.2 contains `minecraft:grass`
- **THEN** its ghost shows short grass there

#### Scenario: Changed property values
- **WHEN** a schematic saved before walls had low and tall sides contains a cobblestone wall with `north=true`
- **THEN** its ghost shows the cobblestone wall with a side to the north, as the game would after upgrading a world

#### Scenario: No data version
- **WHEN** a schematic records no data version and contains `minecraft:grass`
- **THEN** that block is not upgraded and is unknown

### Requirement: The file stays as written
Upgrading SHALL NOT change the schematic file, its content hash, its block counts as the cloud reports them, uploads or exports. Swaps SHALL keep referring to the block ID as written in the file.

#### Scenario: Export after upgrade
- **WHEN** a player exports a schematic from 1.20.2 that contains `minecraft:grass`
- **THEN** the exported file is byte for byte the stored file and still says `minecraft:grass`

#### Scenario: Swap on an upgraded block
- **WHEN** a player swaps the short grass of a 1.20.2 schematic for fern
- **THEN** the swap is saved for `minecraft:grass` and still applies after the game is updated to a version that renames short grass again

### Requirement: Known blocks always resolve
A block state whose block ID the game knows SHALL resolve to that block. Properties the block doesn't have SHALL be dropped, and properties whose value the block doesn't accept SHALL take the block's default value; the other properties SHALL be kept.

#### Scenario: Unknown property value
- **WHEN** a schematic from no older version contains `minecraft:oak_stairs[facing=north,half=middle]`
- **THEN** the ghost shows oak stairs facing north with the default half

#### Scenario: Extra property
- **WHEN** a schematic contains `minecraft:stone[glowing=true]`
- **THEN** the ghost shows stone

### Requirement: Unknown blocks
A position SHALL be unknown when the block it shows, after upgrading and swaps, has a block ID the game doesn't know, such as a block from a mod that isn't installed or from a newer Minecraft version. A swap of an unknown block to a known block SHALL make its positions known.

#### Scenario: Missing mod
- **WHEN** a schematic contains `create:shaft` and Create is not installed
- **THEN** every position with `create:shaft` is unknown

#### Scenario: Swapped away
- **WHEN** the player swaps `create:shaft` for iron chain in that placement
- **THEN** those positions show iron chains and are no longer unknown

### Requirement: Unknown blocks show a placeholder
While unknown blocks are shown, every unknown position a placement draws SHALL show a full cube with the game's missing texture, at the ghost opacity, in every placement state. On a locked placement it SHALL follow the layer setting and the in-hand filter, which never matches an unknown block, like the other ghosts.

#### Scenario: Anchored placement with a missing mod
- **WHEN** an anchored placement contains 40 `create:shaft` blocks and Create is not installed
- **THEN** 40 purple-and-black missing-texture cubes show where the shafts are

#### Scenario: Outside the visible layers
- **WHEN** a locked placement shows layers 0 to 2 and an unknown block is on layer 5
- **THEN** no placeholder shows on layer 5

### Requirement: Showing or hiding unknown blocks
The client setting `ghost.showUnknownBlocks` SHALL decide whether unknown blocks show their placeholder, with a default of on. It SHALL apply to every placement at once and SHALL change only the drawing: counts, list rows and the overlay line SHALL stay.

#### Scenario: New install
- **WHEN** a player starts the game without a Heavymatica client config and places a schematic with unknown blocks
- **THEN** the unknown blocks show their placeholder

#### Scenario: Hidden
- **WHEN** `ghost.showUnknownBlocks` is off and two placements have unknown blocks
- **THEN** neither draws a placeholder, and the overlay still counts the unknown blocks of the selected one
