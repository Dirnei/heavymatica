# Proposal

## Why

Folders are slash-separated paths (`village/houses`), but today they are only a label. The website shows a plain
list with the folder on each entry and cannot browse, filter or move anything, and it has no tags. The game's sidebar
is a flat list of exact paths, so `village` and `village/houses` are unrelated rows. With dozens of schematics, a
library you can't navigate like a file explorer is hard to use. The website also pages from the server, so it cannot
work out the folders or tags from what it has loaded. `web-folders` builds on `web-library` and comes before
`account-links`.

## What Changes

- **The library is a file explorer.**
  - The top level ("Library") shows the top-level folders as folder cards, then the schematics in no folder.
  - Opening a folder shows a breadcrumb back up, its subfolders as folder cards, then only the schematics directly
    in it.
  - "All schematics" is the only flat view of everything.
  - Searching or filtering by tag inside a folder shows flat results from that folder and every folder below it;
    on the top level it covers everything.
- **Folders stay implicit paths.** A folder exists while a schematic is in it or below it, and disappears when it
  is emptied. Paths stay case-sensitive, as today.
- **Cloud: folder list, tag list, subfolder filter.**
  - A new operation returns the caller's folders, each with the number of schematics directly in it and in it plus
    everything below, and the number of schematics in no folder.
  - Another returns the caller's tags with how many schematics carry each.
  - Listing schematics gains an opt-in to include subfolders of the `folder` filter, and a filter for schematics in
    no folder. Without the new parameters, listing behaves exactly as before, so older mods keep working.
- **Cloud: move many, rename a folder.**
  - A new operation moves up to 100 schematics into one folder (or out of every folder) in one request. Unknown IDs
    and other accounts' schematics are skipped, and the answer says how many moved.
  - Another renames a folder path, carrying every schematic in it and below it. Renaming onto an existing folder
    merges the two. A rename that would make any path longer than 200 characters is rejected as a whole.
- **Cloud: thumbnail by address.** Reading a version's thumbnail redirects to a short-lived image link, so the
  website can show thumbnails as plain images.
- **Website: cards, tree, tags.**
  - Schematics show as cards with their thumbnail (or a placeholder), name, version, dimensions and size, and the
    last change as a tooltip.
  - A folder tree beside the cards navigates the explorer. On narrow screens it folds into a drawer.
  - The sidebar lists the tags with counts. A chosen tag combines with the open folder and the search.
  - The open folder and tag are kept in the URL, so reload and back work.
- **Website: organizing.**
  - Cards can be selected (one, several, or all loaded), then "Move…" in their right-click menu opens a dialog with the folder tree and a
    field for a new path. Selected cards can also be dragged onto a folder card or a folder in the tree.
  - A folder can be renamed, which moves everything below it.
  - The details page can edit the schematic's tags.
- **Game: the same explorer.** The grid shows a card to go up, folder cards with counts, then the entries directly in
  the open folder, with the path above it. Local entries sit on the top level. "All schematics" shows everything
  flat; search and tags work as on the website. The sidebar keeps the tags. Moving a single schematic keeps the
  existing path prompt.
- **Not in this change:** rendering thumbnails for uploads that come without one (website uploads). That is
  `cloud-thumbnails`.

## Capabilities

### New Capabilities

None. The website's requirements join `web/player-ui`, which `web-library` introduces.

### Modified Capabilities

- `cloud/schematic-storage`: the folder rules gain the tree meaning, the folder and tag lists, moving many, renaming
  a folder and reading a thumbnail by address; listing gains the subfolder and no-folder filters.
- `web/player-ui` (added by `web-library`): the library becomes an explorer of cards with folders, tags, moving,
  folder rename, and tag editing on the details page.
- `client/schematic-library`: the library becomes the same explorer, with folder cards and a way back up.

## Impact

- **Contract** (`src/api/heavymatica-cloud.v1.yaml`): new `Folders` tag with `listFolders` and `renameFolder`, new
  `Tags` tag with `listTags`, `moveSchematics` and `getThumbnail` under the schematics. `listSchematics` gains
  `includeSubfolders` and `unfiled`. The changes only add things: older mods ignore them. The mod's regenerated
  client takes the two new query parameters by position, so its one `listSchematics` call passes `null` for them.
- **Cloud** (`Features/Schematics`): `Folders` slice (list, rename), `Tags` slice (list), a move operation in
  `Organize`, the thumbnail redirect in `Links`, subfolder filtering in `Listing`, and folder path rules in
  `FolderPaths`. No new index: `owner_folder` serves anchored prefix queries and `owner_tags` the tag grouping.
- **Website** (`src/web/player`): the library page becomes the explorer (tree, breadcrumb, folder cards, card grid,
  tag sidebar, selection, move dialog, folder rename); the details page gets tag editing. The URL carries
  `?folder=`, `?all=1` and `?tag=`.
- **Mod** (`ui/LibraryScreen`, `ui/CardGrid`, `ui/FilterSidebar`, `library/Library`): folder cards and the up card
  in the grid, the explorer state in the screen, the folder tree removed from the sidebar. No new cloud calls, since
  the game already loads the whole listing.
- **Depends on** `web-library` being archived first, since `web/player-ui` and the account-based
  `cloud/schematic-storage` texts come from it.
