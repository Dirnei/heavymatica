## ADDED Requirements

### Requirement: Details beside the grid
Selecting an entry SHALL show its details in a panel beside the card grid, while the grid stays visible and usable. The selected card SHALL be marked. With no entry selected, the panel SHALL say to select one.

#### Scenario: Select another card
- **WHEN** a player has `Starter Hut` selected and clicks the `Watchtower` card
- **THEN** the panel shows `Watchtower`'s details, the `Watchtower` card is marked and every other card stays in the grid

#### Scenario: Nothing selected
- **WHEN** a player opens the library and has not selected an entry
- **THEN** the panel says to select a schematic

### Requirement: Every block type in the details
The details panel SHALL list every block type of the chosen version with its count, most common first. When the list is longer than the panel, it SHALL scroll. The panel SHALL say how many block types the version has.

#### Scenario: Long block list
- **WHEN** a player selects an entry with 40 block types
- **THEN** the panel says 40 block types and the list scrolls to show all 40

### Requirement: Actions in the details panel
The details panel SHALL offer the entry's actions: Place as the main action, then Export, Rename and Delete, and for cloud schematics also Move and Tags. An action that is unavailable SHALL be shown disabled with its reason on hover.

#### Scenario: Local entry
- **WHEN** a player selects a local entry
- **THEN** the panel offers Place, Export, Rename and Delete, and no Move or Tags

#### Scenario: Cloud schematic signed out
- **WHEN** a signed-out player selects a cloud schematic that is in the cache
- **THEN** Place and Export work, and Rename, Move, Tags and Delete are disabled with a hover text saying signing in is needed

### Requirement: Details page
The details panel SHALL offer "All details", which opens a page for that entry showing a large preview with its width, height and depth, its format, its Minecraft version when the file records one, its block count and number of block types, when it was added or uploaded, and the same actions as the panel. Back SHALL return to the library with the entry still selected.

#### Scenario: Open and return
- **WHEN** a player selects `Starter Hut`, opens All details and goes back
- **THEN** the details page showed `Starter Hut`'s facts, and the library is shown again with `Starter Hut` selected

#### Scenario: File without a game version
- **WHEN** the chosen version's file records no Minecraft version
- **THEN** the details page shows no Minecraft version

### Requirement: Block list on the details page
The details page SHALL list every block type of the chosen version with its count and a bar for its share of the version's blocks, most common first, with a filter box that narrows the list by block name or ID. Unknown blocks SHALL be marked as in the details panel.

#### Scenario: Filter
- **WHEN** a player types `oak` in the details page's filter
- **THEN** only block types whose name or ID contains `oak` are listed

### Requirement: Settings from the library
The library SHALL offer a button that opens the Heavymatica settings screen. Closing the settings screen SHALL return to the library.

#### Scenario: Open settings
- **WHEN** a player clicks the settings button in the library and then Done
- **THEN** the settings screen opens, and Done returns to the library
