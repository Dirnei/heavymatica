# Design

## Context

For motivation see proposal.md, Why. For behaviour see the deltas for `cloud/schematic-storage`, `web/player-ui` and
`client/schematic-library`. This change starts after `web-library` is archived.

What exists today:
- **Cloud:** `SchematicDocument.Folder` is a nullable string, normalized by `Listing/Folders.Normalize` (split on
  `/`, trim, drop empty parts). `ListSchematics` filters `Folder` with equality and pages by an opaque
  `(updatedAt, id)` cursor, 50 by default and 100 at most. `OrganizeSchematic` sets name, folder and tags one
  schematic at a time and always bumps `UpdatedAt`. The indexes are `owner_updatedAt`, `owner_folder` and
  `owner_tags`.
- **Website:** `Library.svelte` loads pages through the generated `openapi-fetch` client, searches with a 250 ms
  debounce and shows a plain list with `item.folder` as a label. `Details.svelte` renames through
  `organizeSchematic` and shows thumbnails through `createLinks` (a POST per version). The client adds
  `X-Heavymatica-Request: 1` to every request, which the cloud requires for unsafe cookie requests.
- **Thumbnails** exist only when the uploader attached one. The game renders and attaches them for its uploads; the
  website does not (that is `cloud-thumbnails`).
- **Game:** `Library.refresh` fetches the whole cloud listing (pages of 100) and merges local entries, which have no
  folder. `Library.folders()` returns the sorted set of exact paths, `LibraryEntry.matches` compares the folder with
  `equals`, `FilterSidebar` draws headed rows of folders and tags, and `CardGrid` draws entry cards with thumbnails
  from `ThumbnailTextures`. `pointOf(label)` and `pointOf(index)` let dev scripts click rows and cards. Moving uses
  `TextPromptScreen` with an `OrganizeRequest`.

## Goals / Non-Goals

**Goals:**
- Older mods see exactly today's behaviour: no changed defaults, no new required fields.
- Folder lists, tag lists, moves and renames cost one query each, not one per schematic.
- One rule for "is in folder X or below" shared by the cloud, the website and the game.
- The website and the game browse the same way (D7, D8).

**Non-Goals:**
- Empty folders that exist on their own. Folders stay implicit (see D1).
- Moving several schematics, renaming folders or dragging in the game. The game only gets the explorer.
- Folders for local-only game entries. They have no folder and sit on the top level.
- Rendering thumbnails that the uploader did not attach (`cloud-thumbnails`).
- Sorting by anything but newest change first.
- Undo for moves and renames.

## Decisions

### D1. Folders stay implicit paths

A folder is the `Folder` string of the schematics in it. The tree is derived from the set of paths: every prefix
of a path at a `/` is a parent folder. Nothing is stored for a folder itself.

Alternative: a `folders` collection with IDs and parent links. That would allow empty folders and cheap renames,
but it needs a migration, consistency between two collections, and a decision for every existing path. The
game's model (a path per entry) would no longer match. Not worth it while folders carry no data of their own.

### D2. "In or below" as an anchored match

A schematic is in folder `F` or below it when `Folder == F` or `Folder` starts with `F + "/"`. The `/` matters:
`houses` must not match `housesmall`. In Mongo this is one `$or` of an equality and an anchored, escaped regex
`^<escaped F>/`, which uses the `owner_folder` index as a prefix range. The website and the game use the same
two-part check in their own code (`isInFolder` and `LibraryEntry.inFolder`).

### D3. Contract

All additions, under existing conventions (one tag per operation, default Problem response, `/v1/` paths):

- `listSchematics` gains `includeSubfolders` (boolean, default `false`) and `unfiled` (boolean, default `false`).
  `unfiled=true` together with `folder` is `invalid_request`.
- New tag `Folders`:
  - `listFolders`: `GET /v1/folders` → `FolderList { folders: [FolderEntry { path, directCount, totalCount }],
    unfiledCount }`. Folders are sorted by path (ordinal).
  - `renameFolder`: `POST /v1/folders/rename` with `{ from, to }` → `{ moved }`. An empty `to` is
    `invalid_request`; taking schematics out of every folder is a move.
- New tag `Tags`: `listTags`: `GET /v1/tags` → `TagList { tags: [TagEntry { tag, count }] }`, sorted by tag
  (ordinal).
- `moveSchematics` (tag `Schematics`): `POST /v1/schematics/move` with `{ schematicIds (1..100, unique), folder }`
  → `{ moved }`. `folder: ""` moves out of every folder, the same convention as `OrganizeRequest`.
- `getThumbnail` (tag `Schematics`): `GET /v1/schematics/{id}/versions/{n}/thumbnail`, next to the existing PUT
  → `302` to the thumbnail's pre-signed link, or `404 not_found` (D9).

POST instead of PATCH on a collection keeps the operations explicit and leaves `PATCH /v1/schematics/{id}` as
the single-schematic form. Both new POSTs fall under the existing request check for cookie sessions.

### D4. Folder list by aggregation

