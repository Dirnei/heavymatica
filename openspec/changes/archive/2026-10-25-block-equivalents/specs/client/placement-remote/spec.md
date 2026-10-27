# Spec Delta

## ADDED Requirements

### Requirement: Finishing in the overlay
For a LOCKED selected placement, the overlay SHALL also show, in yellow, how many positions in the visible layers need each finishing step, for every step that any visible position needs. These lines SHALL follow the layer setting, while the counts per mistake state stay over the whole placement. When the crosshair targets a position of the placement that needs finishing, the overlay SHALL show the expected and the found block and the step.

#### Scenario: Finishing steps
- **WHEN** the visible layers of the selected locked placement have 3 dirt paths on dirt and 2 stripped oak logs on oak logs
- **THEN** the overlay shows in yellow that 3 blocks need a shovel and 2 blocks need an axe, and no line for a hoe or water

#### Scenario: Nothing to finish
- **WHEN** no position in the visible layers of the selected locked placement needs finishing
- **THEN** the overlay shows no finishing line

#### Scenario: Finishing follows the visible layers
- **WHEN** the selected locked placement shows layers 4 to 6, and layer 2 has 3 dirt paths on dirt and 2 wrong blocks
- **THEN** the overlay shows no shovel line and still counts the 2 wrong blocks in red

#### Scenario: Looking at a block to finish
- **WHEN** the crosshair targets a position of the selected locked placement that expects white concrete and holds white concrete powder
- **THEN** the overlay shows white concrete as expected, white concrete powder as found, and that it needs water
