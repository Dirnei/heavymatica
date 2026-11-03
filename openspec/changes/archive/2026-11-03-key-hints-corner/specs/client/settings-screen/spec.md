## ADDED Requirements

### Requirement: Key hints setting in the HUD section
The HUD section SHALL offer "Show key hints" (`overlay.keyHints`) as a toggle with a name and a one-line explanation, in place of the former compact overlay setting. Changing it SHALL apply at once and be saved, like every other setting.

#### Scenario: Turn key hints off
- **WHEN** a player opens the settings screen, chooses HUD and switches Show key hints off
- **THEN** the client config holds `overlay.keyHints = false` and the corner hints no longer show while holding the remote
