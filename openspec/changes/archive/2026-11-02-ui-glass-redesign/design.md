## Context

- Every screen in `ui/` is built from vanilla widgets (`Button`, `EditBox`) and placed with fixed pixel constants
  (`LibraryScreen.SIDEBAR = 104`, `ACTION_STEP = 72`, `BlockPickerScreen.PANEL = 260`). That is why rows overlap
  and run off the screen at small GUI scales.
- The wheel already has a game-free, tested way to draw smooth translucent shapes: `remote/WheelMesh` builds feathered
  quads, and `WheelMeshRenderState` submits them as one custom GUI element on the vanilla position-colour pipeline.
  `WheelStyle` holds the palette (glass body, rim, text greys, ghost cyan, danger red, confirm green).
- `ThumbnailRenderer` renders 256 × 256 thumbnails for uploads. Local entries (`LocalLibrary.LocalEntry`) have none,
  so their cards show a placeholder.
- `placement/MaterialList` with `CarriedItems`, `CarriedShares` and `Demand` already computes needed, had and short per
  item, and what the player carries, inventory and containers included.
- `DevScreens` drives screens for screenshots and dev scripts through public hooks. Those hooks must keep working.
- The mod registers no config screen. The mods list shows no Config button for Heavymatica.
- The agreed look is the mockup canvas linked in the proposal. The mockups use a pixel web font as a stand-in for the
  game's font.

## Goals / Non-Goals

**Goals:**
- One small widget kit that every Heavymatica screen and HUD panel uses, so the look is defined once.
- Layouts computed from the screen size by game-free code that is unit-tested at the smallest and largest sizes.
- No change to what the existing screens do, beyond what the specs list.

**Non-Goals:**
- The cloud screens (account, first sign-in choice, link code, upload target). They keep their current look until
  `cloud-ui-redesign`. The cloud parts inside the library (folders, tags, versions, account chip) are part of this
  change, because they share the library's layout.
- A 3D, rotatable preview on the details page. It shows the thumbnail.
- Custom fonts. All text uses the game's font, and titles are scaled up.
- Theming or colour settings.
- Controller or touch input.

## Decisions

### D1: A glass widget kit on top of `WheelMesh`

A new package `ui/glass` holds:

- **`GlassStyle`:** the palette. It extends `WheelStyle`'s colours with built green (`#7EE28F`), carried blue (the
  ghost cyan `#7FD8FF`), short orange (`#FF7A45`), warning yellow and the text greys. The wheel keeps its own
  constants and points them at `GlassStyle`, so there is one source.
- **`GlassMesh`:** a game-free builder for rounded rectangles, pills, rims and segmented bars as feathered quads in
  screen coordinates, next to `WheelMesh`'s polar shapes. A rounded corner is a small quad fan. The feather is one
  physical pixel wide, as in the wheel.
- **`Glass`:** submits each shape as its own custom GUI element through `WheelMeshRenderState`. The game's GUI render
  state already stacks every element above the earlier ones its bounds overlap, text included, so shapes and text drawn
  in order layer correctly without a separate canvas. A dialog starts a new stratum for itself.
- **Widgets:** panel, button (primary, secondary, danger), chip, toggle, segmented choice, slider, number field and
  search field (both wrap vanilla `EditBox` without its border), segmented progress bar, keycap, scroll list with
  scissor and a thin scroll bar, tooltip, and a dialog frame. Each widget is a vanilla `AbstractWidget`, so focus,
  keyboard navigation and narration keep working.
- **`TextFit`:** shortens text to a width with `…` and remembers the full text for the hover tooltip.

*Alternative: vanilla widgets with nine-slice sprites.* Rejected: sprites are pixel art at one scale and cannot give
the wheel's smooth glass edges, so the screens would not match the wheel.

### D2: Layout as a game-free calculation

