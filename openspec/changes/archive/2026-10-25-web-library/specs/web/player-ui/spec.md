# Spec Delta

## ADDED Requirements

### Requirement: Website on the cloud's origin
The player website SHALL be served by the cloud on the same origin as its API. Its pages SHALL hold no player data of their own: everything shown SHALL be loaded through the API with the website session. A page that needs a signed-in player SHALL send a signed-out visitor to the landing page and back after sign-in.

#### Scenario: Signed-out visitor
- **WHEN** a signed-out visitor opens the library page
- **THEN** the website shows the landing page and, after a sign-in, returns to the library

#### Scenario: Session ended elsewhere
- **WHEN** the session of an open library page ends and the player clicks a schematic
- **THEN** the website shows the landing page saying the player was signed out

### Requirement: Landing page with GitHub register and sign-in
The landing page SHALL offer "Register with GitHub" and "Sign in with GitHub" and no other sign-in. It SHALL say that the website's library is not connected to the game yet. After a failed or cancelled sign-in it SHALL say why, and when the GitHub identity has no account it SHALL offer to register.

#### Scenario: Only GitHub
- **WHEN** a signed-out visitor opens the website
- **THEN** the landing page offers registering and signing in with GitHub, and no Microsoft, Google or Minecraft sign-in

#### Scenario: No account yet
- **WHEN** a visitor signs in with a GitHub identity that has no account
- **THEN** the landing page says there is no account for it and offers "Register with GitHub"

### Requirement: Library list with search
The library page SHALL list the account's schematics, newest change first, with name, folder, tags, latest version number, dimensions, size and time of the last change. It SHALL filter by name text as the player types, and SHALL load further pages on request.

#### Scenario: Search
- **WHEN** a player with schematics `Tower`, `Watchtower` and `Bridge` types `tow`
- **THEN** the list shows `Tower` and `Watchtower` only

#### Scenario: Empty library
- **WHEN** a newly registered player opens the library
- **THEN** it says the library is empty and offers an upload

#### Scenario: More than one page
- **WHEN** the account has 120 schematics and the player asks for more after the first page
- **THEN** the list continues with the next schematics, without repeating any

### Requirement: Schematic details
The details page SHALL show a schematic's name, folder, tags and every version with its number, upload time, format, size, dimensions, Minecraft data version and total block count. For a chosen version it SHALL show the block counts per block type, largest first, the thumbnail when there is one, and a download of the file.

#### Scenario: Version details
- **WHEN** a player opens a schematic whose version 2 is 10 x 5 x 8 with 120 oak planks and 40 glass and has a thumbnail
- **THEN** the page shows version 2 with dimensions 10 x 5 x 8, 160 blocks, oak planks 120 above glass 40, and the thumbnail

#### Scenario: No thumbnail
- **WHEN** the chosen version has no thumbnail
- **THEN** the page shows a placeholder instead and everything else as usual

### Requirement: Upload from the browser
The website SHALL upload a chosen file as a new schematic, or as a new version from a schematic's details page, with the same formats and limits as uploads from the game. It SHALL show progress, and SHALL explain a rejection by its reason, such as an unsupported format, a file that is too large or the quota being exceeded.

#### Scenario: New schematic
- **WHEN** a player uploads a valid `castle.litematic`
- **THEN** the library lists a new schematic `castle` at version 1

#### Scenario: New version
- **WHEN** a player uploads a valid file from the details page of a schematic at version 3
- **THEN** the details page shows version 4

#### Scenario: Same file again
- **WHEN** a player uploads the exact file of the latest version as a new version
- **THEN** the website says the file is unchanged and no version is added

#### Scenario: Over quota
- **WHEN** an upload would exceed the account's quota
- **THEN** the website says the storage is full, shows the usage and nothing is stored

#### Scenario: Unsupported file
- **WHEN** a player uploads a PNG image as a schematic
- **THEN** the website says the format is not supported and the library is unchanged

### Requirement: Rename and delete in the browser
The details page SHALL let the player rename the schematic, delete it as a whole, and delete any single version except the only one. Deleting SHALL ask for confirmation, naming the schematic or version.

#### Scenario: Rename
- **WHEN** a player renames `castle` to `Castle on the hill`
- **THEN** the details page and the library show the new name and the versions are unchanged

#### Scenario: Delete a version
- **WHEN** a player deletes version 2 of a schematic with versions 1 to 3 and confirms
- **THEN** versions 1 and 3 remain and the storage usage drops by version 2's size

#### Scenario: Only version
- **WHEN** a schematic has a single version
- **THEN** the website offers no version delete for it, only deleting the schematic

#### Scenario: Delete cancelled
- **WHEN** a player starts deleting a schematic and cancels the confirmation
- **THEN** nothing is deleted

### Requirement: Storage usage shown
While signed in, the website SHALL show the account's storage used against its quota, and SHALL update it after uploads and deletions.

#### Scenario: Usage after upload
- **WHEN** a player using 12 MiB of 100 MiB uploads a 2 MiB file
- **THEN** the website shows 14 MiB of 100 MiB used

### Requirement: Sign-out on the website
The website SHALL let a signed-in player sign out from every page. After signing out it SHALL show the landing page.

#### Scenario: Sign out
- **WHEN** a signed-in player chooses "Sign out"
- **THEN** the landing page is shown, and opening the library again asks the player to sign in
