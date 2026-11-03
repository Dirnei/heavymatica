## MODIFIED Requirements

### Requirement: Placement keys only while the remote is held
Heavymatica's placement keys SHALL act only while a remote is in the main hand and no screen is open. The layer keys on a LOCKED selected placement SHALL be the exception: as `client/placement-layers` describes, they also act without the remote.

#### Scenario: Remote not held
- **WHEN** a player holding a sword presses the move key with an anchored placement selected
- **THEN** no placement changes

#### Scenario: Layer key without the remote
- **WHEN** a player holding a sword presses the up arrow with a locked placement in from-to selected
- **THEN** its layer window moves up one layer

#### Scenario: Rotate without the remote
- **WHEN** a player holding a sword presses R with any placement selected
- **THEN** no placement changes
