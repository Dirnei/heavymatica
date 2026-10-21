# client/placement-layers Specification

## Purpose

Lets a player building by hand see only part of a locked ghost: a window of layers, the layers up to their feet, and only the blocks they hold.

## Requirements

### Requirement: Layer settings are per placement and need a lock
Each placement SHALL have its own layer setting (off, from-to or follow feet) and in-hand flag. They SHALL only be changeable while the placement is LOCKED, and SHALL only take effect while it is LOCKED. An unlocked placement SHALL always show in full.

#### Scenario: Other placements unaffected
- **WHEN** the locked placement `Gate` shows layers 4 to 6 and `Tower` has its layer setting off
- **THEN** `Tower` shows all its layers

#### Scenario: Unlocking shows the whole ghost
- **WHEN** a locked placement showing layers 4 to 6 is unlocked
- **THEN** it shows all its layers, and locking it again shows layers 4 to 6 again

### Requirement: Layers count from the bottom
Layers SHALL be the placement's horizontal slices, numbered from 0 at its bottom up to its height minus 1. Only the Y axis SHALL be sliced.

#### Scenario: Layer numbers
- **WHEN** a placement is 12 blocks high and its bottom is at y 64
- **THEN** layer 0 is at y 64 and layer 11 at y 75

### Requirement: Cycling the layer setting
While the selected placement is LOCKED and a remote is held, the right arrow key SHALL cycle its layer setting from off to from-to to follow feet and back to off, and the left arrow key SHALL cycle the other way.

#### Scenario: Cycle forward
- **WHEN** the selected locked placement's layer setting is off and the player presses the right arrow twice
- **THEN** its layer setting is follow feet

### Requirement: From-to window
In from-to, the placement SHALL show only the layers from a bottom layer to a top layer, inclusive. When from-to is first chosen, the window SHALL be the single layer at the player's feet, clamped into the placement (layer 0 when the player is below it).

#### Scenario: First window at the feet
- **WHEN** the player stands at y 68 next to a placement whose bottom is at y 64 and switches it to from-to
- **THEN** the placement shows only layer 4

#### Scenario: Window shows a range
- **WHEN** the window is layers 4 to 6
- **THEN** layers 4, 5 and 6 show and no others

### Requirement: Moving the window
While a remote is held and the selected LOCKED placement is in from-to, the up and down arrow keys SHALL move the whole window one layer up or down, keeping its size, and SHALL stop at layer 0 and at the top layer.

#### Scenario: Step up
- **WHEN** the window is layers 4 to 6 and the player presses the up arrow
- **THEN** the window is layers 5 to 7

#### Scenario: Top reached
- **WHEN** a 12-layer placement's window is layers 9 to 11 and the player presses the up arrow
- **THEN** the window stays layers 9 to 11

### Requirement: Resizing the window
While a remote is held and the selected LOCKED placement is in from-to, numpad + SHALL raise the window's top layer by one and numpad - SHALL lower it by one. The window SHALL never be smaller than one layer or reach above the placement's top layer.

#### Scenario: Grow
- **WHEN** the window is layer 4 only and the player presses numpad + twice
- **THEN** the window is layers 4 to 6

#### Scenario: Shrink to one
- **WHEN** the window is layer 4 only and the player presses numpad -
- **THEN** the window stays layer 4 only

### Requirement: Follow feet
In follow feet, the placement SHALL show the layers from 0 up to the layer at the player's feet plus the placement's follow offset, clamped into the placement. Offset 0 is the layer the player stands on and 2 is head height. The offset SHALL default to 1. It SHALL update as the player moves up or down, whatever the player holds.

#### Scenario: Standing on layer 3
- **WHEN** the player's feet are at layer 3 of a locked placement in follow feet with the default offset
- **THEN** layers 0 to 4 show

#### Scenario: Below the placement
- **WHEN** the player's feet are below the placement's bottom and the offset is 1
- **THEN** only layer 0 shows

### Requirement: Changing the follow offset
While a remote is held and the selected LOCKED placement is in follow feet, numpad + SHALL raise its follow offset by one and numpad - SHALL lower it by one, between 0 and 16. The offset SHALL be kept per placement.

#### Scenario: Up to head height
- **WHEN** the follow offset is 1, the player's feet are at layer 3 and the player presses numpad +
- **THEN** layers 0 to 5 show

#### Scenario: Only the layer stood on
- **WHEN** the follow offset is 1 and the player presses numpad - twice
- **THEN** the offset is 0 and the shown layers end at the layer the player stands on

### Requirement: In-hand filter
While the selected placement is LOCKED and a remote is held, the I key SHALL toggle its in-hand filter. With the filter on, the placement SHALL show the blocks matching the player's held item at normal opacity, and the blocks matching the other hotbar items at a configurable lower opacity (default one third). Other blocks SHALL NOT show. An item matches a block when it places that block, in any block state.

#### Scenario: Holding oak planks
- **WHEN** the in-hand filter is on, the player holds oak planks, and oak stairs are in the hotbar
- **THEN** the placement's oak planks show normally, its oak stairs show dimmed, and its cobblestone does not show

#### Scenario: Holding the remote
- **WHEN** the in-hand filter is on and the player holds the remote, a stick
- **THEN** only the blocks matching the hotbar's block items show, all dimmed

### Requirement: Filters combine
The layer setting and the in-hand filter SHALL apply together: a block SHALL show only when it is in a shown layer and passes the in-hand filter.

#### Scenario: Building one layer
- **WHEN** a locked placement is in follow feet with the in-hand filter on, the player stands on layer 3 and holds oak planks
- **THEN** only oak planks in layers 0 to 4 show normally, and other hotbar blocks in those layers show dimmed

### Requirement: Slicing never hides geometry wrongly
A shown block SHALL render its faces as if the blocks that are not shown were air, so a slice never shows holes or missing faces where hidden blocks used to be.

#### Scenario: Single layer of a solid wall
- **WHEN** a solid wall placement shows only layer 4
- **THEN** layer 4's top and bottom faces are drawn

### Requirement: Layers change without a delay
Moving or resizing the window, following the player, switching held items and toggling the in-hand filter SHALL change what shows within the next frame.

#### Scenario: Scrolling the hotbar
- **WHEN** the in-hand filter is on and the player scrolls through the hotbar
- **THEN** the shown blocks change with each slot, with no frame where the ghost disappears
