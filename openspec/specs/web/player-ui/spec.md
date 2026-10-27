# web/player-ui Specification

## Purpose
The player website: GitHub sign-in and a browser library to upload, browse, organize and manage the account's schematics on the same origin as the cloud.

## Requirements

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
The library page SHALL show the account's schematics as cards, newest change first, each with its thumbnail or a placeholder, name, latest version number, dimensions and size, and the time of the last change as a tooltip. It SHALL filter by name text as the player types, and SHALL load further pages on request.

#### Scenario: Search
- **WHEN** a player with schematics `Tower`, `Watchtower` and `Bridge` in no folder types `tow` on the top level
- **THEN** the cards show `Tower` and `Watchtower` only

#### Scenario: Empty library
- **WHEN** a newly registered player opens the library
- **THEN** it says the library is empty and offers an upload

#### Scenario: More than one page
- **WHEN** the account has 120 schematics in one folder and the player asks for more after the first page
- **THEN** the cards continue with the next schematics, without repeating any

#### Scenario: Thumbnail or placeholder
- **WHEN** a folder holds one schematic with a thumbnail and one without
- **THEN** the first card shows the thumbnail and the second a placeholder

#### Scenario: Last change on hover
- **WHEN** a player points at a card
- **THEN** a tooltip shows when the schematic last changed

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

### Requirement: Library as a file explorer
The library SHALL open on the top level, which shows the top-level folders as folder cards and then the schematics in no folder. Opening a folder SHALL show a breadcrumb back to the top level, its subfolders as folder cards, and then only the schematics directly in it. Each folder card SHALL show the number of schematics in it and below it.

#### Scenario: Top level
- **WHEN** a player has schematics in `village`, `village/houses` and `castle`, and one in no folder
- **THEN** the top level shows folder cards `castle` and `village`, then the schematic in no folder

#### Scenario: Open a folder
- **WHEN** the player opens `village`
- **THEN** the breadcrumb reads Library › village, a folder card `houses` is shown, and then only the schematics directly in `village`

#### Scenario: Back up
- **WHEN** the player clicks Library in the breadcrumb of `village/houses`
- **THEN** the top level is shown again

### Requirement: All schematics view
The library SHALL offer an "All schematics" view that shows every schematic of the account as cards, without folder cards. It is the only view that lists schematics of several folders without a search or tag.

#### Scenario: Everything at once
- **WHEN** a player chooses "All schematics"
- **THEN** every schematic is listed, whatever its folder, and no folder cards are shown

### Requirement: Folder tree in the library
The library SHALL show the account's folders as a tree beside the cards, with "All schematics" and the top level above them. Every folder SHALL show its count including subfolders, and folders with subfolders SHALL expand and collapse. Choosing a folder in the tree SHALL open it. On narrow screens the tree SHALL fold away behind a control that opens it.

#### Scenario: Tree opens a folder
- **WHEN** a player chooses `village/houses` in the tree
- **THEN** the library opens `village/houses`, and the tree shows it chosen with `village` expanded

#### Scenario: Phone width
- **WHEN** the library is opened at phone width
- **THEN** the cards fill the page and the tree opens from a folder control

### Requirement: Search and tags inside a folder
Searching, or choosing a tag, SHALL list matching schematics from the open folder and every folder below it, flat and without folder cards, and SHALL say which folder the results come from. On the top level they SHALL cover every schematic. A tag and the search text SHALL combine.

#### Scenario: Search in a folder
- **WHEN** a player in `builds` types `tower`
- **THEN** the schematics in `builds` or below it whose name contains `tower` are shown, with a note that the results are from `builds`

#### Scenario: Tag on the top level
- **WHEN** a player on the top level chooses tag `wip`
- **THEN** every schematic tagged `wip` is shown, whatever its folder

### Requirement: Tag list in the library
The library SHALL list the account's tags with the number of schematics carrying each. Choosing a tag SHALL filter by it; choosing it again SHALL clear it.

#### Scenario: Choose and clear a tag
- **WHEN** a player chooses `spruce` and then chooses it again
- **THEN** the cards show only `spruce` schematics, and then the folder's content again

