## MODIFIED Requirements

### Requirement: Import from schematic folders
The library SHALL offer an import list of the schematic files found in the game directory's `schematics` folder (where Litematica keeps its files) and in Heavymatica's own import folder, including subfolders, and SHALL let the player import any of them. Each file SHALL show its folder path, format and size. The player SHALL be able to tick several files and import them in one step, and to open the `schematics` folder in the system's file browser.

#### Scenario: Existing Litematica files
- **WHEN** a player who has Litematica files in `schematics/` opens the import list
- **THEN** those files are listed with their folder paths and can be imported

#### Scenario: Import several at once
- **WHEN** a player ticks `Starter Hut.litematic` and `builds/bridge.nbt` and chooses Import
- **THEN** both become library entries, and the files that were not ticked are not imported

#### Scenario: Open the folder
- **WHEN** a player chooses Open folder on the import screen
- **THEN** the game directory's `schematics` folder opens in the system's file browser, created first if it does not exist

## ADDED Requirements

### Requirement: Empty library shows the import screen
When the library has no entries at all, opening it SHALL show the import screen with a short explanation of how to bring in or capture a first schematic, instead of an empty grid. Once an entry exists, the library SHALL open on its grid.

#### Scenario: First open
- **WHEN** a new player with no local or cloud entries opens the library
- **THEN** the import screen shows, listing the files in `schematics/` and explaining Capture

#### Scenario: After the first import
- **WHEN** that player imports a file and opens the library again
- **THEN** the library opens on its grid with the new entry

### Requirement: Drop files onto the import screen
The import screen SHALL accept dropped schematic files anywhere on it, the same way the library does.

#### Scenario: Drop on the import screen
- **WHEN** a player drops a valid `.schem` file onto the import screen
- **THEN** it is imported as a local entry

### Requirement: Thumbnails for local entries
Importing a file as a local entry SHALL render a thumbnail for it, the same way an upload does, and keep it with the entry. Local entries imported before this change SHALL get their thumbnail the first time the library shows them. If rendering fails, the entry SHALL keep the placeholder and stay usable.

#### Scenario: Imported entry has a picture
- **WHEN** a player imports `Starter Hut.litematic`
- **THEN** its card shows a thumbnail of the structure

#### Scenario: Older entry
- **WHEN** a local entry imported before this change is shown in the library
- **THEN** a thumbnail is rendered and its card shows it from then on
