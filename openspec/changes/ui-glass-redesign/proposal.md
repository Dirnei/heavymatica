## Why

The placement wheel now looks finished, but every other screen still uses vanilla stone buttons, fixed pixel rows and
flat text. At small GUI scales they break: in the library, the account label, quota and status dot are drawn over each
other, the action row runs off the screen and the details overlap the Export button. The materials screen's column
headers overlap and block names get cut down to `...`. The first public alpha will be judged on these screens and on
the HUD, so they need to match the wheel and work at every GUI scale before anything is published.

## What Changes

- **Shared glass look** for every Heavymatica screen and HUD panel, in the wheel's palette: smoked-glass panels,
  rounded and anti-aliased, accent buttons, chips, progress bars, keycaps and a dialog frame that opens over the screen
  it came from. Layouts adapt to the GUI scale, and long text is shortened with `…` and shown in full on hover.
- **Library:** the tag sidebar and the bottom button row are gone.
  - The grid gets the width, and a details panel beside it shows the selected entry: preview, kind, size and block
    count, versions for cloud schematics, every block type in a scrolling list, a large Place button, and Export,
    Rename and Delete (plus Move and Tags for cloud schematics).
  - Folders and tags become filter chips above the grid.
  - The header has search, Import, the account chip (cloud only) and a settings button.
- **Details page:** "All details" opens a full page for one entry: a large preview with its size, facts (format, size,
  Minecraft version when the file has one, block types, added or uploaded time), the same actions, and the full block
  list with a filter and bars showing each block's share.
- **Import screen:** the schematic folder list becomes a checklist (pick several, import them at once, open the folder).
  An empty library shows this screen as its first-run view, next to a short explanation of Capture. Dropping files
  works anywhere on the library and import screens.
- **Local entries get thumbnails** rendered when they are imported, so local and cloud cards look alike.
- **Materials screen:**
  - The Block and "shows as" columns merge. A row shows the block it will be built with: a swapped row in the accent
    colour with an undo button, and the original in a hover tooltip. Clicking the name opens the block picker.
  - On a locked placement, needed, had and short become one bar per row (built green, carried blue, short orange)
    with the short count beside it, and a bar for the whole placement on top.
  - Unknown blocks move to their own part at the end, like "can't carry".
- **Block picker:** a dialog over the materials screen with same-kind tiles first and all blocks below. Each tile shows
  how many of that block the player carries and whether it is enough. A "Keep original" button resets the swap, and
  clicking a tile applies it at once.
- **HUD:**
  - The placement overlay becomes a glass panel: name, version and state badge; a progress bar; one chip per mistake
    state; finishing chips; the top shortages; keycap hints; position and rotation on one small line.
  - Mistake counts at 0 are no longer shown.
  - The targeting HUD and the mistake pointer become separate glass pills under the crosshair.
- **Settings screen:** a new in-game screen for every client setting, in six sections, with a short explanation for
  each. It opens from the mods list and from the library. "Use held item" sets the remote item. The cloud address and
  developer settings stay in the config file only.
- Mockups this was agreed from: https://claude.ai/artifact/FWfALSST927P584wiuFGs6 (library, import, details,
  materials, block picker, HUD, settings).

## Capabilities

### New Capabilities

- `client/settings-screen`: the in-game settings screen: where it opens, which settings it shows and how changes are
  applied and saved.
- `client/screen-layout`: rules every Heavymatica screen and HUD panel follows: fitting every GUI scale, shortened text
  with full text on hover, dialogs over their parent screen.

### Modified Capabilities

- `client/schematic-library`: the details panel beside the grid (scrolling block list, actions in the panel), folder
  and tag chips, the details page and the settings button.
- `client/schematic-import`: the import checklist with several files at once and opening the folder, the empty library
  showing the import view, and thumbnails for local entries.
- `client/material-swaps`: the merged block column with click-to-swap and undo, the combined bars, unknown blocks in
  their own part, and the block picker dialog with carried counts and Keep original.
- `client/placement-remote`: the overlay's progress bar and mistake chips, with counts of 0 left out.

## Impact

- `ui/`: `LibraryScreen`, `ImportingLibraryScreen`, `CardGrid`, `FilterSidebar` (removed), `DetailsPanel`,
  `ImportScreen`, `MaterialsScreen`, `MaterialRows`, `BlockPickerScreen`, `ExportConflictScreen`, `TextPromptScreen`,
  `ScrollBar(s)` are rewritten on new shared glass widgets. `DevScreens` keeps its screenshot hooks.
- `remote/PlacementOverlay`, `targeting/TargetHud`, `targeting/PointerHud`: drawn as glass panels and pills. What they
  show stays the same, except that zero counts and the progress bar change.
- `remote/WheelMesh`, `WheelStyle`: the feathered-quad mesh and palette are shared with the new widgets.
- `library/LocalLibrary`: stores a thumbnail with each local entry, rendered by the existing upload thumbnail renderer.
- `placement/MaterialList`: also reports each item's planned count in the visible layers, for the bars.
- `HeavymaticaConfig` and the mod entry point: a config screen factory for the mods list.
- Language files: new keys for the new screens and sections.
- Cloud screens (account, first sign-in choice, link code, upload) are not part of this change and follow in
  `cloud-ui-redesign`. The settings screen's "Heavymatica Cloud" row needs the `cloud.access` setting from
  `cloud-availability`, which should be applied first.
- No server, cloud API or network changes.
