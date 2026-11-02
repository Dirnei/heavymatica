# client/schematic-import Specification

## Purpose

Brings schematic files the player already has, such as Litematica, WorldEdit/Sponge or vanilla structure files, into the Heavymatica library, kept local or uploaded to the cloud, so switching to Heavymatica costs nothing.

## Requirements

### Requirement: Drop files onto the library
The client SHALL import schematic files that the player drags and drops onto the library screen. Dropping several files SHALL import each one separately.

#### Scenario: Drop three files
- **WHEN** a player drops two valid files and one invalid file onto the library
- **THEN** the two valid files are imported and the third is reported as rejected with its reason

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

### Requirement: Imports are validated
Every imported file SHALL be read and validated according to the `schematic-formats` capability before it enters the library. A rejected file SHALL leave the library unchanged and SHALL leave the source file untouched.

#### Scenario: Corrupt file
- **WHEN** a player imports a truncated `.litematic` file
- **THEN** the library is unchanged, the player sees why the file was rejected, and the file on disk is unchanged

### Requirement: Local-only entries
An imported file SHALL become a local-only library entry unless the player chooses to upload it. A local-only entry SHALL keep its own copy of the file, so it stays usable if the source file is moved or deleted, and SHALL work without signing in.

#### Scenario: Signed out import
- **WHEN** a signed-out player imports a valid file
- **THEN** it appears in the library as a local entry and can be inspected like any other entry

#### Scenario: Source file deleted
- **WHEN** the original file of a local entry is deleted from the `schematics` folder
- **THEN** the local entry is still in the library and still usable

### Requirement: Upload local entries
The library SHALL let a signed-in player upload a local-only entry, or import a file straight to the cloud. After a successful upload the entry SHALL become a cloud schematic and no longer appear as local-only. A failed upload SHALL keep the local entry.

#### Scenario: Upload succeeds
- **WHEN** a signed-in player uploads a local entry
- **THEN** the library shows it once, as a cloud schematic at version 1

#### Scenario: Upload rejected
- **WHEN** an upload fails, for example because it is over quota
- **THEN** the entry stays local and the player sees the reason

### Requirement: Upload as a new version
When uploading, the player SHALL be able to choose an existing cloud schematic to add the file to as a new version instead of creating a new schematic.

#### Scenario: Update an existing schematic
- **WHEN** a player uploads a local entry as a new version of their cloud schematic `Tower`
- **THEN** `Tower` gains a new version and no new schematic is created

### Requirement: Thumbnails made on upload
When uploading, the client SHALL render a thumbnail of the schematic and attach it to the new version. If rendering or attaching the thumbnail fails, the upload SHALL still succeed without one.

#### Scenario: Thumbnail attached
- **WHEN** a player uploads a file
- **THEN** the new version has a thumbnail showing the structure

#### Scenario: Rendering fails
- **WHEN** the thumbnail cannot be rendered during an upload
- **THEN** the upload completes and the version has no thumbnail

### Requirement: Duplicate imports noticed
When an imported file's content hash matches an entry already in the library, the client SHALL tell the player which entry it matches and SHALL let them skip the import or import it anyway.

#### Scenario: Same file again
- **WHEN** a player imports a file identical to their existing cloud schematic `Barn` version 2
- **THEN** the client says it matches `Barn` version 2 and offers to skip or import anyway

### Requirement: Thumbnails show every visible block
An upload thumbnail SHALL show the same blocks a ghost of the schematic shows: block-entity blocks with their own model and texture, fluids, and blocks without geometry as their outline shape, with old schematics upgraded and known blocks resolved as for ghosts. Unknown blocks SHALL be left out of the thumbnail, whatever the setting for showing them.

#### Scenario: Bedroom
- **WHEN** a player uploads a schematic of a room with a bed, a chest and a small pool
- **THEN** the thumbnail shows the bed, the chest and the water

#### Scenario: Ghost and thumbnail agree
- **WHEN** a block is visible in a ghost of a schematic
- **THEN** it is also visible in that schematic's upload thumbnail, unless something in front of it covers it or it is an unknown block's placeholder

#### Scenario: Old schematic
- **WHEN** a player uploads a schematic from Minecraft 1.20.2 that contains `minecraft:grass`
- **THEN** the thumbnail shows short grass there

#### Scenario: Missing mod
- **WHEN** a player uploads a schematic with `create:shaft` while Create is not installed
- **THEN** the thumbnail shows no missing-texture cubes

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
