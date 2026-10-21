# Spec Delta

## Purpose

Stores each player's schematics in the Heavymatica Cloud as immutable versions, lets them organize and find them, and serves them back to the game wherever the player plays.

## ADDED Requirements

### Requirement: Upload creates a schematic or a new version
The service SHALL accept a schematic file upload either as a new schematic or as a new version of an existing schematic the caller owns. A new schematic SHALL start at version 1, and each new version SHALL take the next version number.

#### Scenario: New schematic
- **WHEN** a signed-in player uploads a valid file without naming an existing schematic
- **THEN** a new schematic owned by that player exists with version 1, named after the given name or, if none is given, the file name

#### Scenario: New version
- **WHEN** a player uploads a valid file for a schematic they own whose latest version is 3
- **THEN** the schematic has version 4 and versions 1 to 3 are unchanged

### Requirement: Uploads are validated before they are stored
The service SHALL parse and validate every upload according to the `schematic-formats` capability before storing it. A rejected upload SHALL leave no schematic, version or stored file behind.

#### Scenario: Invalid file rejected
- **WHEN** a player uploads a file that is not a supported format or fails validation
- **THEN** the service rejects the upload with a reason and the player's library is unchanged

### Requirement: Versions are immutable
Once a version is stored, its file content and metadata SHALL never change. The service SHALL identify each version's content by a SHA-256 hash of the stored file.

#### Scenario: Download matches the hash
- **WHEN** a client downloads any version
- **THEN** the SHA-256 of the received bytes equals the content hash reported in that version's metadata

### Requirement: Identical re-upload creates no new version
The service SHALL NOT create a new version when the uploaded file's content hash equals that of the schematic's latest version, and SHALL return the latest version instead.

#### Scenario: Same file uploaded twice
- **WHEN** a player uploads, as a new version, the exact file of the schematic's latest version
- **THEN** the service returns the existing latest version and no version number is used up

### Requirement: Version metadata
The service SHALL expose for each version: version number, content hash, source format, size in bytes, dimensions, Minecraft data version, total block count, block counts per block type, upload time, and whether a thumbnail exists.

#### Scenario: Metadata after upload
- **WHEN** a player uploads a 10 x 5 x 8 schematic containing 120 oak planks and 40 glass blocks
- **THEN** the new version reports dimensions 10 x 5 x 8, 160 blocks, and counts of 120 oak planks and 40 glass

### Requirement: Owner-only access
The service SHALL let only a schematic's owner read, download, change or delete it. To anyone else, a schematic SHALL be indistinguishable from one that does not exist.

#### Scenario: Another player's schematic
- **WHEN** a player requests a schematic ID owned by a different account
- **THEN** the service responds exactly as it does for a non-existent schematic

### Requirement: Downloads through short-lived links
The service SHALL serve a version's file and thumbnail through download links that expire 5 minutes after they are issued and work without the caller's access token.

#### Scenario: Link used in time
- **WHEN** an owner requests a download link and fetches it within 5 minutes
- **THEN** the fetch returns the version's file

#### Scenario: Link expired
- **WHEN** a download link is fetched more than 5 minutes after it was issued
- **THEN** the fetch fails and returns no file content

### Requirement: Names, folders and tags
The service SHALL let an owner rename a schematic, place it in exactly one folder (or none), and attach any number of tags. These changes SHALL NOT create a new version.

#### Scenario: Organize a schematic
- **WHEN** an owner moves a schematic to folder `houses/medieval` and tags it `wip` and `spruce`
- **THEN** the schematic reports that folder and both tags, and its versions are unchanged

### Requirement: Library listing and search
The service SHALL list the caller's schematics, paged, newest change first, and SHALL filter by name text (case-insensitive substring), folder and tag. Each entry SHALL include the latest version's metadata.

#### Scenario: Filter by folder and text
- **WHEN** an owner lists folder `houses` with search text `tower`
- **THEN** only their schematics in folder `houses` whose name contains `tower` in any letter case are returned

### Requirement: Thumbnails
The service SHALL let an owner attach one PNG thumbnail of at most 256 x 256 pixels and 256 KiB to a version, once. A version without a thumbnail SHALL remain fully usable.

#### Scenario: Thumbnail attached
- **WHEN** an owner attaches a valid PNG to a version that has none
- **THEN** the version reports a thumbnail and a download link for it can be requested

#### Scenario: Thumbnail rejected
- **WHEN** an owner attaches a file that is not a PNG, exceeds the size limits, or targets a version that already has a thumbnail
- **THEN** the service rejects it and the version is unchanged

### Requirement: Storage quota
The service SHALL enforce a per-account storage quota counted as the sum of the sizes of all the account's stored versions and thumbnails, and SHALL report usage and quota to the owner.

#### Scenario: Upload over quota
- **WHEN** an upload would take the account's usage above its quota
- **THEN** the service rejects it as over quota and stores nothing

#### Scenario: Usage reported
- **WHEN** an owner asks for their storage usage
- **THEN** the service returns the bytes used and the quota

### Requirement: Deleting schematics and versions
The service SHALL let an owner delete a whole schematic, or any single version except the only remaining one. Deleted content SHALL stop counting toward the quota at once. New download links SHALL NOT be issued for it, and links issued before the deletion SHALL stop working no later than their expiry.

#### Scenario: Delete an old version
- **WHEN** an owner deletes version 2 of a schematic with versions 1 to 4
- **THEN** versions 1, 3 and 4 remain with their numbers, and version 2's size no longer counts toward the quota

#### Scenario: Delete the last version
- **WHEN** an owner tries to delete the only remaining version of a schematic
- **THEN** the service rejects it and tells the owner to delete the schematic instead
