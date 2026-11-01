# client/radial-menu Specification

## Purpose

Gathers the remote's one-shot placement actions in a radial menu ("wheel") on one key, showing only the actions the
selected placement's state allows.

## Requirements

### Requirement: Opening the wheel
The wheel key (default V, rebindable in the Heavymatica category) SHALL open the wheel around the screen centre only while a remote is in the main hand and no screen is open. The wheel SHALL NOT pause the game. It SHALL close when the remote leaves the main hand.

#### Scenario: Open with the remote
- **WHEN** a player holding the remote presses V
- **THEN** the wheel opens

#### Scenario: No remote
- **WHEN** a player holding a sword presses V
- **THEN** no wheel opens

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
- LOCKED: Unlock, In-hand filter, Layers, Materials and Library

#### Scenario: Locked placement
- **WHEN** the selected placement is LOCKED and the player opens the wheel
- **THEN** it shows Unlock, In-hand filter, Layers, Materials and Library, and no Lock, Remove or Material list

#### Scenario: Nothing selected
- **WHEN** no placement is selected and the player opens the wheel
- **THEN** it shows Materials greyed out and Library

#### Scenario: Anchored placement
- **WHEN** the selected placement is ANCHORED and the player opens the wheel
- **THEN** it shows Lock, Remove, Materials and Library

### Requirement: What the segments do
Library SHALL open the library. Cancel SHALL remove the following placement at once. Lock SHALL lock the anchored placement. In-hand filter SHALL toggle the locked placement's in-hand filter. Materials SHALL open the materials screen of the selected placement; greyed out, it SHALL do nothing and the wheel SHALL stay open. Remove, Unlock and Layers SHALL first ask, as their own requirements say.

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
Lock / unlock, Remove, In-hand filter, Materials and cycling the layer setting SHALL each be a key in the Heavymatica category with no default key. A bound key SHALL work only while a remote is held and no screen is open, in the same states as its segment. The cycle key SHALL step the layer setting from off to from-to to follow feet and back, and the other way with Shift held.

#### Scenario: No default
- **WHEN** a player opens the controls screen with default bindings
- **THEN** the Heavymatica category lists the remove, lock / unlock, in-hand filter, materials and cycle layer mode keys as not bound

#### Scenario: Bound in-hand key
- **WHEN** a player binds the in-hand filter key to I and presses I with a locked placement selected
- **THEN** its in-hand filter toggles

### Requirement: Shortage pointer to the wheel
When the overlay says that more items are short than it lists and the materials key is not bound, it SHALL name the wheel key and the Materials segment instead of a materials key.

#### Scenario: More short without a key
- **WHEN** the materials key is not bound and the selected locked placement is short of 5 items
- **THEN** the overlay's "more short" line names V and Materials
