# client/schematic-library Specification

## Purpose

Gives the player one simple in-game place to browse, find, inspect and organize all their schematics, cloud and local alike, and keeps downloaded schematics available when the cloud is not.

## Requirements

### Requirement: Library opens from anywhere in game
The client SHALL open the library screen with a configurable key, by default `H`, whenever the player is in a world and no other screen is open.

#### Scenario: Open with key
- **WHEN** a player in a world presses the library key
- **THEN** the library screen opens

### Requirement: One library for cloud and local entries
The library SHALL show the player's cloud schematics and their local-only entries in the same view, marking each entry as cloud or local.

#### Scenario: Mixed library
- **WHEN** a signed-in player has 5 cloud schematics and 2 local-only entries
- **THEN** the library shows all 7, with the 2 local-only entries marked as local

### Requirement: Entry cards
Each entry SHALL show its name, thumbnail (or a placeholder when there is none), dimensions and block count.

#### Scenario: Entry without thumbnail
- **WHEN** an entry's latest version has no thumbnail
- **THEN** the card shows a placeholder image and still shows name, dimensions and block count

### Requirement: Search, folders and tags
Searching by name or choosing a tag SHALL show matching entries from the open folder and below it, without folder cards. Text and tag filters SHALL combine. An "All schematics" view SHALL show every entry without folder cards.

#### Scenario: Combined filter
- **WHEN** a player has `houses` open, selects tag `spruce`, and types `tow`
- **THEN** only entries in `houses` or below it, tagged `spruce`, whose name contains `tow` in any letter case are shown

#### Scenario: All schematics
- **WHEN** a player turns on "All schematics"
- **THEN** every entry is shown, whatever its folder, and no folder or up cards are shown

### Requirement: Folder explorer
The library SHALL browse folders like a file explorer. The top level SHALL show the top-level folders as folder cards, then the entries in no folder, including local entries. An open folder SHALL show a card to go up, its subfolders as folder cards with their counts including subfolders, then only the entries directly in it, with its path shown.

#### Scenario: Open a folder
- **WHEN** a player has entries in `castle` and `castle/towers` and opens `castle`
- **THEN** the grid shows the up card, a folder card `towers`, then the entries directly in `castle`

#### Scenario: Go up
- **WHEN** a player in `castle/towers` clicks the up card
- **THEN** `castle` is open

### Requirement: Entry details
Selecting an entry SHALL show its details: all versions with number and upload time, and for the chosen version its metadata and block counts sorted from most to least common.

#### Scenario: Older version
- **WHEN** a player opens an entry with 3 versions and picks version 1
- **THEN** the details show version 1's dimensions and block counts

### Requirement: Organize from the game
The library SHALL let the player rename, move to a folder, tag, and delete cloud schematics and single versions, with the same rules as the cloud service. Deleting SHALL ask for confirmation first.

#### Scenario: Delete needs confirmation
- **WHEN** a player chooses delete on a schematic
- **THEN** a confirmation naming the schematic appears, and nothing is deleted unless the player confirms

#### Scenario: Rename while signed out
- **WHEN** a signed-out player tries to rename a cloud schematic
- **THEN** the action is unavailable and the library says signing in is needed

### Requirement: Local entries can be renamed and deleted
The library SHALL let the player rename and delete local-only entries, also while signed out. Deleting SHALL ask for confirmation, SHALL remove the entry and its copy of the file, and SHALL NOT touch the file it was imported from. Moving, tagging and deleting versions SHALL remain cloud-only.

#### Scenario: Delete a local entry
- **WHEN** a signed-out player deletes a local entry and confirms
- **THEN** the entry and its stored copy are gone, and the original file in `schematics/` still exists

#### Scenario: Rename a local entry
- **WHEN** a player renames a local entry to `Starter Hut`
- **THEN** the library shows the entry as `Starter Hut`, also after a restart

### Requirement: The game never freezes on the library
Loading listings, details, thumbnails and downloads SHALL happen without blocking the game. While loading, the library SHALL show a loading state, and failures SHALL show a retryable error.

#### Scenario: Slow network
- **WHEN** the cloud takes 10 seconds to answer a listing request
- **THEN** the game keeps rendering and accepting input, and the library shows a loading state until the answer arrives

### Requirement: Read-through local cache
The client SHALL keep every downloaded version file and thumbnail in a local cache keyed by content hash, and SHALL use the cached copy instead of downloading again.

#### Scenario: Second use
- **WHEN** a player uses a version that was downloaded before
- **THEN** the client takes it from the cache and makes no download

### Requirement: Cache integrity
The client SHALL verify a version file's SHA-256 against its content hash after download and when reading it from the cache, and SHALL discard and download again any file that does not match.

#### Scenario: Corrupted cache file
- **WHEN** a cached version file has been changed on disk
- **THEN** the client discards it and downloads the version again when it is next used

### Requirement: Cache size limit
The cache SHALL stay within a configurable size, by default 1 GiB, by evicting the least recently used entries. Local-only entries SHALL never be evicted.

#### Scenario: Cache full
- **WHEN** a download would take the cache above its limit
- **THEN** the least recently used cached versions are removed until the new file fits, and no local-only entry is removed

### Requirement: Offline library
When the cloud is unreachable or the session has expired, the library SHALL still show the last known listing of that Minecraft account's cloud schematics, marked as offline. Cached versions SHALL remain usable, and versions not in the cache SHALL be shown as unavailable offline.

#### Scenario: Cloud down
- **WHEN** a signed-in player opens the library while the cloud is unreachable
- **THEN** the library shows the last known listing with an offline marker, cached versions can be opened, and uncached versions say they need a connection

### Requirement: Place a version from the library
The entry details SHALL offer a Place action for the chosen version. Choosing it SHALL load that version (from the cache or by downloading it), close the library and start a new placement of it. A version that is unavailable offline SHALL have Place disabled with the reason shown.

#### Scenario: Place a cached version
- **WHEN** a player picks version 2 of `Starter Hut` and chooses Place
- **THEN** the library closes and a placement of version 2 starts

#### Scenario: Offline, not cached
- **WHEN** the cloud is unreachable and the chosen version is not in the cache
- **THEN** Place is disabled and says the version needs a connection

#### Scenario: Local entry
- **WHEN** a player chooses Place on a local-only entry while signed out
- **THEN** a placement of that local entry starts
