# client/schematic-placement Specification

## Purpose

Lets a player put schematic versions into the world as ghosts, select one of many, move, rotate, mirror, anchor, release and remove it, and find anchored placements again when they come back to the world or server.

## Requirements

### Requirement: A placement pins a version
A placement SHALL refer to exactly one schematic version (a cloud version by schematic, version number and content hash, or a local entry by content hash), together with a position, rotation (0, 90, 180 or 270), mirror (none, left-right or front-back), dimension and its block swaps.

#### Scenario: New version uploaded later
- **WHEN** a player has a placement of version 2 and later uploads version 3 of the same schematic
- **THEN** the placement still shows version 2

#### Scenario: Swaps stay with the placement
- **WHEN** two placements of the same version exist and one swaps oak stairs to spruce stairs
- **THEN** only that placement shows spruce stairs

### Requirement: Ghost rendering
The client SHALL render every placement in the player's current dimension as a translucent ghost of the schematic's blocks, rotated and mirrored as the placement says, with block states such as stair facing turned to match. Blocks the game does not know SHALL be left out.

#### Scenario: Rotated stairs
- **WHEN** a placement containing north-facing stairs is rotated 90 degrees clockwise
- **THEN** the ghost shows those stairs facing east

#### Scenario: Other dimension
- **WHEN** a placement was anchored in the Overworld and the player is in the Nether
- **THEN** the ghost is not rendered

### Requirement: Ghost rendering budget
The client SHALL limit ghost rendering to a configurable number of blocks across all ghosts. When the limit is reached, ghosts that do not fit SHALL be drawn as box outlines only, so the game stays playable.

#### Scenario: Huge schematic
- **WHEN** a placement's visible blocks exceed the rendering budget
- **THEN** that ghost is drawn as a box outline and the game keeps its frame rate

### Requirement: Selected placement is highlighted
At most one placement SHALL be selected at a time. The selected placement's bounding box SHALL be outlined in a colour that marks its state, distinct from unselected placements.

#### Scenario: Two placements
- **WHEN** a world has placements `Gate` and `Tower` and `Tower` is selected
- **THEN** only `Tower` has the selection outline

### Requirement: Starting a placement
Starting a placement from the library SHALL create a new placement of the chosen version, select it and put it in FOLLOWING.

#### Scenario: Place from the library
- **WHEN** a player chooses Place on version 3 of `Castle Gate`
- **THEN** the library closes and a ghost of version 3, selected and FOLLOWING, appears at the crosshair

### Requirement: Following the crosshair
A FOLLOWING placement SHALL move with the player's crosshair while a remote is held: its bottom face sits on top of the targeted block face, centred on that block, up to 64 blocks away. When nothing is targeted within that range, it SHALL stay where it was.

#### Scenario: Looking at the ground
- **WHEN** a FOLLOWING placement is selected and the player looks at the top of a grass block at 10, 63, 20
- **THEN** the ghost's bottom layer is at y 64, centred on x 10, z 20

#### Scenario: Looking at the sky
- **WHEN** the player looks at the sky with a FOLLOWING placement
- **THEN** the ghost stays where it last was

### Requirement: Anchoring
Right-clicking with the remote while the selected placement is FOLLOWING SHALL anchor it where it is, putting it in ANCHORED.

#### Scenario: Anchor
- **WHEN** a player right-clicks with the remote while the selected placement is FOLLOWING
- **THEN** the placement is ANCHORED at its current position and no longer follows the crosshair

### Requirement: Releasing
Sneak + right-click with the remote while the selected placement is ANCHORED SHALL release it back to FOLLOWING, wherever the player is looking.

#### Scenario: Release
- **WHEN** a player sneaks and right-clicks with the remote while the selected placement is ANCHORED
- **THEN** the placement is FOLLOWING again and moves with the crosshair

### Requirement: Selecting by pointing
Right-clicking with the remote (without sneaking) when no placement is FOLLOWING SHALL select the placement whose bounding box the line of sight enters first, up to 64 blocks away. A box the camera is inside counts as entered first; between several, the smallest wins. Right-clicking the already selected placement SHALL change nothing.

#### Scenario: Select another ghost
- **WHEN** `Tower` is selected and the player right-clicks while looking at `Gate`
- **THEN** `Gate` becomes selected and `Tower` stays where it is

#### Scenario: Right-click the selected ghost
- **WHEN** the player right-clicks while looking at the selected, anchored placement
- **THEN** nothing changes

### Requirement: Right-click on no ghost opens the library
Right-clicking with the remote (without sneaking) when no placement is FOLLOWING and the line of sight hits no ghost SHALL open the library.

#### Scenario: Open the library with the remote
- **WHEN** a player holding the remote right-clicks at a spot with no ghost
- **THEN** the library screen opens

### Requirement: Moving relative to the camera
While a remote is held and the selected placement is ANCHORED, the arrow keys SHALL move it one block in the direction the player faces, snapped to the nearest horizontal direction: up away from the player, down toward them, left and right sideways. With Shift held, each press SHALL move 10 blocks. Holding a key SHALL repeat the move.

#### Scenario: Facing east
- **WHEN** the player faces roughly east and presses the up arrow
- **THEN** the selected placement moves one block east

#### Scenario: Faster move
- **WHEN** the player holds Shift and presses the left arrow while facing north
- **THEN** the selected placement moves 10 blocks west

