## ADDED Requirements

### Requirement: Active build options above the hotbar
While Easy Place or placement restriction is on, a LOCKED placement exists in the player's dimension, no screen is open and the HUD is not hidden, the client SHALL show a small row centred above the hotbar, clear of the experience bar and the held item's name. The row holds the wheel icon of each of the two options that is on, Easy Place first, each on a small glass background. The row SHALL show whether or not the remote is held. While both options are off, or no locked placement exists in the player's dimension, nothing SHALL show.

#### Scenario: Easy Place on
- **WHEN** Easy Place is on, placement restriction is off and a locked placement is in the player's dimension
- **THEN** the Easy Place icon shows above the hotbar and no restriction icon

#### Scenario: Both on
- **WHEN** Easy Place and placement restriction are on and a locked placement is in the player's dimension
- **THEN** the Easy Place and placement restriction icons show side by side above the hotbar, Easy Place first

#### Scenario: No locked placement
- **WHEN** Easy Place is on and the only placement is ANCHORED
- **THEN** no icons show above the hotbar

#### Scenario: Turned off
- **WHEN** the player turns Easy Place off from the wheel while restriction is off
- **THEN** the icon row disappears

#### Scenario: Screen open
- **WHEN** Easy Place is on and the player opens the inventory
- **THEN** no icons show above the hotbar
