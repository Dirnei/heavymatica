## 1. Glass widget kit

- [x] 1.1 Add `ui/glass/GlassStyle` with the palette from D1 and point `WheelStyle`'s constants at it. Verify `./gradlew :heavymatica:test` still passes and the wheel looks unchanged in a `runClient` dev screenshot.
- [x] 1.2 Add `GlassMesh` (rounded rectangle, pill, rim, segmented bar) with `WheelMesh`'s feathering, free of the game. Verify with `GlassMeshTest`: corners lie on the radius, the feather vertices have alpha 0 and are one physical pixel wide at GUI scales 1 to 4, and a segmented bar's parts add up to its width with gaps.
- [x] 1.3 Add `Glass` (each shape submitted as its own custom GUI element, which the GUI stacks with text by bounds) and `TextFit` (shorten with `…`, keep the full text for the tooltip). Verify `TextFitTest` with a fake width function: fitting text is unchanged, long text ends in `…` and fits, and an empty width gives `…`.
- [x] 1.4 Add the widgets from D1: panel, button (primary, secondary, danger, disabled with reason), chip, toggle, segmented choice, slider, number and search field on a borderless `EditBox`, progress bar, keycap, scroll list with scissor, tooltip. Add `GlassDialogScreen` (D10) and spike it with a throwaway dev dialog over the library: check in a `runClient` screenshot at GUI scale 2 and 4 that the edges are smooth, text sits above its panel, and the dialog layer covers the parent including its text. Keyboard focus and Tab must move through the widgets.

## 2. Layouts

- [x] 2.1 Add game-free layout records for the library, import, details page, materials screen, block picker, settings and HUD overlay (D2). Verify with `ScreenLayoutTest` at 427 × 240, 640 × 360 and 960 × 540: no two rectangles overlap, all lie inside the screen, the details panel width follows `clamp(width × 0.3, 170, 260)`, and the search field shrinks before any header button.

## 3. Library and details

- [x] 3.1 Rewrite `LibraryScreen` on the kit and `LibraryLayout`. It gets a header (title, count, search, Import, account chip when the cloud is shown, settings button), a chip row (folder path, All, Cloud and Local, tags; this replaces `FilterSidebar`, which is deleted) and the card grid (folder and up cards, then entries with a cloud or local mark). Keep `Library` and the filtering rules unchanged. Verify `./gradlew :heavymatica:test`, and in `runClient` the "Search, folders and tags" and "Folder explorer" scenarios.
- [x] 3.2 Rebuild `DetailsPanel` as the panel beside the grid. It holds the preview, chips, versions for cloud schematics, "N block types" with the scrolling list from `DetailBlockCounts`, Place, and the action grid (Export, Rename, Delete; Move and Tags for cloud). Disabled actions show their reason on hover, and "All details" is linked. Local entries show the upload box from `ImportingLibraryScreen` when signed in. Verify in `runClient` the specs' "Details beside the grid", "Every block type in the details" and "Actions in the details panel" scenarios, plus the existing Place and Export scenarios.
- [x] 3.3 Move the rename prompt, the delete confirmation and `ExportConflictScreen` onto `GlassDialogScreen`. Verify in `runClient` the "Rename and cancel" scenario and the existing "Delete needs confirmation", "Keep both" and "Cancel" scenarios.
- [x] 3.4 Add `EntryDetailsScreen` (D4): preview with its size labels, facts (format, size, Minecraft version only when the file records one, blocks, types, time), the actions, and the block list with filter and share bars. Back keeps the selection. Verify with a unit test for the facts (a model without a data version gives no version line), and in `runClient` the "Open and return" and "Filter" scenarios.
- [x] 3.5 Add the settings button to the library header, opening `SettingsScreen` with the library as parent (needs group 7, or opens a placeholder until it lands). Verify the "Open settings" scenario in `runClient`.

## 4. Import and local thumbnails

- [x] 4.1 Rewrite `ImportScreen` as the import checklist: folder path, format and size per file, tick several, "Import N selected", Open folder (creates `schematics/` if missing), and drop anywhere. Keep the existing import, validation and duplicate rules. Verify in `runClient` the "Import several at once", "Open the folder" and "Drop on the import screen" scenarios, and the existing "Drop three files" and "Same file again".
- [x] 4.2 Show the import screen with the Capture explanation when the library has no entries, after the listing has loaded. Verify with a unit test on the decision (no local, no cloud entries after loading means the import view), and in `runClient` the "First open" and "After the first import" scenarios.
- [x] 4.3 Give local entries thumbnails through the library's thumbnail cache (D5): rendered when their card is first shown, one render at a time, skipped until restart on failure. Verify in `runClient` the "Imported entry has a picture" and "Older entry" scenarios: local entries imported before the change show their rendered thumbnails in the grid.

