# Spec Delta

## ADDED Requirements

### Requirement: Finishing a placement
A locked placement SHALL finish when its progress becomes 100% while it has at least one counted position. On finishing, the client SHALL play a short success sound, show a toast with the placement's name that says how to bring it back, and remove the placement; when it was selected, no placement SHALL be selected afterwards.

#### Scenario: Last block placed
- **WHEN** a locked placement has 200 counted positions, 199 match, and the player places the last right block
- **THEN** the success sound plays, a toast names the placement, and the placement is gone from the world's list

#### Scenario: Locking a finished build
- **WHEN** the player locks a placement over a build that already matches it everywhere
- **THEN** the placement finishes once every counted position has been compared

#### Scenario: Not finished while parts are unknown
- **WHEN** every compared position of a locked placement matches but part of it is in a chunk that is not loaded
- **THEN** the placement does not finish

#### Scenario: Extra blocks do not stop it
- **WHEN** every counted position matches and the footprint also has extra blocks
- **THEN** the placement finishes

#### Scenario: Empty schematic
- **WHEN** a locked placement has no counted positions
- **THEN** it does not finish

### Requirement: Bringing a finished placement back
For 10 seconds after a finish the player SHALL be able to bring the placement back, restoring it with the same version, position, rotation, mirror, dimension, layers, in-hand filter and swaps, LOCKED and selected. Only the most recent finish SHALL be able to be brought back. A brought-back placement SHALL NOT finish again until its progress has been below 100% with every counted position compared, and then reaches 100% again.

#### Scenario: Bring it back
- **WHEN** a placement finished 4 seconds ago and the player brings it back
- **THEN** it is in the world's list again, LOCKED, selected, at its old position, and it does not finish again at once

#### Scenario: Too late
- **WHEN** a placement finished 11 seconds ago
- **THEN** it can no longer be brought back

#### Scenario: Only the latest
- **WHEN** two placements finish 2 seconds apart
- **THEN** only the second can be brought back

#### Scenario: Finishes again after a repair
- **WHEN** a brought-back placement has a block broken, so its progress drops below 100%, and the player places it again
- **THEN** the placement finishes again
