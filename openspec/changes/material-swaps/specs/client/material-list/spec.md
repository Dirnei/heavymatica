# Spec Delta

## MODIFIED Requirements

### Requirement: Shortage lines in the overlay
While the overlay shows a LOCKED placement, it SHALL show up to 3 items with the largest shortage, largest first, each with its shortage. When nothing is short, it SHALL say so. When more items are short, it SHALL say how many more and name the materials key, which opens the materials screen.

#### Scenario: Several items short
- **WHEN** the selected locked placement is short 12 oak planks, 4 stone bricks, 2 glass and 1 torch
- **THEN** the overlay shows oak planks 12, stone bricks 4, glass 2 and that 1 more is short, with the materials key

#### Scenario: Nothing short
- **WHEN** the player carries everything the visible layers still need
- **THEN** the overlay says nothing is short

#### Scenario: More short with a key
- **WHEN** the materials key is bound to K and the selected locked placement is short of 5 items
- **THEN** the overlay's "more short" line names K and Materials

## REMOVED Requirements

### Requirement: Material list screen
**Reason**: The material list screen merges into the materials screen of `client/material-swaps`, which opens for a placement in every state and shows the material list's needed, had and short counts while the placement is LOCKED.
**Migration**: The material list key becomes the materials key and keeps its binding. See `client/material-swaps`: "Materials screen", "Needs on a locked placement" and "Materials screen order and can't carry".
