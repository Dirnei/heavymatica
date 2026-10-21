# Spec Delta

## ADDED Requirements

### Requirement: Locking
While a remote is held and the selected placement is ANCHORED, the lock key SHALL put it in LOCKED. A locked placement SHALL stay where it is and SHALL NOT be moved, raised, lowered, rotated, mirrored, released or removed.

#### Scenario: Lock
- **WHEN** the selected placement is ANCHORED and the player presses L
- **THEN** it is LOCKED

#### Scenario: Locked placement ignores move keys
- **WHEN** the selected placement is LOCKED and the player presses R, M or sneak + right-click
- **THEN** its position, rotation, mirror and state do not change

#### Scenario: Following placement cannot lock
- **WHEN** the selected placement is FOLLOWING and the player presses L
- **THEN** it stays FOLLOWING

### Requirement: Unlocking needs a second press
While a remote is held and the selected placement is LOCKED, the lock key SHALL unlock it, back to ANCHORED, only when pressed a second time within 3 seconds. After the first press, the overlay SHALL say to press again to unlock.

#### Scenario: Unlock
- **WHEN** the player presses L twice within 3 seconds with a locked placement selected
- **THEN** the placement is ANCHORED

#### Scenario: Single press
- **WHEN** the player presses L once on a locked placement and waits 5 seconds
- **THEN** the placement is still LOCKED

## MODIFIED Requirements

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
The remove key SHALL remove the selected placement only when pressed a second time within 3 seconds. After the first press, the overlay SHALL say to press again to remove. A FOLLOWING placement SHALL be removed on the first press, since it was never anchored. A LOCKED placement SHALL NOT be removed; it has to be unlocked first.

#### Scenario: Remove anchored placement
- **WHEN** the player presses Delete twice within 3 seconds with an anchored placement selected
- **THEN** the placement is gone and no placement is selected

#### Scenario: Single press
- **WHEN** the player presses Delete once and waits 5 seconds
- **THEN** the placement is still there

#### Scenario: Locked placement
- **WHEN** the player presses Delete twice with a locked placement selected
- **THEN** the placement is still there and still LOCKED

### Requirement: Anchored placements persist
The client SHALL save anchored and locked placements per world (singleplayer) or server address (multiplayer) and restore them when the player joins again, including whether each is locked, its layer setting, its layer window and its in-hand flag. FOLLOWING placements SHALL NOT be saved. Placements SHALL stay private to the player's client. Placements saved before locking existed SHALL load as ANCHORED with the layer setting off.

#### Scenario: Rejoin a server
- **WHEN** a player anchors two placements on `play.example.net`, disconnects and joins again
- **THEN** both placements are back, unselected, at the same positions, rotations and mirrors

#### Scenario: Different server
- **WHEN** the same player joins `other.example.net`
- **THEN** the placements from `play.example.net` are not shown

#### Scenario: Locked placement with layers
- **WHEN** a player locks a placement, sets its window to layers 4 to 6 with the in-hand filter on, and joins again
- **THEN** the placement is LOCKED with layers 4 to 6 and the in-hand filter on
