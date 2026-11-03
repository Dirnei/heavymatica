## MODIFIED Requirements

### Requirement: Right-click on no ghost opens the library
Right-clicking with the remote (without sneaking) when no placement is FOLLOWING and the line of sight hits no ghost SHALL deselect the selected placement, leaving it where it is. When no placement is selected, the same click SHALL open the library.

#### Scenario: Open the library with the remote
- **WHEN** no placement is selected and a player holding the remote right-clicks at a spot with no ghost
- **THEN** the library screen opens

#### Scenario: Deselect with the remote
- **WHEN** the anchored placement `Gate` is selected and the player right-clicks at the sky with the remote
- **THEN** no placement is selected, `Gate` stays where it is, and the library does not open
