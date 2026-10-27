# Tasks

Cloud tests run with `dotnet test --project src/cloud/Heavymatica.Cloud.Tests`. While a dev AppHost is running, add
`-p:OutDir=<repo>/src/cloud/Heavymatica.Cloud.Tests/bin/alt/`. Website commands run in `src/web/player`, mod
commands in `src/mod`. Tasks marked **(manual check)** run in a browser or the dev client against the dev AppHost.

## 1. Contract

- [x] 1.1 In `src/api/heavymatica-cloud.v1.yaml`, add `includeSubfolders` and `unfiled` to `listSchematics`, the `Folders` tag with `listFolders` (`GET /v1/folders`, `FolderList`, `FolderEntry`) and `renameFolder` (`POST /v1/folders/rename`, `RenameFolderRequest`), and `moveSchematics` (`POST /v1/schematics/move`, `MoveRequest` with 1 to 100 unique IDs, `MoveResult`) (D3); verify `npm run lint` in `src/api` passes and `ContractTests` still serves the file unchanged
- [x] 1.2 Regenerate both sides: `dotnet build src/cloud` (add a stub `FoldersApi : IFoldersController` registered in `ContractServices`, and stub `moveSchematics` in `SchematicsApi`) and `compileJava` of the mod; verify both build and the existing mod unit tests pass
- [x] 1.3 Add the `Tags` tag with `listTags` (`GET /v1/tags`, `TagList`, `TagEntry`) and `getThumbnail` (`GET` on `/v1/schematics/{schematicId}/versions/{versionNumber}/thumbnail`, `302` and `404`) (D3), register a `TagsApi`, and regenerate both sides; verify the lint, `ContractTests` (controller list with `TagsController`), the cloud build, and `compileJava` with the mod unit tests

- [x] 1.4 Add the optional `folder` query parameter to `uploadSchematic`; verify the lint, the cloud build, and `compileJava` with the mod unit tests (the mod uploads through its own `CloudClient` and is unaffected)

## 2. Cloud

- [x] 2.1 Move `Listing/Folders` to `Schematics/FolderPaths` with `Normalize`, `Prefixes` and the in-or-below filter (equality or the escaped, anchored `^F/` regex) (D2, D6); verify unit tests for `Prefixes("a/b/c")`, and that the filter matches `houses` and `houses/medieval` but not `housesmall`, for folders containing `.`, `+`, `(` and `[`
- [x] 2.2 In `ListSchematics`, apply `includeSubfolders` and `unfiled`, and answer `invalid_request` for `unfiled` together with `folder`; verify `LibraryTests` for "Include subfolders", "Exact folder by default", "No folder" and the invalid combination, with paging across a subfolder listing without repeats
- [x] 2.3 Add `Folders/ListFolders`: aggregation by folder for the owner, prefix expansion into `directCount` and `totalCount`, sorted by path, and `unfiledCount` (D4); verify tests for "Parents are listed", "Other accounts are invisible", an empty library (no folders, `unfiledCount` 0), and that "Emptied folder disappears" after a move
- [x] 2.4 Add `Organize/MoveSchematics` with one `UpdateMany` on owner and IDs, setting `Folder` and `UpdatedAt`, and `moved` from the modified count (D5); verify tests for "Move three", "Foreign ID skipped", "Too many", a duplicate ID, an empty `folder` moving out of every folder, and that no version is created
- [x] 2.5 Add `Folders/RenameFolder`: normalize, the longest-path check, one pipeline `UpdateMany` replacing the prefix and setting `UpdatedAt`, `not_found` when nothing matches, `moved: 0` when `from == to` (D5); verify tests for "Rename with subfolders", "Merge", "Unknown folder", "Path too long" (nothing moved), renaming `a` to `a/b`, and that another account's schematics in the same path are untouched
- [x] 2.6 Check that cookie sessions need the request marker on both new POSTs and that `listFolders` accepts a website session; verify `WebSessionTests` for a cookie `moveSchematics` without `X-Heavymatica-Request` answering 403 and with it answering 200
- [x] 2.7 Add `Tags/ListTags`: `$unwind` and `$group` on the owner's tags, sorted by tag (D10); verify tests for "Counts per tag", "Other accounts' tags are invisible" and an empty library
- [x] 2.8 Add the thumbnail redirect in `Links/`: ownership and `hasThumbnail` as in `CreateLinks`, `302` to the pre-signed thumbnail link with `Cache-Control: private, max-age=240` (D9); verify tests for "Thumbnail shown" (following the redirect returns the stored PNG), "No thumbnail", another account's version answering the same 404 as a missing one, and a website session cookie being accepted
- [x] 2.9 Create new uploads directly in the normalized `folder` and reject a path over 200 characters (D11); verify a `FolderTests` case for "Upload into a folder" and "Folder path too long"

