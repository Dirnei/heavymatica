## MODIFIED Requirements

### Requirement: Overlay key hints
While a remote is in the main hand, no screen is open and the HUD is not hidden, the client SHALL list key hints in the bottom-right corner of the screen, apart from the placement overlay. The hints SHALL cover the actions available in the current state and only those. Each hint SHALL show the key the player currently has bound, not the default. It SHALL always show the wheel key as "menu". An action that is on the wheel SHALL only get a hint when the player has bound a key for it. The placement overlay itself SHALL NOT list key hints.

#### Scenario: Hints follow the state
- **WHEN** the selected placement is FOLLOWING
- **THEN** the corner hints show anchoring, rotating, mirroring and the menu, and do not show moving with the arrow keys

#### Scenario: Hints show rebound keys
- **WHEN** a player has rebound "move up" to Page Up
- **THEN** the corner hint for moving up names Page Up

#### Scenario: Wheel actions without keys
- **WHEN** the selected placement is ANCHORED and the player has default bindings
- **THEN** the corner hints show `V menu` and no hints for lock or remove

#### Scenario: Bound wheel action
- **WHEN** the player has bound the lock / unlock key to L and the selected placement is ANCHORED
- **THEN** the corner hints also show L for lock

#### Scenario: No hints in the panel
- **WHEN** a player holds the remote with an anchored placement selected
- **THEN** the overlay on the configured side shows the placement's status and no key hints

### Requirement: Pinned overlay
The client SHALL offer an `overlay.pinned` setting, off by default. While it is on, a placement is selected, no screen is open and the HUD is not hidden, the overlay SHALL also show without the remote in the main hand, with the same status as with the remote. Without the remote, the corner hint list SHALL show only for a LOCKED placement, with its layer header and layer-key hints. With no placement selected, a pinned overlay SHALL NOT show without the remote.

#### Scenario: Building with blocks in hand
- **WHEN** the overlay is pinned, the selected 12-layer placement is LOCKED in from-to with layers 4 to 6, and the player holds oak planks
- **THEN** the overlay shows the LOCKED badge, `Layers 4-6 of 12`, progress and shortages, and the corner shows the from-to header and the hints for moving and resizing the window, with no menu hint

#### Scenario: Anchored placement without the remote
- **WHEN** the overlay is pinned, the selected placement is ANCHORED and the player holds a pickaxe
- **THEN** the overlay shows the placement's name, badge and position, and no corner hints show

#### Scenario: Remote back in hand
- **WHEN** the overlay is pinned and the player switches to the remote
- **THEN** the corner shows every hint for the state

#### Scenario: Nothing selected
- **WHEN** the overlay is pinned, no placement is selected and the player holds a sword
- **THEN** no overlay and no corner hints show

#### Scenario: F1
- **WHEN** the overlay is pinned and the player hides the HUD with F1
- **THEN** neither the overlay nor the corner hints show

## ADDED Requirements

### Requirement: Layer header in the key hints
For a LOCKED selected placement, the corner hint list SHALL start with a header naming the layer setting and its layers, followed by the layer-key hints and then the other hints. The header SHALL say `Layers: off` for off, give the window and the placement's height for from-to, and give the follow offset with the shown layers for follow feet.

#### Scenario: From-to header
- **WHEN** the player holds the remote and the selected 12-layer placement is LOCKED in from-to with layers 4 to 6
- **THEN** the corner list starts with `From-to: layers 4-6 of 12`, followed by the hints for the layer window and the window size

#### Scenario: Follow feet header
- **WHEN** the selected locked placement is in follow feet with offset 1 and shows layers 0 to 4 of 12
- **THEN** the corner list starts with `Follow feet +1: layers 0-4 of 12`, followed by the hint for the height above the feet

#### Scenario: Layers off
- **WHEN** the selected locked placement's layer setting is off
- **THEN** the corner list starts with `Layers: off` and has no window hints

### Requirement: Key hints clear of subtitles and the hotbar
The corner hint list SHALL be right-aligned at the bottom-right of the screen. While vanilla subtitles are on, it SHALL sit above the area where subtitles show. When the screen is too narrow for the list to fit beside the hotbar, it SHALL sit above the hotbar.

#### Scenario: Subtitles on
- **WHEN** subtitles are on and the player holds the remote
- **THEN** the corner hints sit above the subtitle area

#### Scenario: Narrow screen
- **WHEN** the screen is so narrow that the list would reach the hotbar
- **THEN** the list sits above the hotbar

### Requirement: Key hints setting
The client SHALL offer an `overlay.keyHints` setting, on by default. While it is off, the corner hint list SHALL NOT show. A player who had the former compact overlay setting on SHALL find key hints off after updating.

#### Scenario: Hints off
- **WHEN** key hints are off and the player holds the remote
- **THEN** the overlay shows the placement's status and no corner hints show

#### Scenario: Compact overlay carried over
- **WHEN** a player had compact overlay on and starts the game with this version
- **THEN** key hints are off and the overlay shows the full status

## REMOVED Requirements

### Requirement: Compact overlay
**Reason**: Once the key hints move to the corner, the overlay is short enough that a mode hiding its status is not needed; the key hints setting takes its role.
**Migration**: A compact overlay that was on turns into key hints off on the first start.
