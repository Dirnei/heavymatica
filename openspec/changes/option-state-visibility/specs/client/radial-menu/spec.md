## MODIFIED Requirements

### Requirement: Opening the wheel
The wheel key (default V, rebindable in the Heavymatica category) SHALL open the wheel around the screen centre while no screen is open and either a remote is in the main hand, or a placement is selected and capture mode is off. The wheel SHALL NOT pause the game. It SHALL close when neither a remote is in the main hand nor a placement is selected, and it SHALL close in capture mode when the remote leaves the main hand.

#### Scenario: Open with the remote
- **WHEN** a player holding the remote presses V
- **THEN** the wheel opens

#### Scenario: No remote
- **WHEN** no placement is selected and a player holding a sword presses V
- **THEN** no wheel opens

#### Scenario: No remote, placement selected
- **WHEN** a placement is selected, capture mode is off and a player holding oak planks presses V
- **THEN** the wheel opens with the segments for that placement's state

#### Scenario: Capture mode without the remote
- **WHEN** capture mode is on and a player holding a sword presses V
- **THEN** no wheel opens

#### Scenario: Remote put away while open
- **WHEN** a placement is selected, the wheel is open, and the remote leaves the main hand
- **THEN** the wheel stays open

### Requirement: Capture segment
Outside capture mode and while a remote is in the main hand, the wheel SHALL show Capture after its other segments when no placement is selected or the selected placement is ANCHORED or LOCKED. It SHALL NOT show Capture while the selected placement is FOLLOWING, or while the wheel was opened without the remote. Picking Capture SHALL close the wheel and start capture mode. Capture mode, its wheel and its keys SHALL need the remote.

#### Scenario: Nothing selected
- **WHEN** no placement is selected and the player opens the wheel
- **THEN** it shows Capture as the last segment

#### Scenario: Following placement
- **WHEN** the selected placement is FOLLOWING and the player opens the wheel
- **THEN** it does not show Capture

#### Scenario: Without the remote
- **WHEN** the selected placement is ANCHORED and the player opens the wheel while holding oak planks
- **THEN** it does not show Capture

### Requirement: Wheel actions as keys
Lock / unlock, Remove, In-hand filter, Materials and cycling the layer setting SHALL each be a key in the Heavymatica category with no default key. A bound key SHALL work while no screen is open and either a remote is held, or a placement is selected and capture mode is off, in the same states as its segment. Without the remote, the lock / unlock key SHALL lock an anchored placement but SHALL NOT unlock a locked one. The cycle key SHALL step the layer setting from off to from-to to follow feet and back, and the other way with Shift held.

#### Scenario: No default
- **WHEN** a player opens the controls screen with default bindings
- **THEN** the Heavymatica category lists the remove, lock / unlock, in-hand filter, materials and cycle layer mode keys as not bound

#### Scenario: Bound in-hand key
- **WHEN** a player binds the in-hand filter key to I and presses I with a locked placement selected
- **THEN** its in-hand filter toggles

#### Scenario: Bound cycle key without the remote
- **WHEN** a player binds the cycle layer mode key to L, holds oak planks and presses L with a locked placement selected
- **THEN** its layer setting steps to the next one

#### Scenario: Bound lock key without the remote
- **WHEN** a player binds the lock / unlock key to K, holds oak planks and presses K with an anchored placement selected
- **THEN** the placement is LOCKED

#### Scenario: No unlock without the remote
- **WHEN** a player binds the lock / unlock key to K, holds oak planks and presses K with a locked placement selected
- **THEN** the placement stays LOCKED and the action bar says to hold the remote to unlock

#### Scenario: Nothing selected
- **WHEN** a player binds the materials key to K, holds a sword and presses K with no placement selected
- **THEN** nothing happens

### Requirement: Unknown blocks segment
When the selected placement has unknown blocks, the wheel SHALL also show an Unknown blocks segment in every placement state, labelled with whether unknown blocks are shown or hidden. Picking it SHALL switch between shown and hidden, write the choice to `ghost.showUnknownBlocks` in the client config file, save the file and close the wheel. Without unknown blocks, the segment SHALL NOT show.

The segment SHALL be in the Options sub-wheel, not on the main ring.

#### Scenario: Hide from the wheel
- **WHEN** the selected anchored placement has unknown blocks that are shown and the player picks Unknown blocks
- **THEN** the wheel closes, the placeholders disappear from every placement, and the client config holds `ghost.showUnknownBlocks = false`

#### Scenario: Show again
- **WHEN** unknown blocks are hidden and the player picks Unknown blocks on a placement that has some
- **THEN** the placeholders show again on every placement

#### Scenario: Kept after a restart
- **WHEN** the player hides unknown blocks and restarts the game
- **THEN** unknown blocks are still hidden

#### Scenario: No unknown blocks
- **WHEN** the selected placement's blocks are all known
- **THEN** the wheel shows no Unknown blocks segment

#### Scenario: Nothing selected
- **WHEN** no placement is selected
- **THEN** the wheel shows no Unknown blocks segment

#### Scenario: Anchored placement with unknown blocks
- **WHEN** the selected anchored placement has unknown blocks and the player opens the wheel
- **THEN** the main ring shows Options after Opacity and no Unknown blocks segment, and picking Options shows Unknown blocks and Pin overlay

