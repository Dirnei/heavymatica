# Spec Delta

## MODIFIED Requirements

### Requirement: Placement overlay
While a remote is in the main hand and no screen is open, the client SHALL show an overlay on the configured side of the screen (default left). It SHALL show the selected placement's name, version, a state badge (FOLLOWING, ANCHORED or LOCKED), position, rotation and mirror, or "No placement selected" when none is. For a LOCKED placement it SHALL also show:
- the layer setting with its layers and the placement's height
- whether the in-hand filter is on
- its progress in percent with matching and counted blocks, or that it is still scanning until every loaded position has been compared once
- its layer progress, when the layer setting is not off
- the number of positions per state: wrong block, wrong orientation, wrong water and extra
- when the crosshair targets a position of the placement in one of those states, the expected and the found block

#### Scenario: Anchored placement
- **WHEN** a player holds the remote and the selected placement `Castle Gate` version 3 is anchored at 128, 64, -40 with rotation 90 and no mirror
- **THEN** the overlay shows `Castle Gate`, `v3`, an ANCHORED badge, `128, 64, -40`, `90` and mirror off

#### Scenario: Remote put away
- **WHEN** the player switches from the remote to another item
- **THEN** the overlay disappears

#### Scenario: Locked placement with layers
- **WHEN** the selected 12-layer placement is LOCKED with layers 4 to 6 and the in-hand filter on
- **THEN** the overlay shows a LOCKED badge, `Layers 4-6 of 12` and that the in-hand filter is on

#### Scenario: Progress and mistakes
- **WHEN** the selected locked placement has 3104 of 5006 blocks matching, 12 wrong blocks, 4 wrong orientations, 2 wrong water and 3 extra
- **THEN** the overlay shows `62%`, `3104 / 5006` and the counts 12, 4, 2 and 3 in red, orange, cyan and magenta

#### Scenario: Just locked
- **WHEN** the player has just locked a placement and not every loaded position has been compared yet
- **THEN** the overlay says the placement is scanning instead of showing a percentage

#### Scenario: Looking at a mistake
- **WHEN** the crosshair targets a position of the selected locked placement that expects oak stairs facing north and holds oak stairs facing east
- **THEN** the overlay shows the expected and the found stairs with their facing

#### Scenario: Anchored placement shows no progress
- **WHEN** the selected placement is ANCHORED
- **THEN** the overlay shows no progress and no counts
