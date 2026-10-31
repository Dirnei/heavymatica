# Spec Delta

## ADDED Requirements

### Requirement: Export an entry as a file
The entry details SHALL offer Export for a local entry and for the chosen version of a cloud schematic. Export SHALL write that file unchanged, in its own format, to the game directory's `schematics` folder as the entry's name with the file's extension, with characters that are not allowed in file names replaced. The player SHALL be told where the file was written.

#### Scenario: Export a capture
- **WHEN** a player exports the local entry `Starter Hut`
- **THEN** `schematics/Starter Hut.litematic` exists with the same bytes as the entry's stored copy

#### Scenario: Export a cloud version in its own format
- **WHEN** a player exports version 2 of the cloud schematic `Castle`, which was uploaded as a `.schem` file
- **THEN** `schematics/Castle.schem` exists with the bytes of version 2

#### Scenario: Unsafe characters
- **WHEN** a player exports an entry named `Tower: v2/final`
- **THEN** the file is written in `schematics/` itself under a name with the `:` and `/` replaced

### Requirement: Exporting cloud versions
Export of a cloud version SHALL take the file from the cache, or download it first. A version that is unavailable offline SHALL have Export disabled with the reason shown.

#### Scenario: Not cached yet
- **WHEN** a signed-in player exports a version that is not in the cache
- **THEN** the version is downloaded, kept in the cache and written to `schematics/`

#### Scenario: Offline, not cached
- **WHEN** the cloud is unreachable and the chosen version is not in the cache
- **THEN** Export is disabled and says the version needs a connection

### Requirement: Export never overwrites silently
When the export's file already exists, the client SHALL ask whether to overwrite it, save it under the first free name with a number added such as `Starter Hut (2).litematic`, or cancel. Nothing SHALL be written until the player chooses.

#### Scenario: Keep both
- **WHEN** `schematics/Starter Hut.litematic` exists and the player exports `Starter Hut` and chooses to keep both
- **THEN** the new file is `schematics/Starter Hut (2).litematic` and the existing file is unchanged

#### Scenario: Cancel
- **WHEN** the file exists and the player cancels
- **THEN** no file in `schematics/` changes
