## MODIFIED Requirements

### Requirement: Placement keys only while the remote is held
Heavymatica's placement keys SHALL act only while a remote is in the main hand and no screen is open, with two exceptions. The layer keys on a LOCKED selected placement also act without the remote, as `client/placement-layers` describes. The wheel key and the wheel action keys also act without the remote while a placement is selected and capture mode is off, as `client/radial-menu` describes. Right-click with the remote and the move, rotate and mirror keys SHALL still need the remote.

#### Scenario: Remote not held
- **WHEN** a player holding a sword presses the move key with an anchored placement selected
- **THEN** no placement changes

#### Scenario: Layer key without the remote
- **WHEN** a player holding a sword presses the up arrow with a locked placement in from-to selected
- **THEN** its layer window moves up one layer

#### Scenario: Rotate without the remote
- **WHEN** a player holding a sword presses R with any placement selected
- **THEN** no placement changes

#### Scenario: Wheel without the remote
- **WHEN** a player holding a sword presses V with an anchored placement selected
- **THEN** the wheel opens

### Requirement: Placement overlay
While a remote is in the main hand and no screen is open, the client SHALL show an overlay on the configured side of the screen (default left) with the selected placement's name, version, a state badge (FOLLOWING, ANCHORED or LOCKED), position, rotation and mirror, or "No placement selected" when none is. For a LOCKED placement it SHALL also show the layer setting with its layers and the placement's height, whether the in-hand filter is on, and the shortage lines of its material list.

#### Scenario: Anchored placement
- **WHEN** a player holds the remote and the selected placement `Castle Gate` version 3 is anchored at 128, 64, -40 with rotation 90 and no mirror
- **THEN** the overlay shows `Castle Gate`, `v3`, an ANCHORED badge, `128, 64, -40`, `90` and mirror off

#### Scenario: Remote put away
- **WHEN** the overlay is not pinned and the player switches from the remote to another item
- **THEN** the overlay disappears

#### Scenario: Locked placement with layers
- **WHEN** the selected 12-layer placement is LOCKED with layers 4 to 6 and the in-hand filter on
- **THEN** the overlay shows a LOCKED badge, `Layers 4-6 of 12` and that the in-hand filter is on

#### Scenario: Shortages
- **WHEN** the selected locked placement is short 12 oak planks
- **THEN** the overlay shows oak planks with 12 short

## ADDED Requirements

### Requirement: Pinned overlay
The client SHALL offer an `overlay.pinned` setting, off by default. While it is on, a placement is selected, no screen is open and the HUD is not hidden, the overlay SHALL also show without the remote in the main hand. It SHALL have the same content as with the remote, except key hints. Without the remote, the only hints SHALL be the layer-key hints of a LOCKED placement: moving and resizing the window, the follow offset, and cycling the layer setting when that key is bound. With no placement selected, a pinned overlay SHALL NOT show without the remote. The compact overlay setting SHALL apply to the pinned overlay too.

#### Scenario: Building with blocks in hand
- **WHEN** the overlay is pinned, the selected 12-layer placement is LOCKED in from-to with layers 4 to 6, and the player holds oak planks
- **THEN** the overlay shows the LOCKED badge, `Layers 4-6 of 12`, progress and shortages, the hints for moving and resizing the window, and no menu hint

#### Scenario: Anchored placement without the remote
- **WHEN** the overlay is pinned, the selected placement is ANCHORED and the player holds a pickaxe
- **THEN** the overlay shows the placement's name, badge and position, and no hints

#### Scenario: Remote back in hand
- **WHEN** the overlay is pinned and the player switches to the remote
- **THEN** the overlay shows every hint as before

#### Scenario: Nothing selected
- **WHEN** the overlay is pinned, no placement is selected and the player holds a sword
- **THEN** no overlay shows

#### Scenario: F1
- **WHEN** the overlay is pinned and the player hides the HUD with F1
- **THEN** no overlay shows
