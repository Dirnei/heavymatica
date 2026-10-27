# cloud/schematic-storage Specification

## Purpose

Stores each player's schematics in the Heavymatica Cloud as immutable versions, lets them organize and find them, and serves them back to the game wherever the player plays.

## Requirements

### Requirement: Upload creates a schematic or a new version
The service SHALL accept a schematic file upload either as a new schematic or as a new version of an existing schematic that the caller's account owns. A new schematic SHALL be owned by the caller's account, SHALL start at version 1, and each new version SHALL take the next version number.

#### Scenario: New schematic
- **WHEN** a signed-in player uploads a valid file without naming an existing schematic
- **THEN** a new schematic owned by that player's account exists with version 1, named after the given name or, if none is given, the file name

#### Scenario: New version
- **WHEN** a player uploads a valid file for a schematic their account owns whose latest version is 3
- **THEN** the schematic has version 4 and versions 1 to 3 are unchanged

#### Scenario: Upload from the website
- **WHEN** a player signed in on the website uploads a valid file as a new schematic
- **THEN** the new schematic is owned by the account of their website session, exactly as for an upload from the game

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
The service SHALL let only the account that owns a schematic read, download, change or delete it, through any identity linked to that account. To anyone else, a schematic SHALL be indistinguishable from one that does not exist.

#### Scenario: Another player's schematic
- **WHEN** a player requests a schematic ID owned by a different account
- **THEN** the service responds exactly as it does for a non-existent schematic

#### Scenario: Separate GitHub and Minecraft accounts
- **WHEN** a player signed in on the website with GitHub requests a schematic they uploaded from the game, and the GitHub and Minecraft identities belong to different accounts
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
The service SHALL let an owner rename a schematic, place it in exactly one folder (or none), and attach any number of tags. These changes SHALL NOT create a new version. A folder is a slash-separated path of at most 200 characters, compared case-sensitively, and stored without leading, trailing or repeated slashes. A folder includes the folders below it: `houses` is the parent of `houses/medieval`. A folder exists only while a schematic is in it or below it.

#### Scenario: Organize a schematic
- **WHEN** an owner moves a schematic to folder `houses/medieval` and tags it `wip` and `spruce`
- **THEN** the schematic reports that folder and both tags, and its versions are unchanged

#### Scenario: Path is normalized
- **WHEN** an owner moves a schematic to folder `/houses//medieval/`
- **THEN** the schematic reports folder `houses/medieval`

#### Scenario: Emptied folder disappears
- **WHEN** an owner moves the only schematic in `drafts` to another folder
- **THEN** `drafts` no longer appears in the owner's folder list

### Requirement: Library listing and search
The service SHALL list the schematics of the caller's account, paged, newest change first, and SHALL filter by name text (case-insensitive substring), folder and tag. The folder filter SHALL match that exact folder unless the caller asks to include subfolders. A separate filter SHALL list only schematics in no folder. Each entry SHALL include the latest version's metadata.

#### Scenario: Filter by folder and text
- **WHEN** an owner lists folder `houses` with search text `tower`
- **THEN** only their account's schematics in folder `houses` whose name contains `tower` in any letter case are returned

#### Scenario: Include subfolders
- **WHEN** an owner has schematics in `houses`, `houses/medieval` and `housesmall`, and lists folder `houses` including subfolders
- **THEN** the schematics in `houses` and `houses/medieval` are returned, and the one in `housesmall` is not

#### Scenario: Exact folder by default
- **WHEN** an older client lists folder `houses` without asking for subfolders
- **THEN** only schematics directly in `houses` are returned, as before

#### Scenario: No folder
- **WHEN** an owner lists the schematics in no folder
- **THEN** only their schematics without a folder are returned

### Requirement: Thumbnails
The service SHALL let an owner attach one PNG thumbnail of at most 256 x 256 pixels and 256 KiB to a version, once. A version without a thumbnail SHALL remain fully usable.

#### Scenario: Thumbnail attached
- **WHEN** an owner attaches a valid PNG to a version that has none
- **THEN** the version reports a thumbnail and a download link for it can be requested

#### Scenario: Thumbnail rejected
- **WHEN** an owner attaches a file that is not a PNG, exceeds the size limits, or targets a version that already has a thumbnail
- **THEN** the service rejects it and the version is unchanged

### Requirement: Storage quota
The service SHALL enforce a storage quota per account, an entitlement of the account rather than of any identity. Usage SHALL be the sum of the sizes of all the account's stored versions and thumbnails. The service SHALL report usage and quota to the owner.

#### Scenario: Upload over quota
- **WHEN** an upload would take the account's usage above its quota
- **THEN** the service rejects it as over quota and stores nothing

#### Scenario: Usage reported
- **WHEN** an owner asks for their storage usage
- **THEN** the service returns the bytes used and the quota

