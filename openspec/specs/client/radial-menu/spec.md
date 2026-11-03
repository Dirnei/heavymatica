# client/radial-menu Specification

## Purpose

Gathers the remote's one-shot placement actions in a radial menu ("wheel") on one key, showing only the actions the
selected placement's state allows.

## Requirements

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

### Requirement: Hold mode
With the client setting `wheel.mode` on HOLD (the default), the wheel SHALL stay open while the wheel key is held, the segment under the mouse direction SHALL be highlighted, and releasing the key SHALL run that segment. Releasing it with the mouse in the centre, or pressing Esc, SHALL close the wheel without running anything.

#### Scenario: Flick and release
- **WHEN** in HOLD mode the player holds V, moves the mouse towards Library and releases V
- **THEN** the wheel closes and the library opens

#### Scenario: Release in the centre
- **WHEN** in HOLD mode the player holds V and releases it without moving the mouse out of the centre
- **THEN** the wheel closes and nothing runs

### Requirement: Toggle mode
With `wheel.mode` on TOGGLE, pressing the wheel key SHALL open the wheel and leave it open, and clicking a segment SHALL run it. Pressing the wheel key again, pressing Esc or clicking the centre SHALL close it without running anything.

#### Scenario: Click a segment
- **WHEN** in TOGGLE mode the player taps V and clicks Lock with an anchored placement selected
- **THEN** the wheel closes and the placement is LOCKED

#### Scenario: Tap again
- **WHEN** in TOGGLE mode the wheel is open and the player taps V
- **THEN** the wheel closes and nothing runs

### Requirement: Camera and movement while open
While the wheel is open, moving the mouse SHALL only point at segments and SHALL NOT turn the camera. The movement keys (forward, back, left, right, jump, sneak and sprint) SHALL keep moving the player, also when they were already held as the wheel opened.

#### Scenario: Walking with the wheel open
- **WHEN** the player holds W and opens the wheel
- **THEN** the player keeps walking forward and the camera does not turn while the mouse moves

### Requirement: Segments follow the state
The wheel SHALL show these segments for the selected placement's state and no others:
- no placement selected: Materials, greyed out, and Library
- FOLLOWING: Cancel, Materials and Library
- ANCHORED: Lock, Remove, Materials and Library
- LOCKED: Unlock, Options, Layers, Materials and Library

#### Scenario: Locked placement
- **WHEN** the selected placement is LOCKED and the player opens the wheel
- **THEN** it shows Unlock, Options, Layers, Materials and Library, and no Lock, Remove, In-hand filter or Material list

#### Scenario: Nothing selected
- **WHEN** no placement is selected and the player opens the wheel
- **THEN** it shows Materials greyed out and Library

#### Scenario: Anchored placement
- **WHEN** the selected placement is ANCHORED and the player opens the wheel
- **THEN** it shows Lock, Remove, Materials and Library

### Requirement: What the segments do
Library SHALL open the library. Cancel SHALL remove the following placement at once. Lock SHALL lock the anchored placement. Options SHALL open the Options sub-wheel. Materials SHALL open the materials screen of the selected placement; greyed out, it SHALL do nothing and the wheel SHALL stay open. Remove, Unlock and Layers SHALL first ask, as their own requirements say.

#### Scenario: Cancel a following placement
- **WHEN** the selected placement is FOLLOWING and the player picks Cancel
- **THEN** the placement is gone

#### Scenario: Material list
- **WHEN** the selected placement is LOCKED and the player picks Materials
- **THEN** the materials screen opens with the material list's counts

#### Scenario: Greyed out without a selection
- **WHEN** no placement is selected and the player opens the wheel and clicks Materials
- **THEN** no screen opens and the wheel stays open

### Requirement: Bring back segment
While a finished placement can be brought back, the wheel SHALL show Bring back first, in every state. Bring back SHALL bring the most recently finished placement back at once and close the wheel; when the time to bring it back runs out while the wheel is open, picking it SHALL do nothing.

#### Scenario: Just finished
- **WHEN** a placement finished 3 seconds ago, nothing is selected, and the player opens the wheel
- **THEN** it shows Bring back, Materials greyed out and Library

#### Scenario: Bring back from the wheel
- **WHEN** a placement finished 5 seconds ago and the player picks Bring back
- **THEN** the placement is back, LOCKED and selected, and the wheel is closed

### Requirement: Confirming remove and unlock
Picking Remove or Unlock SHALL NOT act at once. The wheel SHALL instead show Yes and Cancel with a label naming the action and the placement, such as `Remove "Castle Gate"?`. Yes SHALL run the action. Cancel, Esc or the centre SHALL close the wheel and change nothing. In HOLD mode, releasing the wheel key on Remove or Unlock SHALL leave the wheel open on Yes and Cancel, and a click SHALL answer.

