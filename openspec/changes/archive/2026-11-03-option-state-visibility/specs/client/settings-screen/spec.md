## ADDED Requirements

### Requirement: Pinned overlay setting
The HUD section SHALL offer the pinned overlay setting as a toggle, with a name and a one-line explanation, next to the overlay side and compact overlay settings. Changing it SHALL apply at once and be saved, like every other setting.

#### Scenario: Pin from the settings screen
- **WHEN** a player opens the settings screen, chooses HUD and switches Pin overlay on
- **THEN** the client config holds `overlay.pinned = true` and, back in the game with a placement selected, the overlay shows without the remote
