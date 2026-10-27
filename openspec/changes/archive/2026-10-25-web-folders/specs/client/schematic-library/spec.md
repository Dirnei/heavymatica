# Spec Delta

## MODIFIED Requirements

### Requirement: Search, folders and tags
The library SHALL browse folders like a file explorer: the top level shows the top-level folders as folder cards, then the entries in no folder (including local entries); an open folder shows a card to go up, its subfolders as folder cards with their counts including subfolders, then only the entries directly in it, with its path shown. An "All schematics" view SHALL show every entry without folder cards. Searching by name or choosing a tag SHALL show matching entries from the open folder and below it, without folder cards. Text and tag filters SHALL combine.

#### Scenario: Combined filter
- **WHEN** a player has `houses` open, selects tag `spruce`, and types `tow`
- **THEN** only entries in `houses` or below it, tagged `spruce`, whose name contains `tow` in any letter case are shown

#### Scenario: Open a folder
- **WHEN** a player has entries in `castle` and `castle/towers` and opens `castle`
- **THEN** the grid shows the up card, a folder card `towers`, then the entries directly in `castle`

#### Scenario: Go up
- **WHEN** a player in `castle/towers` clicks the up card
- **THEN** `castle` is open

#### Scenario: All schematics
- **WHEN** a player turns on "All schematics"
- **THEN** every entry is shown, whatever its folder, and no folder or up cards are shown
