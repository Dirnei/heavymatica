# Proposal

## Why

Some planned blocks are never placed as themselves. Grass, dirt paths and farmland are dirt that grows over or gets
a shovel or a hoe. Stripped logs are logs plus an axe, and concrete is powder plus water. Today the material list
asks for items the player can't get, such as dirt paths, and the comparison flags dirt where grass is planned in
red, although nothing is wrong. Grass spreading and farmland drying out make it worse over time.

## What Changes

- **A table of block equivalents.** Each row names a planned block and says:
  - which item places it (counted in the material list)
  - which other blocks already count as the planned block (equivalent)
  - which blocks only need a finishing step, and which: a shovel, a hoe, an axe or water
- **The table is data.** A row is one entry over block IDs, with `*` patterns for families such as stripped logs.
  Rows only relate blocks. Orientation is still compared through the properties both blocks share, so a stripped
  log and its log compare their `axis`.
- **Initial rows:**

  | Planned block | Item counted | Also matches | Needs finishing |
  |---|---|---|---|
  | grass block | dirt | dirt | none |
  | dirt | dirt | grass block | none |
  | dirt path | dirt | none | dirt, grass block, podzol, mycelium, coarse dirt or rooted dirt: shovel |
  | farmland | dirt | none | dirt, grass block or dirt path: hoe |
  | stripped logs, wood, stems, hyphae and stripped bamboo block (every wood type) | the unstripped block | none | the unstripped block: axe |
  | concrete (16 colours) | concrete powder of the same colour | none | that powder: water |

  Planned podzol, mycelium, coarse dirt and rooted dirt have no row and keep their own items.
- **Need and have per planned block.** A planned block's have counts the carried items of every block that is
  placed as it or placed and then finished into it: its own item, the item that places it, and the items of the
  blocks it accepts or finishes from. So silk-touched grass counts for planned grass and dirt; dirt path items for
  planned paths and farmland; podzol, mycelium, coarse dirt and rooted dirt for planned paths; concrete and its
  powder for planned concrete; and only powder for planned powder, never concrete.
- **Sharing carried items.** Each carried item counts once. Planned blocks are served one after another, those
  accepting the fewest kinds of item first (ties by block ID), each taking its own item first, then the placing
  item, then accepted, then finishing items. The material list still has one row per placing item: needed adds
  up, had is what its planned blocks were given plus its own unused stock, and short is what they were not given.
- **Comparison gains a state: needs finishing.**
  - It shows as a yellow box with no ghost, like the mistakes.
  - It doesn't count as done for progress or layer progress.
  - It needs no item from the material list.
  - When something is wrong with the orientation or water of a block that only needs finishing, that is shown first,
    like any other mistake.
  - Farmland that dried back to dirt shows yellow again, not red.
- **Materials screen:** while locked, a row shows the needed, had and short of the planned block it shows as,
  not of its item, so a white concrete row and a white concrete powder row can differ. Rows of the same planned
  block still share. A "Finish" column shows how many positions of the row's block need finishing in the
  visible layers, and with which tool.
- **Overlay:** one line per tool, such as "3 blocks need a shovel", counted over the visible layers only, while
  the red, orange, cyan and magenta counts stay over the whole placement. The crosshair on a yellow box shows the
  expected and the found block and the tool.
- **In-hand filter:** holding a block highlights the ghosts it places and those whose have it counts toward.
  Dirt highlights grass, dirt, dirt path and farmland ghosts. Podzol highlights dirt path ghosts. An oak log
  highlights stripped oak log ghosts. White concrete powder highlights white concrete ghosts, and white concrete
  does not highlight powder ghosts.
- **Swaps come first.** The table applies to the block a swap produces.

Out of scope (later rows):
- copper weathering accepted as equivalent
- waxed copper (place copper, then apply honeycomb)
- snow layers, crop ages and leaf persistence
- equivalents defined in config or datapacks; the table is built in for now

## Capabilities

### New Capabilities
- `client/block-equivalents`: the equivalents table and its initial rows, swaps coming first, when a position needs
  finishing, that finishing is not done for progress, which carried items count toward a planned block and how
  they are shared, and the materials screen's per-planned-block numbers and Finish column.

### Modified Capabilities
- `client/world-comparison`:
  - "Cell states": a block the table accepts matches, and positions can also need finishing.
  - "Mistake states": a block the table accepts or lists as needing finishing is not wrong block.
  - "The first fix decides the state": needs finishing comes after wrong orientation and wrong water.
  - "Showing the states": needs finishing shows a yellow box and no ghost.
- `client/material-list`:
  - "Positions that need an item": needs finishing doesn't need an item.
  - "Blocks become items": a position counts as the table's item.
  - New "Had follows the planned blocks": an item's had and short come from the carried items shared out to the
    planned blocks it places.
- `client/placement-layers`, "In-hand filter": the held block also highlights the planned blocks whose have it
  counts toward.
- `client/placement-remote`: a new "Finishing in the overlay" requirement adds the finishing lines per tool and
  the step under the crosshair.
- `client/material-swaps` exists only in the unarchived `material-swaps` change, so it is not modified as a delta.
  When `material-swaps` is archived, its "Needs on a locked placement" is superseded by `client/block-equivalents`
  ("Needs per planned block in the materials screen" and "Finish column in the materials screen"): rows show the
  table's item, as `client/material-list` "Blocks become items" says, with the numbers of their planned block.

## Impact

- **Mod code only (`src/mod/heavymatica`), `placement` package:**
  - a game-free table (rules over block-ID strings) and a game-free sharing of carried items (`CarriedShares`)
  - `CellState` and the per-entry `Expected` term
  - `CellKeys`, `ComparisonState`, `MaterialCounts`, `WorldComparison`, `Materials` and `MistakeBoxes`
  - the in-hand filter in `GhostRenderer`
- **Other packages:**
  - `ui`: `MaterialRows` and `MaterialsScreen`
  - `remote`: `PlacementOverlay`
  - language keys
- **Saved data:** none. Comparison results are not saved.
- **Depends on `material-swaps` and `ghost-opacity-wheel`.** It builds on the swaps and the materials screen. Archive
  order: `material-swaps` → `ghost-opacity-wheel` → `block-equivalents`.
- No cloud, API or server changes.