### Requirement: Library address
The open folder, the "All schematics" view and the chosen tag SHALL be kept in the page address, so reloading the page, sharing the address, or going back restores them.

#### Scenario: Back button
- **WHEN** a player opens `castle`, then `village`, then goes back in the browser
- **THEN** `castle` is open again, with `castle` chosen in the tree

### Requirement: Moving schematics in the browser
The library SHALL let the player select one or more schematic cards, or all loaded ones, and move them to a folder chosen from the tree or typed as a new path, or out of every folder. Dragging selected cards onto a folder card or a folder in the tree SHALL move them there. Folder cards, the tree and counts SHALL update after a move.

#### Scenario: Move a selection
- **WHEN** a player selects three cards and moves them to a new folder `harbor`
- **THEN** `harbor` appears in the tree with count 3

#### Scenario: Drag onto a folder card
- **WHEN** a player drags two selected cards onto the folder card `castle`
- **THEN** both move to `castle`, leave the open folder's cards, and the count of `castle` grows by 2

### Requirement: Renaming a folder in the browser
The library SHALL let the player rename a folder, which moves every schematic in it and below it. When the new path is an existing folder, the website SHALL say that the folders will be merged before it renames. If the renamed folder was open, the website SHALL keep showing it under its new path.

#### Scenario: Rename the open folder
- **WHEN** a player who has `castle` open renames it to `builds/castle`
- **THEN** the breadcrumb reads Library › builds › castle, and the address names `builds/castle`

#### Scenario: Merge warning
- **WHEN** a player renames `drafts` to the existing folder `wip`
- **THEN** the website says the two folders will be merged, and renames only after the player confirms

### Requirement: Editing tags in the browser
The details page SHALL let the player add and remove the schematic's tags, with the same limits as the cloud service (at most 20 tags of 1 to 50 characters). Changing tags SHALL NOT create a version.

#### Scenario: Add and remove a tag
- **WHEN** a player adds `spruce` to a schematic tagged `wip` and removes `wip`
- **THEN** the schematic is tagged `spruce` only, and its versions are unchanged

#### Scenario: Duplicate tag
- **WHEN** a player adds a tag the schematic already has
- **THEN** the tags stay unchanged

### Requirement: Context menus in the library
Right-clicking a schematic card SHALL open a menu to open, rename, move or delete it; with several cards selected, the menu SHALL move or unselect all of them. Right-clicking a folder card or a folder in the tree SHALL open a menu to open it or to rename or move it. The menu SHALL also open from the keyboard (menu key or Shift+F10) and close with Escape or a click elsewhere. Deleting SHALL ask for confirmation.

#### Scenario: Rename from the menu
- **WHEN** a player right-clicks the card `Keep` and chooses Rename
- **THEN** a dialog asks for the new name, and the card shows it after saving

#### Scenario: Move a selection from the menu
- **WHEN** a player selects three cards, right-clicks one of them and chooses to move them
- **THEN** the move dialog moves all three

#### Scenario: Folder from the menu
- **WHEN** a player right-clicks the folder card `drafts` and renames it to `archive/drafts`
- **THEN** `drafts` appears under `archive` in the tree

### Requirement: Uploading into the open folder
The library SHALL upload new schematics into the open folder (the top level in "All schematics") from an "Upload here" card after the last card, from files dropped anywhere on the cards, or from the right-click menu of the empty area. Files dropped onto a folder card or a folder in the tree, or "Upload into this folder" in a folder's menu, SHALL go into that folder. The library SHALL have no separate upload button.

#### Scenario: Drop files into the open folder
- **WHEN** a player with `builds/medieval` open drops two schematic files onto the cards
- **THEN** both are uploaded into `builds/medieval` and appear among its cards

#### Scenario: Drop onto a folder card
- **WHEN** a player drops a file onto the folder card `castle`
- **THEN** it is uploaded into `castle`

### Requirement: Upload progress in the library
Several files SHALL upload one after another, with each file's progress and result shown. A file over 16 MiB SHALL be reported without being sent. After the uploads, the cards, folders and storage use SHALL update.

#### Scenario: A file is rejected
- **WHEN** one of three dropped files is larger than 16 MiB
- **THEN** that file is reported as too large without being sent, and the other two are uploaded