#### Scenario: Anchored placement without unknown blocks
- **WHEN** the selected anchored placement's blocks are all known and the player picks Options
- **THEN** the sub-wheel shows no Unknown blocks segment

### Requirement: Options sub-wheel
Whenever a placement is selected, the main ring SHALL show an Options segment: for a LOCKED placement where it was before, and for a FOLLOWING or ANCHORED placement after Opacity. With no placement selected there SHALL be no Options segment.

Picking Options SHALL swap the wheel's segments to the options for the selected placement's state, each marked while it is on. For a LOCKED placement these SHALL be In-hand filter, Easy Place, Placement restriction, then Unknown blocks when the placement has unknown blocks, then Pin overlay, then Mistakes. For a FOLLOWING or ANCHORED placement they SHALL be Unknown blocks, when the placement has unknown blocks, then Pin overlay. Picking a toggle SHALL toggle it and close the wheel. Esc or the centre SHALL close the wheel and change nothing. In HOLD mode, releasing the wheel key on Options SHALL leave the sub-wheel open, and a click SHALL pick.

#### Scenario: Turn Easy Place on
- **WHEN** Easy Place is off and the player picks Options, then Easy Place
- **THEN** Easy Place is on, the wheel is closed and the action bar says Easy Place is on

#### Scenario: Toggle the in-hand filter
- **WHEN** the selected locked placement's in-hand filter is off and the player picks Options, then In-hand filter
- **THEN** its in-hand filter is on

#### Scenario: Marks show the current settings
- **WHEN** placement restriction is on, Easy Place is off, the in-hand filter is off, the overlay is not pinned, and the player picks Options
- **THEN** only Placement restriction is marked

#### Scenario: Close without a change
- **WHEN** the player picks Options and then presses Esc
- **THEN** the wheel closes and no setting changes

#### Scenario: Locked placement with unknown blocks
- **WHEN** the selected locked placement has unknown blocks and the player picks Options
- **THEN** the sub-wheel shows In-hand filter, Easy Place, Placement restriction, Unknown blocks, Pin overlay and Mistakes, in that order

#### Scenario: Anchored placement without unknown blocks
- **WHEN** the selected anchored placement's blocks are all known and the player opens the wheel
- **THEN** the main ring shows Options after Opacity, and picking Options shows Pin overlay alone

#### Scenario: Nothing selected
- **WHEN** no placement is selected and the player opens the wheel
- **THEN** the main ring shows no Options segment

## ADDED Requirements

### Requirement: Pin overlay segment
The Options sub-wheel's Pin overlay segment SHALL be marked while the `overlay.pinned` client setting is on. Picking it SHALL switch the setting, save the client config file, close the wheel and say the new state in the action bar ("Overlay pinned" or "Overlay unpinned"). It SHALL have its own icon.

#### Scenario: Pin from the wheel
- **WHEN** the overlay is not pinned and the player picks Options, then Pin overlay
- **THEN** the wheel closes, the action bar says "Overlay pinned" and the client config holds `overlay.pinned = true`

#### Scenario: Unpin from the wheel
- **WHEN** the overlay is pinned and the player picks Options, then Pin overlay
- **THEN** the action bar says "Overlay unpinned" and the overlay hides again once the remote leaves the hand

### Requirement: Toggles that are on are tinted
On the Options sub-wheel, a segment that switches something on or off SHALL fill its glass with a green tint while that thing is on, as well as carrying the current option mark, hovered or not. These segments are In-hand filter, Easy Place, Placement restriction, Unknown blocks (while unknown blocks are shown), Pin overlay, and Mistakes (while the mistake pointer is not off). Segments that pick one of several values, such as on the Layers, Opacity, Paste and Mistakes sub-wheels, SHALL keep only the mark.

#### Scenario: Easy Place on
- **WHEN** Easy Place is on and the player picks Options
- **THEN** the Easy Place segment is tinted green and Placement restriction, which is off, is plain glass

#### Scenario: Hovered while on
- **WHEN** the mouse points at the tinted Easy Place segment
- **THEN** it shows the hover highlight and stays tinted green

#### Scenario: Layer picker
- **WHEN** the selected locked placement uses From-to and the player opens the Layers sub-wheel
- **THEN** From-to carries the mark and is not tinted

### Requirement: Unlock needs the remote
When the wheel is open without a remote in the main hand, the Unlock segment SHALL be greyed out and its hint SHALL say to hold the remote to unlock. Picking it SHALL do nothing and keep the wheel open. Every other segment SHALL work as with the remote.

#### Scenario: Unlock without the remote
- **WHEN** the selected placement is LOCKED, the player holds oak planks, opens the wheel and points at Unlock
- **THEN** Unlock is greyed out and the centre's hint says to hold the remote to unlock

#### Scenario: Lock without the remote
- **WHEN** the selected placement is ANCHORED, the player holds oak planks and picks Lock on the wheel
- **THEN** the placement is LOCKED

#### Scenario: Options without the remote
- **WHEN** the selected placement is LOCKED, the player holds oak planks and picks Options, then Easy Place
- **THEN** Easy Place toggles