#### Scenario: Remove with Yes
- **WHEN** the selected placement `Castle Gate` is ANCHORED, the player picks Remove and then Yes
- **THEN** the placement is gone and no placement is selected

#### Scenario: Remove cancelled
- **WHEN** the player picks Remove and then Cancel
- **THEN** the placement is still there

#### Scenario: One flick never removes
- **WHEN** in HOLD mode the player holds V, points at Remove and releases V
- **THEN** the wheel stays open showing `Remove "Castle Gate"?` with Yes and Cancel, and the placement is still there

#### Scenario: Unlock with Yes
- **WHEN** the selected placement is LOCKED and the player picks Unlock and then Yes
- **THEN** the placement is ANCHORED

### Requirement: Layers sub-wheel
Picking Layers SHALL swap the wheel's segments to Off, From-to and Follow feet, with the current layer setting marked. Picking one SHALL set the selected locked placement's layer setting to it and close the wheel. Esc or the centre SHALL close the wheel and change nothing. In HOLD mode, releasing the wheel key on Layers SHALL leave the sub-wheel open, and a click SHALL pick.

#### Scenario: Pick follow feet
- **WHEN** the selected locked placement's layer setting is off and the player picks Layers, then Follow feet
- **THEN** its layer setting is follow feet

#### Scenario: Pick from-to
- **WHEN** the player picks Layers, then From-to, on a locked placement whose window was never set
- **THEN** its layer setting is from-to with a one-layer window at the player's feet

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

### Requirement: Shortage pointer to the wheel
When the overlay says that more items are short than it lists and the materials key is not bound, it SHALL name the wheel key and the Materials segment instead of a materials key.

#### Scenario: More short without a key
- **WHEN** the materials key is not bound and the selected locked placement is short of 5 items
- **THEN** the overlay's "more short" line names V and Materials

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

### Requirement: Capture wheel
In capture mode, the wheel SHALL show only Capture, Swap corner, Reset and Cancel. Capture and Swap corner SHALL be greyed out until both corners are set, and Reset until a corner is set; a greyed-out segment SHALL do nothing and keep the wheel open. Capture SHALL capture the box, Swap corner SHALL make the other corner active, Reset SHALL clear both corners and stay in capture mode, and Cancel SHALL end capture mode.

#### Scenario: Before any corner
- **WHEN** capture mode is on with no corners and the player opens the wheel
- **THEN** it shows Capture, Swap corner and Reset greyed out, and Cancel

#### Scenario: Reset
- **WHEN** both corners are set and the player picks Reset
- **THEN** capture mode is on with no corners

#### Scenario: Cancel
- **WHEN** the player picks Cancel in capture mode
- **THEN** capture mode is off and the remote's keys work on placements again

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

### Requirement: Paste segment
While paste is available, the wheel SHALL show a Paste segment after Materials when the selected placement is LOCKED.
It SHALL NOT show Paste while the selected placement is FOLLOWING or ANCHORED. It SHALL be greyed out while a paste or undo is running; a greyed-out Paste SHALL do nothing and keep the
wheel open. Picking it SHALL swap the wheel's segments to the paste sub-wheel. In HOLD mode, releasing the wheel key on
Paste SHALL leave the sub-wheel open, and a click SHALL pick.

#### Scenario: Locked placement in creative
- **WHEN** the player is in creative single player with a locked placement selected and opens the wheel
- **THEN** it shows Paste after Materials

#### Scenario: Survival
- **WHEN** the player is in survival single player with a locked placement selected and opens the wheel
- **THEN** it shows no Paste

#### Scenario: Following placement
- **WHEN** the player is in creative single player with a following placement selected and opens the wheel
- **THEN** it shows no Paste

#### Scenario: Anchored placement
- **WHEN** the player is in creative single player with an anchored placement selected and opens the wheel
- **THEN** it shows no Paste

### Requirement: Paste sub-wheel
The paste sub-wheel SHALL show Replace all, Only into air and Skip air. Picking one SHALL start a paste of the selected
placement in that mode and close the wheel. Esc or the centre SHALL close the wheel and paste nothing.

#### Scenario: Paste only into air
- **WHEN** the player picks Paste, then Only into air
- **THEN** a paste in that mode starts and the wheel closes

#### Scenario: Close without pasting
- **WHEN** the player picks Paste and then presses Esc
- **THEN** the wheel closes and nothing is pasted

### Requirement: Undo paste segment
While paste is available and a paste can be undone, the wheel SHALL show an Undo paste segment as its last segment, in
every placement state, also with no placement selected. It SHALL be greyed out while a paste or undo is running. Picking
it SHALL undo the last paste and close the wheel.

#### Scenario: After a paste
- **WHEN** the player has pasted and opens the wheel with nothing selected
- **THEN** it shows Undo paste as its last segment

#### Scenario: Nothing to undo
- **WHEN** the player has not pasted since joining the world and opens the wheel
- **THEN** it shows no Undo paste

