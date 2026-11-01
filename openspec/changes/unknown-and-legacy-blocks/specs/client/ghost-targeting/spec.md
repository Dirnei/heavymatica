# Spec Delta

## MODIFIED Requirements

### Requirement: Targetable ghost cells
A position SHALL be targetable only when it is drawn: it belongs to a LOCKED placement in the player's dimension, selected or not, lies in the layers its layer setting shows, and either shows a ghost that passes the in-hand filter (dimmed counts), shows a red, orange, cyan or yellow box, or shows the unknown-block placeholder. Matching, ignored, air and extra positions, can't check positions while unknown blocks are hidden, and every position of FOLLOWING and ANCHORED placements, SHALL NOT be targetable.

#### Scenario: Missing block in a visible layer
- **WHEN** a locked placement shows layers 0 to 4 and expects oak planks on layer 2 where the world has air
- **THEN** that position is targetable

#### Scenario: Outside the visible layers
- **WHEN** a locked placement shows layers 4 to 6 and expects oak planks on layer 2 where the world has air
- **THEN** that position is not targetable

#### Scenario: Hidden by the in-hand filter
- **WHEN** the in-hand filter is on, the player holds oak planks, no cobblestone is in the hotbar, and a visible position expects cobblestone where the world has air
- **THEN** that position is not targetable

#### Scenario: Dimmed by the in-hand filter
- **WHEN** the in-hand filter is on, the player holds oak planks with oak stairs in the hotbar, and a visible position expects oak stairs where the world has air
- **THEN** that position is targetable

#### Scenario: Already built
- **WHEN** a locked placement expects oak planks at a position where the world has oak planks
- **THEN** that position is not targetable

#### Scenario: Anchored placement
- **WHEN** a placement is ANCHORED and the player looks at its ghost
- **THEN** none of its positions is targetable

#### Scenario: Unknown block shown
- **WHEN** a locked placement shows layers 0 to 4, unknown blocks are shown, and layer 2 has `create:shaft` while Create is not installed
- **THEN** that position is targetable

#### Scenario: Unknown block hidden
- **WHEN** the same placement is locked and unknown blocks are hidden
- **THEN** that position is not targetable

## ADDED Requirements

### Requirement: Unknown blocks in the HUD
When the ghost target is an unknown block, the targeting HUD, or Jade's Heavymatica line when Jade is installed, SHALL name the block ID as the schematic shows it and say the game doesn't know it. Pick block SHALL behave as without Heavymatica on such a target, since no item places it.

#### Scenario: Missing mod
- **WHEN** the crosshair targets a placeholder for `create:shaft` while Create is not installed
- **THEN** the HUD shows `create:shaft` and that the block is unknown

#### Scenario: Middle click
- **WHEN** the player middle-clicks that placeholder within reach
- **THEN** pick block does what it does without Heavymatica
