# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