#### Scenario: Quota kept through the migration
- **WHEN** a Minecraft-keyed account with a quota of 100 MiB and 40 MiB used is migrated
- **THEN** the migrated account reports 40 MiB used of 100 MiB

### Requirement: Deleting schematics and versions
The service SHALL let an owner delete a whole schematic, or any single version except the only remaining one. Deleted content SHALL stop counting toward the quota at once. New download links SHALL NOT be issued for it, and links issued before the deletion SHALL stop working no later than their expiry.

#### Scenario: Delete an old version
- **WHEN** an owner deletes version 2 of a schematic with versions 1 to 4
- **THEN** versions 1, 3 and 4 remain with their numbers, and version 2's size no longer counts toward the quota

#### Scenario: Delete the last version
- **WHEN** an owner tries to delete the only remaining version of a schematic
- **THEN** the service rejects it and tells the owner to delete the schematic instead

### Requirement: Folder list
The service SHALL list every folder of the caller's account, including parent folders that hold no schematic directly. For each folder it SHALL give the number of schematics directly in it and the number in it and below it. It SHALL also give the number of the account's schematics in no folder.

#### Scenario: Parents are listed
- **WHEN** an owner's only schematics are two in `castle/towers` and one in `castle`
- **THEN** the folder list holds `castle` with 1 direct and 3 in total, and `castle/towers` with 2 direct and 2 in total

#### Scenario: Other accounts are invisible
- **WHEN** another account has a schematic in folder `secret`
- **THEN** `secret` does not appear in the owner's folder list

### Requirement: Moving several schematics
The service SHALL move up to 100 of the caller's schematics into one folder, or out of every folder, in one request, without creating versions. IDs that are unknown or belong to another account SHALL be skipped without revealing which. The answer SHALL give the number of schematics moved.

#### Scenario: Move three
- **WHEN** an owner moves three of their schematics to `village`
- **THEN** all three report folder `village` and the answer says 3 were moved

#### Scenario: Foreign ID skipped
- **WHEN** an owner moves two of their own schematics and one of another account to `village`
- **THEN** their two schematics move, the other account's schematic is unchanged, and the answer says 2 were moved

#### Scenario: Too many
- **WHEN** an owner sends 101 IDs
- **THEN** the request is rejected as invalid and nothing moves

### Requirement: Renaming a folder
The service SHALL rename a folder of the caller's account by replacing that path at the start of every schematic's folder that is the folder or below it. Renaming onto an existing folder SHALL merge the two. If any resulting path would be longer than 200 characters, the whole rename SHALL be rejected and nothing SHALL change. The answer SHALL give the number of schematics moved.

#### Scenario: Rename with subfolders
- **WHEN** an owner renames `castle` to `builds/castle` while schematics are in `castle`, `castle/towers` and `castles`
- **THEN** they are in `builds/castle`, `builds/castle/towers` and `castles`

#### Scenario: Merge
- **WHEN** an owner renames `drafts` to `wip` while both folders hold schematics
- **THEN** every schematic of both is in `wip`, and `drafts` is gone from the folder list

#### Scenario: Unknown folder
- **WHEN** an owner renames a folder that holds none of their schematics
- **THEN** the service answers not found and nothing changes

#### Scenario: Path too long
- **WHEN** a rename would make one schematic's folder 201 characters long
- **THEN** the rename is rejected as invalid and no schematic moves

### Requirement: Tag list
The service SHALL list every tag on the caller's account's schematics, with the number of schematics carrying each, sorted by tag.

#### Scenario: Counts per tag
- **WHEN** an owner has two schematics tagged `wip` and one tagged both `wip` and `spruce`
- **THEN** the tag list holds `spruce` with 1 and `wip` with 3

#### Scenario: Other accounts' tags are invisible
- **WHEN** another account tags a schematic `secret`
- **THEN** `secret` does not appear in the owner's tag list

### Requirement: Thumbnail by address
Reading a version's thumbnail SHALL redirect the caller to a short-lived link to the image, so it can be shown as a plain image with the website session. A version without a thumbnail, and any version of another account's schematic, SHALL answer not found.

#### Scenario: Thumbnail shown
- **WHEN** the website requests the thumbnail of a version that has one
- **THEN** it is redirected to a link that returns the PNG

#### Scenario: No thumbnail
- **WHEN** the thumbnail of a version without one is requested
- **THEN** the service answers not found

### Requirement: Upload into a folder
Uploading a new schematic SHALL accept an optional folder path, normalized like any other, and SHALL create the schematic directly in that folder. A path longer than 200 characters SHALL be rejected as invalid and store nothing.

#### Scenario: Upload into a folder
- **WHEN** an owner uploads a new schematic with folder `/builds//medieval/`
- **THEN** the new schematic reports folder `builds/medieval`

#### Scenario: Folder path too long
- **WHEN** an owner uploads with a folder path of 201 characters
- **THEN** the upload is rejected as invalid and no schematic is created