## 3. Website

- [x] 3.1 Add `lib/folders.ts` with `isInFolder`, `buildTree` (from the sorted `listFolders` result), `parentsOf`, `chunk` and the merge check, and the `?folder=` helpers in `lib/navigation.ts` (D7); verify Vitest tests for nesting, totals, `houses` vs `housesmall`, chunks of 100, and URL round trips with `/`, spaces and `%` in paths
- [x] 3.2 Map views to the URL and the listing query: top level, open folder, `?all=1` and `&tag=`, browsing vs search or tag (the D7 table), and `childFolders` for the folder cards; verify Vitest tests for every row of the table and URL round trips with a tag
- [x] 3.3 Build the explorer: sidebar with `FolderTree` ("All schematics", "Library", folders) and `TagList`, the "Folders & tags" dialog below 48rem, breadcrumb with "Rename folder", folder cards, and schematic cards with lazy thumbnails or a placeholder and the last change as tooltip; `pushState`/`popstate` for views; verify `npm run check` and **(manual check)** "Top level", "Open a folder", "Back up", "Everything at once", "Tree opens a folder", "Phone width", "Search in a folder", "Tag on the top level", "Choose and clear a tag", "Back button" and "Thumbnail or placeholder" on the seeded library
- [x] 3.4 Selection, moving and renaming in the explorer: checkboxes on schematic cards, selection bar, `MoveDialog` in chunks of 100, dragging onto folder cards and tree rows, the rename dialog with the merge warning and `replaceState` (opened from the context menu, see 3.7); refresh tree, tags and cards after a change; verify `npm run check` and **(manual check)** "Move a selection", "Drag onto a folder card", "Rename the open folder", "Merge warning", and that the selection survives "Load more"
- [x] 3.5 On the details page, add tag editing (chips with remove, an "Add tag" field that keeps focus for the next tag, at most 20 tags of 1 to 50 characters); verify `npm run check` and **(manual check)** "Add and remove a tag" and "Duplicate tag"
- [x] 3.6 Check keyboard and touch use **(manual check)**: tree rows, folder cards, selection, tags and the dialogs work with Tab, Space, Enter and Escape, and on a touch device (or the browser's touch emulation) moving works through the dialog
- [x] 3.7 Replace the explorer's action buttons with context menus: schematic cards (open, rename, move, delete; move or unselect a selection), folder cards and tree rows (open, rename or move), opening at the focused element from the keyboard; verify `npm run check` and **(manual check)** "Rename from the menu", "Move a selection from the menu" and "Folder from the menu", the menu key, Escape and a click elsewhere
- [x] 3.8 Upload into the open folder: the "Upload here" card, dropping files on the cards, onto folder cards and tree rows, "Upload here…" and "Upload into this folder…" in the menus, the upload queue with progress and reasons, and no toolbar upload button (D11); verify `npm run check`, a Vitest test for the upload address with a folder, and **(manual check)** "Drop files into the open folder", "Drop onto a folder card" and "A file is rejected"

## 4. Game

- [x] 4.1 Add `Library.view(folder, all, text, tag)` returning up, folder and entry items by the D7/D8 table, with child folders and their totals from the cloud entries and local entries on the top level, and drop `folderTree()`; keep `LibraryEntry.inFolder` (D8); verify `LibraryTest` cases for "Open a folder", "Combined filter", "All schematics", the top level with a local entry, `houses` vs `housesmall`, and no up card on the top level
- [x] 4.2 Draw up and folder cards in `CardGrid`, open folders and go up on click, show the path above the grid, replace the sidebar's folder rows with the "All schematics" row and the tags, and add the `openFolder=<path>` dev step (D8); verify `compileJava` and **(manual check)** with a `HEAVYMATICA_DEV_SCREEN` script that opens the library, opens `builds` by its card, opens `builds/medieval` with `openFolder`, goes up, turns on "All schematics", and takes a screenshot of each

## 5. Integration

- [ ] 5.1 Run the flow on the dev AppHost **(manual check)**: in the browser, move schematics into `castle/towers` and rename `castle` to `builds/castle`; then do the same in a dev account through the API and see the game's explorer show `builds/castle` with `towers` inside it
- [x] 5.2 Update `ROADMAP.md` step 5 to list `web-folders` between `web-library` and `account-links` and mark it done; verify the file reads consistently

## Workflow follow-up

- Archive `web-library` before archiving this change; its `web/player-ui` and account-based `cloud/schematic-storage` texts are the base these deltas build on.
- Commit the implementation once all tasks are done (Conventional Commits, for example `feat(web): add the library explorer`).
- Archive the change with `/opsx:archive` when asked.
- `cloud-thumbnails` follows: the cloud renders a thumbnail for every version uploaded without one, and backfills older versions.
