# Spec Delta

## Purpose

Lets the player mark an area of the world with the remote and save it as a schematic in the library, so builds made
in the game can be placed again, uploaded or exported without another tool.

## ADDED Requirements

### Requirement: Capture mode
Picking Capture on the wheel SHALL start capture mode with no corners set. While capture mode is on and a remote is held, right-click, the arrow keys and numpad + / - SHALL work on the capture box, the remote's other placement keys SHALL do nothing, and no placement SHALL change. Placements SHALL keep being drawn. Capture mode SHALL end when the player captures, cancels, leaves the world or changes dimension.

#### Scenario: Start capturing
- **WHEN** an anchored placement is selected and the player picks Capture on the wheel
- **THEN** capture mode is on with no corners, and pressing the arrow keys does not move the placement

#### Scenario: Leaving the world
- **WHEN** the player leaves the world while capturing
- **THEN** capture mode is off and its corners are gone when they join again

### Requirement: Setting corners
In capture mode, a right-click on a block within the remote's reach SHALL set corner 1 to that block when no corner is set, and corner 2 when only corner 1 is set. The corner just set SHALL become the active corner. With both corners set, a right-click on a block SHALL move the active corner to it. A right-click that hits no block SHALL do nothing.

#### Scenario: Two clicks
- **WHEN** in capture mode the player right-clicks the block at 10, 64, 10 and then the block at 21, 64, 18
- **THEN** corner 1 is 10, 64, 10, corner 2 is 21, 64, 18, and corner 2 is active

#### Scenario: Moving the active corner by clicking
- **WHEN** both corners are set, corner 1 is active and the player right-clicks the block at 5, 70, 5
- **THEN** corner 1 is 5, 70, 5 and corner 2 is unchanged

### Requirement: Moving the active corner with keys
With at least one corner set, the arrow keys SHALL move the active corner one block in the direction the player faces, snapped to the nearest horizontal direction, as they move a placement. Numpad + and - SHALL move it one block up or down. With Shift held, each press SHALL move it 10 blocks. Holding a key SHALL repeat the move. A corner SHALL NOT move outside the world's build height.

#### Scenario: Raising the top without climbing
- **WHEN** both corners are set at y 64, corner 2 is active and the player presses numpad + 8 times
- **THEN** corner 2 is at y 72 and the box is 9 blocks high

#### Scenario: Ten at a time
- **WHEN** the player faces north and presses Shift and the up arrow
- **THEN** the active corner moves 10 blocks north

#### Scenario: Build height
- **WHEN** the active corner is at the top of the build height and the player presses numpad +
- **THEN** the corner stays where it is

### Requirement: Swapping the active corner
With both corners set, scrolling the mouse wheel in either direction while Shift is held SHALL make the other corner active and SHALL NOT change the selected hotbar slot. The swap corner key in the Heavymatica category, unbound by default, and the wheel's Swap corner SHALL do the same. Scrolling without Shift SHALL change the hotbar slot as usual.

#### Scenario: Shift and scroll
- **WHEN** both corners are set, corner 2 is active, and the player holds Shift and scrolls down
- **THEN** corner 1 is active and the hotbar slot is unchanged

#### Scenario: One corner only
- **WHEN** only corner 1 is set and the player holds Shift and scrolls
- **THEN** corner 1 stays active and the hotbar slot is unchanged

### Requirement: Showing the box
In capture mode, each set corner SHALL be marked in the world and the active corner SHALL be marked more brightly. With both corners set, the box they span, both corners included, SHALL be drawn as an outline. The overlay SHALL show the box's width, height and length, the active corner's position, and the capture keys.

#### Scenario: Box size
- **WHEN** corner 1 is 10, 64, 10 and corner 2 is 21, 72, 18
- **THEN** the outline spans both corners and the overlay shows 12 x 9 x 9

### Requirement: Box limits
The overlay SHALL show the size in a warning colour when a dimension exceeds 1024 blocks or the volume exceeds 16,777,216 blocks. Capturing such a box SHALL be refused with that reason, and the box SHALL stay as it is so the player can shrink it.

#### Scenario: Too wide
- **WHEN** the box is 1030 blocks wide and the player picks Capture
- **THEN** nothing is captured, the player is told the box is too large, and the corners are unchanged

### Requirement: Unloaded chunks refuse the capture
Capturing SHALL be refused while any part of the box lies in a chunk the client has not loaded. The player SHALL be told so, and the box SHALL stay as it is.

#### Scenario: Box beyond render distance
- **WHEN** part of the box lies in a chunk the client has not loaded and the player picks Capture
- **THEN** nothing is captured and the player is told part of the box is not loaded

### Requirement: Naming a capture
Picking Capture with both corners set and the box within the limits SHALL ask for a name, prefilled with `Capture` followed by the local date and time, such as `Capture 2026-10-31 14:02`. Confirming SHALL start the capture. Cancelling SHALL leave capture mode on with the box unchanged. When the player is on a server, the prompt SHALL warn that container contents and other data the server does not share may be missing.

#### Scenario: Prefilled name
- **WHEN** the player picks Capture at 14:02 on 31 October 2026
- **THEN** the prompt shows the name `Capture 2026-10-31 14:02`

#### Scenario: Cancel the prompt
- **WHEN** the player cancels the name prompt
- **THEN** nothing is captured and capture mode is on with the same corners

#### Scenario: Server warning
- **WHEN** the player captures on a multiplayer server
- **THEN** the prompt warns that some block data may be missing

### Requirement: What a capture contains
A capture SHALL save every position of the box, both corners included, with the block state the client sees there, air as `minecraft:air`. It SHALL save the block entity data the client has for each block entity in the box, at its position in the box. It SHALL NOT save entities. Placement ghosts SHALL NOT affect what is saved.

#### Scenario: Air is kept
- **WHEN** the player captures a box with 40 stone blocks and 60 air positions
- **THEN** the entry's file has 40 stone and 60 `minecraft:air` positions

#### Scenario: Sign text
- **WHEN** the box contains a sign with text
- **THEN** the captured sign has that text when the entry is placed

#### Scenario: Ghosts ignored
- **WHEN** a placement ghost shows a block where the world has air
- **THEN** the capture saves air at that position

### Requirement: Captures become local entries
A finished capture SHALL be saved as a local-only library entry with the chosen name, in `.litematic` format, with the Minecraft data version of the game it was captured in. The entry SHALL be usable signed out and SHALL be uploadable like any local entry. Capture mode SHALL end and the player SHALL be told the entry was saved.

#### Scenario: Saved to the library
- **WHEN** the player confirms the name `Starter Hut` and the capture finishes
- **THEN** the library has a local entry `Starter Hut` with the box's dimensions, and capture mode is off

#### Scenario: Upload afterwards
- **WHEN** a signed-in player uploads the captured entry `Starter Hut`
- **THEN** it becomes a cloud schematic `Starter Hut` with a thumbnail

### Requirement: Capturing never freezes the game
A capture SHALL NOT block the game noticeably, whatever the box size. While it runs, the overlay SHALL show its progress. When the box reaches into a chunk that gets unloaded before the capture has read it, the capture SHALL fail with that reason, nothing SHALL be saved, and capture mode SHALL stay on with the box unchanged.

#### Scenario: Large capture
- **WHEN** the player captures a box of 4,000,000 blocks
- **THEN** the game keeps rendering and accepting input, and the overlay shows the progress until the entry is saved

#### Scenario: Chunk unloaded during capture
- **WHEN** a chunk in the box is unloaded before the capture has read it
- **THEN** nothing is saved, the player is told why, and the box is unchanged
