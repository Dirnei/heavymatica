# client/creative-paste Specification

## Purpose
Lets a player in creative single player put a placement into the world at once, as its ghost shows it, and take the
last paste back.

## Requirements

### Requirement: When paste is available
Paste and undo paste SHALL be available only while the player is in creative mode in a single-player world whose server
runs in the player's own game, including when it is opened to LAN. They SHALL NOT be available on other servers, as a
LAN guest, or in survival, adventure or spectator mode.

#### Scenario: Creative single player
- **WHEN** the player is in creative mode in their own single-player world
- **THEN** paste is available

#### Scenario: Dedicated server
- **WHEN** the player is in creative mode on a dedicated server
- **THEN** paste is not available

#### Scenario: Survival
- **WHEN** the player is in survival mode in their own single-player world
- **THEN** paste is not available

### Requirement: What a paste places
A paste SHALL place the selected LOCKED placement as its ghost shows it: every position of the
placement's box with the block the ghost shows there after rotation, mirror and material swaps, including positions in
hidden layers. Block-entity data in the schematic SHALL be placed with its block, at the pasted position. Blocks the game
doesn't know SHALL be skipped and leave the world unchanged there. Structure voids SHALL leave the world unchanged.
Entities SHALL NOT be pasted.

#### Scenario: Rotated with a swap
- **WHEN** the placement is rotated 90 degrees and swaps oak planks to spruce planks, and the player pastes it
- **THEN** the world shows the rotated build with spruce planks where the schematic has oak planks

#### Scenario: Chest contents
- **WHEN** the schematic has a chest holding 5 diamonds and the player pastes it
- **THEN** the pasted chest holds 5 diamonds

#### Scenario: Unknown block
- **WHEN** the schematic has a block from a mod that is not installed and the player pastes it
- **THEN** the world at that position is unchanged

### Requirement: Paste modes
Each paste SHALL use one of three modes. Replace all SHALL set every position to the planned block, air included, so
the box matches the plan afterwards. Only into air SHALL place planned blocks only where the world has air or a
replaceable block such as grass or water, and change nothing else. Skip air SHALL place every planned block, replacing
what is there, and leave the world unchanged where the plan has air.

#### Scenario: Replace all clears terrain
- **WHEN** the plan has air where the world has dirt and the player pastes with Replace all
- **THEN** that position is air afterwards

#### Scenario: Only into air keeps blocks
- **WHEN** the plan has oak planks where the world has stone and the player pastes with Only into air
- **THEN** the stone stays

#### Scenario: Skip air keeps terrain
- **WHEN** the plan has air where the world has dirt and oak planks where the world has stone, and the player pastes with Skip air
- **THEN** the dirt stays and the stone becomes oak planks

### Requirement: Paste places blocks as planned
A paste SHALL place each block in the planned state without neighbour updates, so that blocks keep the state the plan
gives them while the paste runs: sand and gravel don't fall, water and lava don't flow, and redstone keeps its planned
power and connections. Afterwards the world SHALL behave normally.

#### Scenario: Floating sand
- **WHEN** the plan has sand above air and the player pastes it
- **THEN** the sand stands where planned right after the paste

### Requirement: Paste never freezes the game
A paste SHALL run over as many ticks as it needs, within a per-tick budget, and the overlay SHALL show its progress. It
SHALL load chunks it needs. Leaving the world SHALL stop a running paste; what it placed so far stays and can be undone
until then. Only one paste or undo SHALL run at a time.

#### Scenario: Large paste
- **WHEN** the player pastes a placement of 500,000 positions
- **THEN** the game keeps running at playable frame rates and the overlay shows the paste's progress until it is done

#### Scenario: Second paste while one runs
- **WHEN** a paste is running
- **THEN** Paste and Undo paste are not available until it finishes

### Requirement: Undo the last paste
The client SHALL remember what the last paste changed: the previous block and block-entity data of every position it
changed. Undo paste SHALL restore them, in the same paced way and without neighbour updates, and then forget the paste.
Only the last paste SHALL be undoable; a new paste replaces what is remembered. Leaving the world SHALL forget it.

#### Scenario: Undo a paste
- **WHEN** the player pastes with Replace all over a hill and then picks Undo paste
- **THEN** the hill is back as it was, including any chest contents the paste replaced

#### Scenario: Only the last one
- **WHEN** the player pastes twice and picks Undo paste
- **THEN** only the second paste is undone and Undo paste is no longer available

#### Scenario: After leaving the world
- **WHEN** the player pastes, leaves the world and joins it again
- **THEN** Undo paste is not available

### Requirement: Undo brings back the placement the paste finished
When a paste causes a locked placement to finish and be removed, Undo paste SHALL, once the blocks are restored, put
that placement back as it was when it finished (entry and version, position, rotation, mirror, swaps, locked state,
layers and in-hand flag), selected, and SHALL no longer offer it for bring back. A finish counts as caused by the paste
only while the paste runs or shortly after, and only the first one. Undo SHALL NOT add it while a placement with its ID
is there again.

#### Scenario: Undo restores a finished placement
- **WHEN** the player pastes a locked placement with Replace all, the placement finishes and is removed, and the player picks Undo paste
- **THEN** the world is back as it was and the placement is back, locked, at the same position with the same swaps and selected
- **AND** Bring back is no longer offered for it

#### Scenario: Already brought back
- **WHEN** the paste finishes the placement, the player brings it back and then picks Undo paste
- **THEN** the world is restored and there is still exactly one copy of the placement

#### Scenario: Anchored paste
- **WHEN** the player pastes an anchored placement and picks Undo paste
- **THEN** only the blocks are restored and the placements stay as they are
