# Spec Delta

## ADDED Requirements

### Requirement: Unknown blocks in the overlay
When the selected placement has unknown blocks, the overlay SHALL show, in every placement state, how many block types are unknown and how many positions they cover over the whole placement, and that they are hidden while unknown blocks are hidden. A placement without unknown blocks SHALL show no such line.

#### Scenario: Missing mod
- **WHEN** the selected anchored placement has 40 `create:shaft` and 12 `create:cogwheel` positions and Create is not installed
- **THEN** the overlay says 2 block types are unknown, at 52 positions

#### Scenario: Hidden
- **WHEN** the same placement is selected and unknown blocks are hidden
- **THEN** the overlay still shows the line and says they are hidden

#### Scenario: Nothing unknown
- **WHEN** the selected placement's blocks are all known after upgrading and swaps
- **THEN** the overlay shows no unknown-blocks line