`listFolders` runs one aggregation on the owner's schematics: `$group` by `Folder` with a count. The service then
expands each path into its prefixes, adds the direct counts up into `totalCount`, and returns the sorted list. The
null group becomes `unfiledCount`. The number of distinct paths is bounded by the number of schematics, and quota
limits those, so expanding in memory is fine.

Alternative: maintaining counters on organize, upload and delete. More writes on every path, more ways to drift,
for a list that is cheap to compute.

### D5. Moving and renaming with UpdateMany

- **Move:** one `UpdateMany` with `Owner == caller && Id in ids`, setting `Folder` and `UpdatedAt`. `moved` is the
  modified count. Unknown and foreign IDs simply don't match, which reveals nothing.
- **Rename:**
  1. Normalize `from` and `to`. An empty result or `to` longer than 200 characters is `invalid_request`.
  2. Read the distinct paths in or below `from`. None is `404 not_found`; `from == to` returns `moved: 0`.
  3. The longest of them, minus `from`'s length, plus `to`'s length, must stay within 200. Otherwise answer
     `invalid_request` and change nothing.
  4. Run one `UpdateMany` with an update pipeline: `Folder = to + substrCP(Folder, codePoints(from))` for the
     matched schematics (D2's filter), and set `UpdatedAt`.
- Renaming `a` to `a/b` is allowed; the string rewrite turns `a/x` into `a/b/x`.
- `UpdatedAt` is bumped, as `organizeSchematic` does today, so moved schematics come first in the list. That keeps
  one meaning for "last change" and lets the game's cursor-free full refresh pick them up unchanged.

Step 2 and step 4 are two queries. A concurrent organize between them can, at worst, store a path over 200
characters, and only by racing the owner's own requests. The service accepts that instead of a transaction.

Alternative: one request per schematic from the website. 60 sequential PATCHes for a folder rename are slow,
cannot fail as a whole, and would count against rate limits.

### D6. Shared path helper and slices

`Listing/Folders` becomes `Schematics/FolderPaths` with `Normalize`, `InOrBelow` (the D2 filter builder) and
`Prefixes`. The new slices live in `Features/Schematics/Folders/` (`ListFolders`, `RenameFolder`) and
`Features/Schematics/Tags/` (`ListTags`). `MoveSchematics` lives next to `OrganizeSchematic` in `Organize/`, and the
thumbnail redirect next to `CreateLinks` in `Links/`. The rename removes the name clash between the old `Folders`
class and the new namespace.

### D7. Website: the explorer

- **Views and the URL:**
  - `/library/` is the top level, `?folder=village%2Fhouses` an open folder, `?all=1` "All schematics".
  - `&tag=<tag>` adds the chosen tag. Changing the view pushes a history entry; `popstate` restores it.
  - The search text stays out of the URL, as today.
- **What a view loads:**

  | View | Folder cards | Schematics (`listSchematics`) |
  |---|---|---|
  | Top level, browsing | top-level folders | `unfiled=true` |
  | Folder `F`, browsing | children of `F` | `folder=F` (exact) |
  | Top level, search or tag | none | no folder filter, `q`/`tag` |
  | Folder `F`, search or tag | none | `folder=F&includeSubfolders=true`, `q`/`tag` |
  | All schematics | none | no folder filter, `q`/`tag` |

  Folder cards come from the `listFolders` result already loaded for the tree, so browsing needs no extra call.
- **Layout:** a two-column grid from 48rem up: a sticky sidebar with the tree ("All schematics", "Library", then
  the folders) and the tags below it, and the content with breadcrumb, folder cards and schematic cards. Below
  48rem a "Folders & tags" button opens the sidebar in a `<dialog>`.
- **Cards:** a responsive grid (`repeat(auto-fill, minmax(10rem, 1fr))`).
  - Schematic cards show the thumbnail as `<img loading="lazy" src="/v1/schematics/{id}/versions/{n}/thumbnail">`
    when `latest.hasThumbnail`, otherwise a placeholder. Below it: name, version, dimensions and size. The tooltip
    holds the last change.
  - Folder cards show a folder icon, the name and the total count. They open the folder on click and accept drops.
- **Tree:** `FolderTree.svelte`, built from `listFolders`. Expand state is kept per path in `localStorage` (wrapped
  in try/catch); the open folder's parents are always expanded. Loaded once per page and again after every move or
  rename.
- **Tags:** `TagList.svelte` from `listTags`, chips with counts; choosing the chosen tag again clears it.
- **Selection:** a checkbox on each schematic card, plus "Select all loaded" and the count; the selection is moved
  through the context menu or by dragging. The selection is kept across "Load more" and dropped when the view or search changes.
- **Move dialog:** `MoveDialog.svelte` shows the tree as radio choices, "No folder", and a path field. It sends one
  `moveSchematics` per 100 IDs.
- **Drag and drop:** native HTML drag events. Dragging a checked card carries the whole selection, an unchecked card
  carries itself. Folder cards and tree rows highlight while a drag is over them. Touch devices use the dialog.
- **Context menus instead of action buttons:** right-clicking a schematic card offers Open, Rename…, Move… and
  Delete… (or, on a selection of several, moving or unselecting them); right-clicking a folder card or tree row offers
  Open and Rename or move…. `ContextMenu.svelte` is a fixed-position `role="menu"` list with arrow-key navigation that
  closes on Escape, outside clicks, scrolling and blur. Opened from the keyboard, it sits at the focused element.
- **Folder rename:** "Rename or move…" from a folder's menu opens a dialog with the full path. If the new path is already in the folder list, the dialog shows the merge
  warning and the confirm button reads "Merge". After renaming the open folder, the URL is replaced, not pushed.
- **Tag editing:** the details page shows the tags as chips with a remove button and an "Add tag" field (Enter
  adds). Each change sends the full tag list through `organizeSchematic`; the cloud's trimming and de-duplication
  apply.
- **Shared logic** in `lib/folders.ts` and `lib/navigation.ts` (pure, Vitest-tested): `isInFolder`, `buildTree`,
  `childFolders`, `parentsOf`, `chunk`, the merge check, and the view to URL and query mapping.

### D8. Game: the same explorer

- `LibraryScreen` holds the view: the open folder (`""` for the top level), "All schematics" on or off, the tag and
  the search text.
- `Library.view(folder, all, text, tag)` returns the grid items, by the same table as D7:
  - browsing: an up card (unless on the top level), the child folders with their total counts, then the entries
    whose folder is exactly the open one (none on the top level, which includes local entries);
  - search or tag: entries in the open folder or below it (everything on the top level), no folder cards;
  - all: every entry, no folder cards.
- `CardGrid` draws three kinds of cards: up (`..`), folder (a folder glyph, name, count) and entry, as today.
  Clicking a folder card opens it, the up card opens the parent.
- `FilterSidebar` loses the folder rows. It shows an "All schematics" row that toggles the flat view, then the tags.
- The screen draws the path (`Library › builds › medieval`) above the grid.
- Dev scripts: `clickCard=<index>` counts up and folder cards too, and a new `openFolder=<path>` step opens a folder
  directly.
- No new cloud calls: the game already holds the whole listing, so counts and folders come from it, offline as
  well.

### D9. Thumbnails by address

`getThumbnail` checks ownership and `hasThumbnail` like `createLinks`, then answers `302` to the thumbnail's
pre-signed link (the same 5-minute link `createLinks` makes) with `Cache-Control: private, max-age=240`. While that
redirect is cached, the browser reuses the same signed link and so its cached image. Thumbnails of versions never
change once attached, so a stale cache cannot show a wrong image.

Alternative: `thumbnailUrl` in every `SchematicSummary`. That signs a link per listed item on every listing, puts
expiring links into the game's stored listing, and still breaks after five minutes on a page left open; the
redirect is signed when the image is actually loaded.

### D10. Tag list by aggregation

`listTags` runs one aggregation on the owner's schematics: `$unwind` the tags, `$group` by tag with a count, sort
by tag. The `owner_tags` index serves the match on the owner. Like folders, tags are counted, not stored.

### D11. Uploading where the player is

- `uploadSchematic` takes an optional `folder` query parameter, so a new schematic is created in its folder in one
  request instead of an upload followed by a move that could leave it on the top level if the second call failed.
- The library uploads into the open folder (the top level in "All schematics") from three places: an "Upload here"
  card after the last card while browsing, files dropped anywhere on the cards, and "Upload here…" in the menu of
  the empty area. Files dropped onto a folder card or tree row, or "Upload into this folder…", go into that folder.
  The toolbar upload button is gone; the upload page stays for new versions.
- Files upload one after another through the existing `XMLHttpRequest` helper (for progress). An upload queue in the
  corner shows each file's progress and result; the 16 MiB check runs before sending. Afterwards the cards, folders,
  tags and storage use reload once.

## Risks / Trade-offs

- [A rename of a big folder bumps `UpdatedAt` for all of it, so those schematics jump to the top of "newest
  change first".] → Accepted: it matches a single move. Sorting by name is a later feature.
- [The website's counts are from the last `listFolders` and `listTags`, and another device can change them.] → They
  refresh after every change on this page and on load; a stale count is harmless.
- [Regex on `owner_folder` with special characters in a folder name.] → The prefix is escaped with
  `Regex.Escape`, and a test covers folders containing `.`, `+`, `(` and `[`.
- [The length check in rename and the update are not atomic.] → See D5; the worst case is the owner's own racing
  request producing one over-long path, which still lists and moves normally.
- [Dragging is mouse-only.] → The dialog covers touch and keyboard; the tree rows and folder cards are buttons and the
  dialog is keyboard-operable.
- [Website uploads have no thumbnail until `cloud-thumbnails`.] → Cards show a placeholder; nothing depends on a
  thumbnail being there.
- [Many cards load many thumbnails.] → `loading="lazy"` loads only visible ones, and the cached redirect (D9) avoids
  signing again while scrolling back.

## Migration Plan

No stored data changes. Deploy the cloud first; the website ships with it. The mod update can follow at any time,
since older mods only use the unchanged defaults. Rollback is redeploying the previous build.
