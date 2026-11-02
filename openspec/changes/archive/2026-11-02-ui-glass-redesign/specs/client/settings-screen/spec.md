## Purpose

Lets players see and change every Heavymatica client setting in the game, with a short explanation of each, instead
of editing the config file by hand.

## ADDED Requirements

### Requirement: Settings screen opens from the mods list and the library
The client SHALL offer a settings screen that opens from Heavymatica's config button in the game's mods list and from the library's settings button. Closing it SHALL return to the screen it was opened from.

#### Scenario: From the mods list
- **WHEN** a player opens the mods list, selects Heavymatica and chooses Config
- **THEN** the Heavymatica settings screen opens, and Done returns to the mods list

### Requirement: Settings in sections
The settings screen SHALL group the settings into the sections Remote and wheel, Ghosts, Building, HUD, Library and Performance, show one section at a time, and give every setting a name and a one-line explanation. It SHALL show every client setting except the cloud address and settings meant only for development, which stay in the config file.

#### Scenario: Switch section
- **WHEN** a player opens the settings screen and chooses Building
- **THEN** Easy Place, placement restriction, pick block on ghosts and the mistake pointer are shown, each with its explanation

#### Scenario: Cloud address hidden
- **WHEN** a player looks through every section
- **THEN** no section offers the cloud address or a development setting

### Requirement: Heavymatica Cloud setting
The Library section SHALL offer the cloud access setting as "Heavymatica Cloud" with the choices Automatic, Always and Never.

#### Scenario: Turn the cloud off
- **WHEN** a player sets Heavymatica Cloud to Never
- **THEN** the cloud access setting is `off`

### Requirement: Changes apply at once and are saved
A change on the settings screen SHALL take effect at once, while the screen is still open, and SHALL be saved to the client config file so it stays after a restart. A value outside a setting's allowed range SHALL NOT be accepted.

#### Scenario: Ghost opacity
- **WHEN** a player in a world drags Ghost opacity to 60% on the settings screen
- **THEN** every ghost shows at 60% opacity, and after a restart the setting is still 60%

#### Scenario: Out of range
- **WHEN** a player types 5 into the ghost block budget, whose minimum is 1000
- **THEN** the value is not accepted and the setting keeps its previous value

### Requirement: Reset a section
The settings screen SHALL offer to reset the shown section to its defaults. Settings in other sections SHALL keep their values.

#### Scenario: Reset ghosts
- **WHEN** a player has ghost opacity at 80% and Easy Place on, and resets the Ghosts section
- **THEN** ghost opacity is back at 40% and Easy Place is still on

### Requirement: Remote item from the hand
The remote item setting SHALL show the current remote item with its icon and name, and SHALL offer "Use held item", which makes the item in the player's main hand the remote. With an empty main hand, or outside a world, "Use held item" SHALL be disabled with the reason shown on hover.

#### Scenario: Use a blaze rod
- **WHEN** a player holding a blaze rod opens the settings and chooses Use held item
- **THEN** the remote item becomes `minecraft:blaze_rod` and holding the blaze rod shows the overlay

#### Scenario: Empty hand
- **WHEN** a player with an empty main hand opens the settings
- **THEN** Use held item is disabled and its hover text says to hold the item first

### Requirement: Opacity steps as a list
The wheel's opacity steps SHALL be shown as one entry per step, each removable, with a way to add a step. The screen SHALL keep the same rules as the setting: 2 to 8 steps from 5% to 100%, without duplicates, sorted from low to high.

#### Scenario: Add a step
- **WHEN** a player adds a 30% step to 20, 40, 60, 80 and 100%
- **THEN** the steps are 20, 30, 40, 60, 80 and 100%, and the wheel's Opacity page offers six steps

#### Scenario: Two steps minimum
- **WHEN** only two steps are left
- **THEN** neither can be removed
