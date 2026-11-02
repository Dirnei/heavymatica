# Spec Delta

## Purpose

Points the player to the nearest position of the selected locked placement that still needs attention, so mistakes and
the last missing blocks of a large build can be found without searching for boxes.

## ADDED Requirements

### Requirement: Mistake pointer setting
The mistake pointer SHALL be a client setting with the values off, mistakes, and mistakes and missing; off by default,
saved in the client config so it survives restarts. It SHALL NOT depend on the placement.

#### Scenario: Default
- **WHEN** a player starts the game with a fresh config
- **THEN** the mistake pointer is off

#### Scenario: Survives a restart
- **WHEN** the player sets the mistake pointer to mistakes and missing and restarts the game
- **THEN** the mistake pointer is still set to mistakes and missing

### Requirement: What the pointer looks for
The pointer's candidates SHALL be the positions of the selected placement, while it is LOCKED, that lie in the layers
its layer setting shows and whose state is boxed: wrong block, wrong orientation, too many, not enough yet, wrong water,
extra or needs finishing. In the mistakes and missing mode, missing positions SHALL be candidates too. Matching,
ignored, air and can't-check positions SHALL never be candidates, and nor SHALL positions of other placements.

#### Scenario: Mistakes mode ignores missing blocks
- **WHEN** the pointer is set to mistakes and the selected locked placement has missing positions but no boxed ones
- **THEN** the pointer has no candidate

#### Scenario: Missing blocks counted
- **WHEN** the pointer is set to mistakes and missing and the only position left is a missing block
- **THEN** that position is the candidate

#### Scenario: Hidden layer
- **WHEN** the only wrong block of the selected locked placement lies in a layer its layer setting hides
- **THEN** the pointer has no candidate

### Requirement: The nearest candidate
The pointer SHALL point at the candidate nearest to the player's eye by straight-line distance to the position's centre.
When two are equally near, either MAY be chosen. The choice SHALL follow the player's movement and changes in the world
within a second.

#### Scenario: Two mistakes
- **WHEN** the selected locked placement has a wrong block 5 blocks from the player and another 40 blocks away
- **THEN** the pointer points at the one 5 blocks away

#### Scenario: Fixed mistake
- **WHEN** the player breaks the wrong block the pointer points at and places the planned block
- **THEN** within a second the pointer points at the next nearest candidate

### Requirement: Pointer HUD
While the pointer is not off, no screen is open, and the selected placement is LOCKED, the
client SHALL show a small pointer below the crosshair: an arrow for the candidate's horizontal direction relative to
where the player looks, the height difference in blocks with up or down, the distance in blocks, and the candidate's
state named in the comparison colours. It SHALL sit below the targeting HUD when both show.

#### Scenario: Mistake behind the player
- **WHEN** the nearest candidate is a wrong block 12 blocks behind the player and 3 blocks higher
- **THEN** the pointer shows an arrow pointing down the screen (behind), "3 up", "12 blocks" and "Wrong block" in red

#### Scenario: Turned off
- **WHEN** the pointer is off
- **THEN** no pointer shows

#### Scenario: No locked placement selected
- **WHEN** the pointer is set to mistakes and the selected placement is ANCHORED
- **THEN** no pointer shows

### Requirement: Nothing left
When the pointer is not off and the selected locked placement has no candidate, the pointer SHALL show that nothing is
left in its mode instead of an arrow: no mistakes, or no mistakes and nothing missing.

#### Scenario: Clean build in mistakes mode
- **WHEN** the pointer is set to mistakes and the selected locked placement has no boxed position in its visible layers
- **THEN** the pointer says there are no mistakes