### Requirement: Mistakes segment
The Options sub-wheel SHALL show a Mistakes segment after its other segments, marked while the mistake pointer is not
off. Picking it SHALL swap the wheel's segments to the Mistakes sub-wheel. In HOLD mode, releasing the wheel key on
Mistakes SHALL leave the sub-wheel open, and a click SHALL pick.

#### Scenario: Options shows Mistakes
- **WHEN** the selected placement is LOCKED and the player picks Options
- **THEN** the sub-wheel shows Mistakes as its last segment

#### Scenario: Marked while on
- **WHEN** the mistake pointer is set to mistakes and the player picks Options
- **THEN** Mistakes is marked

### Requirement: Mistakes sub-wheel
The Mistakes sub-wheel SHALL show Off, Mistakes, and Mistakes and missing, with the current mistake pointer setting
marked. Picking one SHALL set the mistake pointer to it and close the wheel. Esc or the centre SHALL close the wheel and
change nothing.

#### Scenario: Turn on with missing blocks
- **WHEN** the mistake pointer is off and the player picks Options, then Mistakes, then Mistakes and missing
- **THEN** the mistake pointer is set to mistakes and missing and the wheel is closed

#### Scenario: Turn off
- **WHEN** the mistake pointer is set to mistakes and the player picks Options, then Mistakes, then Off
- **THEN** the mistake pointer is off

#### Scenario: Close without a change
- **WHEN** the player opens the Mistakes sub-wheel and presses Esc
- **THEN** the wheel closes and the mistake pointer setting is unchanged

### Requirement: Segment labels and centre hint
Every segment SHALL show its icon with its name under it on the ring. While a segment is hovered, the centre of the wheel SHALL show that segment's name and a one-line hint of what picking it does. While no segment is hovered, the centre SHALL name the selected placement and its state, or the question when the wheel asks to confirm. A segment that is greyed out SHALL say in its hint why it does nothing.

#### Scenario: Hover shows the hint
- **WHEN** the wheel is open on an anchored placement and the mouse points at Lock
- **THEN** the centre shows "Lock" and a hint saying the placement is fixed in place for building

#### Scenario: Nothing hovered
- **WHEN** the wheel opens on an anchored placement named "Castle Gate" with the mouse in the centre
- **THEN** the centre shows "Castle Gate" and its state, Anchored

#### Scenario: Greyed-out segment explains itself
- **WHEN** in capture mode with no corner set the mouse points at Capture
- **THEN** the centre's hint says both corners must be set first

### Requirement: Current option mark
On a page that offers settings, such as Layers, Options, Mistakes or Opacity, the option that is currently in effect or switched on SHALL be marked on its segment. The mark SHALL stay visible while the segment is hovered.

#### Scenario: Current layer setting
- **WHEN** a locked placement uses From-to and the player opens the Layers sub-wheel
- **THEN** From-to carries the mark and Off and Follow feet do not

#### Scenario: Hovering a marked segment
- **WHEN** the mouse points at the marked From-to segment
- **THEN** the segment is highlighted and still shows the mark

### Requirement: Wheel sounds
With the client setting `wheel.sounds` on (the default), the wheel SHALL play a quiet tick when the hovered segment changes to another segment, and a click when a segment runs or opens a sub-page or a confirm page. Moving into the centre, a greyed-out segment being picked, and closing without running anything SHALL NOT play the click. With `wheel.sounds` off, the wheel SHALL play no sounds. The sounds SHALL play only for the player who has the wheel open.

#### Scenario: Sweeping across segments
- **WHEN** with sounds on the mouse moves from Lock over Remove to Materials
- **THEN** one tick plays as Remove becomes hovered and one as Materials does

#### Scenario: Picking plays a click
- **WHEN** with sounds on the player picks Library
- **THEN** a click plays and the library opens

#### Scenario: Sounds off
- **WHEN** `wheel.sounds` is off and the player sweeps across segments and picks one
- **THEN** no wheel sound plays

### Requirement: Wheel animations setting
With the client setting `wheel.animations` on (the default), the wheel SHALL animate opening, hovering, page changes and closing, and every animation SHALL finish within 250 ms. With it off, the wheel SHALL show each state at once in its final layout. Animations SHALL NOT delay input: a segment SHALL be pointable and pickable from the moment the wheel opens, and a release or click SHALL act on the segment under the mouse direction, as without animations.

#### Scenario: Quick flick while opening
- **WHEN** in HOLD mode the player presses V, flicks towards Library and releases V within 100 ms
- **THEN** the library opens, even though the opening animation had not finished

#### Scenario: Animations off
- **WHEN** `wheel.animations` is off and the player opens the wheel and moves between segments
- **THEN** the wheel appears fully drawn at once and the highlight moves without transition

#### Scenario: Settings survive a restart
- **WHEN** the player sets `wheel.animations` and `wheel.sounds` to false and restarts the game
- **THEN** the wheel opens without animations and without sounds

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