### Requirement: Moving up and down
While a remote is held and the selected placement is ANCHORED, the up and down keys SHALL move it one block up or down (10 with Shift). While it is FOLLOWING, they SHALL raise or lower it relative to the crosshair target instead, and that offset SHALL be kept until the placement is anchored.

#### Scenario: Anchored up
- **WHEN** the selected placement is ANCHORED at y 64 and the player presses numpad +
- **THEN** it is at y 65

#### Scenario: Following offset
- **WHEN** the selected placement is FOLLOWING and the player presses numpad - twice
- **THEN** the ghost sits two blocks below the crosshair target as it keeps following

### Requirement: Rotating in place
The rotate key SHALL turn the selected placement 90 degrees clockwise, or counter-clockwise with Shift held, while it is FOLLOWING or ANCHORED. The ghost SHALL turn around the centre of its footprint, so it stays roughly where it was. A LOCKED placement SHALL NOT rotate.

#### Scenario: Rotate an anchored placement
- **WHEN** an anchored 5 x 3 x 9 placement is rotated clockwise
- **THEN** its footprint becomes 9 x 5 with the same centre (rounded down) and it stays anchored

#### Scenario: Locked placement does not rotate
- **WHEN** the selected placement is LOCKED and the player presses the rotate key
- **THEN** its rotation does not change

### Requirement: Mirroring
The mirror key SHALL cycle the selected placement's mirror through none, left-right and front-back, while it is FOLLOWING or ANCHORED, without moving its footprint. A LOCKED placement SHALL NOT change its mirror.

#### Scenario: Cycle mirror
- **WHEN** the selected placement has no mirror and the player presses the mirror key twice
- **THEN** its mirror is front-back

### Requirement: Removing needs a second press
An anchored placement SHALL only be removed after a confirmation: on the wheel, Remove asks Yes / Cancel; a bound remove key SHALL remove it only when pressed a second time within 3 seconds, and after the first press the overlay SHALL say to press again to remove. A FOLLOWING placement SHALL be removed at once (the wheel's Cancel, or the first press of the remove key), since it was never anchored. A LOCKED placement SHALL NOT be removed; it has to be unlocked first.

#### Scenario: Remove anchored placement
- **WHEN** the player has bound the remove key to Delete and presses Delete twice within 3 seconds with an anchored placement selected
- **THEN** the placement is gone and no placement is selected

#### Scenario: Single press
- **WHEN** the player presses the bound remove key once and waits 5 seconds
- **THEN** the placement is still there

#### Scenario: Locked placement
- **WHEN** the player presses the bound remove key twice with a locked placement selected
- **THEN** the placement is still there and still LOCKED

#### Scenario: Remove from the wheel
- **WHEN** the player picks Remove on the wheel with an anchored placement selected and then Yes
- **THEN** the placement is gone and no placement is selected

### Requirement: Following placements need the remote
A FOLLOWING placement SHALL be shown and follow the crosshair only while a remote is in the main hand. If no remote is held when a placement is started from the library, the client SHALL tell the player to hold a remote, naming the configured item.

#### Scenario: Place without a remote
- **WHEN** a player holding a sword chooses Place in the library
- **THEN** a message says to hold the remote item (for example "Hold a Stick to place") and the ghost appears once a remote is held

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

### Requirement: Unavailable versions
When a saved placement's version can't be loaded (offline and not cached, or deleted from the library), the client SHALL draw its bounding box outline only, keep the placement, and the overlay SHALL say the version is unavailable when it is selected. The client SHALL retry loading when the cloud becomes reachable again.

#### Scenario: Offline rejoin
- **WHEN** a player rejoins while the cloud is unreachable and a placement's version is not cached
- **THEN** that placement shows as an outline and the overlay says it is unavailable when selected

### Requirement: Locking
While a remote is held and the selected placement is ANCHORED, the wheel's Lock segment, or the lock / unlock key when bound, SHALL put it in LOCKED. A locked placement SHALL stay where it is and SHALL NOT be moved, raised, lowered, rotated, mirrored, released or removed.

#### Scenario: Lock
- **WHEN** the selected placement is ANCHORED and the player picks Lock on the wheel
- **THEN** it is LOCKED

#### Scenario: Locked placement ignores move keys
- **WHEN** the selected placement is LOCKED and the player presses R, M or sneak + right-click
- **THEN** its position, rotation, mirror and state do not change

#### Scenario: Following placement cannot lock
- **WHEN** the selected placement is FOLLOWING and the player presses the bound lock / unlock key
- **THEN** it stays FOLLOWING

### Requirement: Unlocking needs a second press
While a remote is held and the selected placement is LOCKED, it SHALL only be unlocked, back to ANCHORED, after a confirmation: on the wheel, Unlock asks Yes / Cancel; a bound lock / unlock key SHALL unlock it only when pressed a second time within 3 seconds, and after the first press the overlay SHALL say to press again to unlock.

#### Scenario: Unlock
- **WHEN** the player has bound the lock / unlock key to L and presses L twice within 3 seconds with a locked placement selected
- **THEN** the placement is ANCHORED

#### Scenario: Single press
- **WHEN** the player presses the bound lock / unlock key once on a locked placement and waits 5 seconds
- **THEN** the placement is still LOCKED

#### Scenario: Unlock cancelled on the wheel
- **WHEN** the player picks Unlock on the wheel with a locked placement selected and then Cancel
- **THEN** the placement is still LOCKED