Each screen gets a small layout record (`LibraryLayout`, `MaterialsLayout`, …) computed from the screen's width and
height in GUI units. It returns rectangles for header, grid, panel, rows and so on. The rules are explicit: the details
panel takes `clamp(width × 0.3, 170, 260)`, the grid gets the rest, card columns are
`floor(gridWidth / minCardWidth)`, and the header's search field shrinks before any button does.

The smallest size the game offers for an 854 × 480 window is 427 × 240 GUI units, at scale 2. Unit tests check every
layout at 427 × 240, 640 × 360 and 960 × 540: no two rectangles overlap, and every one lies inside the screen.

*Alternative: a general flex layout engine.* Rejected: a handful of screens with clear rules don't need one, and plain
records are easier to test and read.

### D3: Library structure

- `LibraryScreen` becomes three parts: header, grid area and details panel. In a panel shorter than 300 GUI units
  the preview is left out, since the selected card shows the thumbnail and the block list needs the room.
- The panel only counts as under the mouse on its version rows and block list. The screen hands clicks to the first
  child under the mouse, and the buttons it places on the panel must get theirs.
- `FilterSidebar` goes away. Its "All schematics" toggle and tags become chips in a row above the grid, together with
  the folder path and All, Cloud and Local filters. Folder cards stay in the grid as in the folder explorer requirement.
- `DetailsPanel` becomes the panel beside the grid. It holds the preview, chips for kind, version, size and blocks,
  the versions list for cloud schematics, the scrolling block list from `DetailBlockCounts`, the Place button and the
  action grid.
- `ImportingLibraryScreen` keeps adding import and upload. Its upload entry moves into the details panel for local
  entries, as the mockup's "Only on this computer" box.
- **Account chip:** the account button, the sign-in button and the quota text in the header become one chip: skin,
  name, status dot and quota bar while signed in, otherwise Sign in. Clicking it opens the existing account screen,
  which gains the Sign out button the header had.

### D4: Details page

A new `EntryDetailsScreen` takes the library entry and the chosen version and returns to the library with the same
selection. The facts come from what the client already has: format, size and block count from the entry, the
Minecraft version from the model's data version (named from a small table of releases and the running game's own data
version, otherwise shown as the data version), and the time from
`importedAt` or `updatedAt`. The preview is the entry's thumbnail drawn large. The block list reuses
`DetailBlockCounts` and adds a filter box and share bars.

### D5: Thumbnails for local entries

Local entries keep their thumbnail in the library's thumbnail cache, keyed by content hash like cloud thumbnails;
`ThumbnailTextures` already rendered them there with `ThumbnailRenderer` when a card was first shown. This change
limits that to one render at a time and skips an entry whose render failed until the next game start. A freshly
imported entry gets its thumbnail as soon as the library shows its card. If the cache ever evicts a local thumbnail,
it is rendered again the next time the card shows.

*Alternative: a `thumbnail.png` next to each local copy.* Not needed: the cache path already worked for local entries,
and a second store would only duplicate it.

### D6: Bars from the material list

`MaterialCounts` also sums, per planned block and layer, what each position plans whatever its state; it is filled
from the same demands as the needs, so two-position blocks, amounts and the two-item rules count the same way.
`MaterialList.withPlanned` takes those sums for the visible layers, and `bar(block)` and `total()` derive the bars. The bar is then built
`planned − needed` (at least 0), carried `min(had, needed)` and short `needed − carried`. The whole-placement bar sums
these over all items. The overlay and the materials screen read the same numbers, so they always agree.

The overlay's percentage stays the world comparison's progress over the whole placement. The bar follows the visible
layers, like the material list. With layers off, both cover the same positions.

### D7: Block picker counts

The picker asks `CarriedItems` once when it opens, and again whenever the materials screen's own refresh runs, for
the count of every block item in the player's inventory and containers. "Enough" compares with the row's needed count
while the placement is locked, and with the row's position count otherwise. The footer's "short with this block"
uses the same comparison. It doesn't account for the chosen block already being needed elsewhere in the placement:
the picker gives guidance, not the material list's exact sharing.

