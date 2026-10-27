# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
The library SHALL upload new schematics into the open folder (the top level in "All schematics"): from an "Upload here" card among the cards, by dropping files anywhere on the cards, or from the right-click menu of the empty area. Dropping files onto a folder card or a folder in the tree, or choosing "Upload into this folder" from a folder's menu, SHALL upload into that folder. Several files SHALL upload one after another with their progress and result shown, and the cards, folders and storage use SHALL update afterwards. The library SHALL have no separate upload button.

#### Scenario: Drop files into the open folder
- **WHEN** a player with `builds/medieval` open drops two schematic files onto the cards
- **THEN** both are uploaded into `builds/medieval` and appear among its cards

#### Scenario: Drop onto a folder card
- **WHEN** a player drops a file onto the folder card `castle`
- **THEN** it is uploaded into `castle`

#### Scenario: A file is rejected
- **WHEN** one of three dropped files is larger than 16 MiB
- **THEN** that file is reported as too large without being sent, and the other two are uploaded
