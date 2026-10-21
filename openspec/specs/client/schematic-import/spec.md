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
The library SHALL offer an import list of the schematic files found in the game directory's `schematics` folder (where Litematica keeps its files) and in Heavymatica's own import folder, including subfolders, and SHALL let the player import any of them.

#### Scenario: Existing Litematica files
- **WHEN** a player who has Litematica files in `schematics/` opens the import list
- **THEN** those files are listed with their folder paths and can be imported

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