## 5. Materials and block picker

- [x] 5.1 Add `planned` per item to `MaterialList` (D6), counted with the needs' rules. Verify with `MaterialListTest` cases: a door counts once, three candles count 3, a two-item block on its empty base counts its content item, and planned covers only the visible layers.
- [x] 5.2 Rework `MaterialRows` into the row model of D8: shown block, swapped flag, original, bar numbers, sections (rows, can't carry, unknown blocks). Verify with `MaterialRowsTest`: the "Row bar" and "More carried than needed" numbers, shared items giving equal bars, and unknown blocks sorted into their own part in every state.
- [x] 5.3 Rewrite `MaterialsScreen` on the kit: header with placement, state and layer chips, search and Done; the whole-placement bar with legend when locked; the scrolling table (block name button with hover text, undo, in-plan count, bar with legend, short count); can't carry and unknown parts. Verify in `runClient` the "Swapped row", "Click to swap", "Undo a swap", "Locked placement", "Picking up items while it is open", "Water" and "Unknown blocks last" scenarios.
- [x] 5.4 Rewrite `BlockPickerScreen` as a `GlassDialogScreen` over the materials screen. It gets a header with the block and how many positions change, a focused search, Keep original, same-kind tiles, then all blocks, each with carried counts (D7), and a footer line. Clicking a tile applies the swap and closes, and Esc closes without a change. Verify with a unit test for the carried labels (none, a count, enough), and in `runClient` the "Suggestions for stairs", "Search", "Keep original", "Esc", "Choose by what is carried" and "Not locked" scenarios.

## 6. HUD

- [x] 6.1 Redraw `PlacementOverlay` as the glass panel of D9: chips only for counts above 0, the bar from the material list, finishing chips, the top shortages, keycap hints, a small position line, and the compact title row alone. Width is capped at a third of the screen. Verify with a unit test on the overlay's line model ("Zero counts left out"), and in `runClient` the "Progress and mistakes", "Zero counts left out", "Progress bar", "Compact enabled" and key-hint scenarios on both sides of the screen.
- [x] 6.2 Redraw `TargetHud` and `PointerHud` as pills under the crosshair, with the pointer placed below the target HUD's height that frame. Verify in `runClient` the "Everything shown at once" scenario at GUI scale 2 and 4, and the existing targeting and pointer scenarios.

## 7. Settings screen

- [x] 7.1 Add the settings descriptor table and `SettingsScreen` (D11) with the six sections, explanations in `en_us.json`, Reset section, and Done back to the parent. Changes save at once, and out-of-range input is rejected. Verify with a unit test that every `HeavymaticaConfig` value except the cloud address and the dev sign-in URL appears in exactly one section, and in `runClient` the "Switch section", "Ghost opacity", "Out of range" and "Reset ghosts" scenarios.
- [x] 7.2 Add the remote item row with "Use held item" and the opacity steps row (add, remove, keep the 2-to-8 and range rules). Verify in `runClient` the "Use a blaze rod", "Empty hand", "Add a step" and "Two steps minimum" scenarios.
- [x] 7.3 Add the "Heavymatica Cloud" row (Automatic, Always, Never) bound to `cloud.access` from `cloud-availability`. Verify the "Turn the cloud off" scenario in `runClient`.
- [x] 7.4 Register the screen with `IConfigScreenFactory` in the client entry point. Verify the "From the mods list" scenario in `runClient`.

## 8. Integration

- [x] 8.1 Update `DevScreens` hooks for the new layouts and take dev screenshots of every redesigned screen and the HUD at GUI scale 2 and 4 in an 854 × 480 window. Check them against the mockup canvas and the `client/screen-layout` scenarios (no overlap, `…` with hover on a long name). Run `./gradlew :heavymatica:test` and fix any failures.
- [x] 8.2 Update the README's description of the library, materials screen and settings, if it describes them, and mark the UI redesign in `ROADMAP.md` if it is listed there. Verify both files read correctly.