### D8: Materials rows

`MaterialRows` produces a row model holding the original block, the block it shows as, whether it is swapped, and
the bar numbers. A row's name is a button: clicking it opens the picker, and hovering it shows "Swapped from …" when
swapped. The undo button resets the swap through the existing swap API. Sections are ordered as follows: rows, then
"can't carry", then "unknown blocks", as the spec orders them. The header and the whole-placement bar stay outside
the scroll list.

### D9: HUD

- `PlacementOverlay` draws one glass panel: title row (name, version or "local", swap count, state badge),
  percentage and bar, layer line, mistake chips with counts above 0, finishing chips, the top shortages, the unknown
  line, then keycap hints and the small position line. It never names the block under the crosshair: that changes
  with every look and made the panel flicker, and the targeting HUD already shows it. The compact overlay is the title
  row alone.
- `TargetHud` and `PointerHud` become pills under the crosshair. Where the pointer goes depends on the target HUD's
  height that frame, so they never overlap. The overlay sits at the side and never reaches the centre column: its
  width is capped at a third of the screen.

### D10: Dialogs keep their parent

A `GlassDialogScreen` base holds the parent screen. While it is open, it renders the parent (without hover), then
the dim, then the dialog. Esc and close return to the parent object itself, not a new screen, so the selection and
scroll position survive. `BlockPickerScreen`, `TextPromptScreen`, `ExportConflictScreen` and the delete confirmation
move onto it. Heavymatica screens skip the game's menu blur and draw their own dim, like the wheel.

### D11: Settings screen from a table

`SettingsScreen` is driven by a list of setting descriptors: section, config value, control kind, and language keys
for the name and explanation. Changing a control sets the `ModConfigSpec` value and saves the spec at once.
Out-of-range input is rejected by the value's own validator before it is set. The remote item row reads the main hand
for "Use held item". The opacity steps row edits the list through `OpacitySteps`' existing rules. It's registered with
`container.registerExtensionPoint(IConfigScreenFactory.class, …)` in the client entry point. The "Heavymatica Cloud"
row binds to `cloud.access`, which `cloud-availability` adds.

*Alternative: NeoForge's generated `ConfigurationScreen`.* Rejected: it shows raw keys in vanilla widgets, can't
group by our sections, and hides the explanations in tooltips.

## Risks / Trade-offs

- [Custom GUI elements and text may not order correctly when several panels overlap, as with a dialog over a
  screen.] → `GlassCanvas` submits one element per layer (screen, then dialog), and draws each layer's text after it.
  The dialog dim is its own layer between them. This is checked first on the block picker (task 1.4).
- [`EditBox` draws its own background and cursor colours.] → Use it without a border, draw the glass field behind
  it, and set the text colours. Both are supported by the vanilla widget.
- [Rendering thumbnails for many old local entries could cause stutter.] → At most one render per frame, and only
  for cards on screen.
- [Merging the "shows as" column into the name hides the original block on touch-less, hover-less input such as a
  controller.] → Accepted: hover is how Minecraft shows item details too, and the swap is still visible by colour
  and the undo button.
- [The overlay's bar (visible layers) and percentage (whole placement) can disagree while layers are on.] → The layer
  line shows the layer progress next to them, and the materials screen uses the same bar numbers.
- [Large rewrite of `LibraryScreen` (~640 lines) and `MaterialsScreen` (~420 lines).] → The behaviour lives in
  `Library`, `MaterialList` and the swap code, which are unchanged and already tested. The screens become layout plus
  widgets.

## Migration Plan

- No data migration. Old local entries get their thumbnails the first time they appear.
- Apply `cloud-availability` first, so `cloud.access` exists when the settings screen is built. If it isn't applied
  yet, task 7.3's row is added once it is.
- Rollback is a revert. Saved settings, swaps and placements are unaffected.

## Open Questions

- Should the preview on the details page later become a rotatable 3D view? Not needed for this change.
