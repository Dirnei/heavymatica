# Spec Delta

## MODIFIED Requirements

### Requirement: Cycling the layer setting
While the selected placement is LOCKED and a remote is held, the wheel's Layers sub-wheel SHALL set its layer setting to off, from-to or follow feet directly. The cycle layer mode key, when bound, SHALL cycle it from off to from-to to follow feet and back to off, and the other way with Shift held. The arrow keys SHALL NOT change the layer setting.

#### Scenario: Cycle forward
- **WHEN** the player has bound the cycle layer mode key, the selected locked placement's layer setting is off and the player presses that key twice
- **THEN** its layer setting is follow feet

#### Scenario: Set from the wheel
- **WHEN** the selected locked placement is in from-to and the player picks Layers, then Off on the wheel
- **THEN** its layer setting is off

#### Scenario: Right arrow
- **WHEN** the selected locked placement's layer setting is off and the player presses the right arrow
- **THEN** its layer setting stays off

### Requirement: In-hand filter
While the selected placement is LOCKED and a remote is held, the wheel's In-hand filter segment, or the in-hand filter key when bound, SHALL toggle its in-hand filter. With the filter on, the placement SHALL show the blocks matching the player's held item at normal opacity, and the blocks matching the other hotbar items at a configurable lower opacity (default one third). Other blocks SHALL NOT show. An item matches a block when it places that block, in any block state.

#### Scenario: Holding oak planks
- **WHEN** the in-hand filter is on, the player holds oak planks, and oak stairs are in the hotbar
- **THEN** the placement's oak planks show normally, its oak stairs show dimmed, and its cobblestone does not show

#### Scenario: Holding the remote
- **WHEN** the in-hand filter is on and the player holds the remote, a stick
- **THEN** only the blocks matching the hotbar's block items show, all dimmed

#### Scenario: Toggle from the wheel
- **WHEN** the selected locked placement's in-hand filter is off and the player picks In-hand filter on the wheel
- **THEN** its in-hand filter is on
