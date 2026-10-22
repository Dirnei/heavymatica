# Design

## Context

For motivation see proposal.md, Why. For behaviour see `specs/client/block-equivalents` and the deltas for
`client/world-comparison`, `client/material-list`, `client/placement-layers` and `client/placement-remote`.

The current state this builds on (after `world-comparison`, `material-list`, `material-swaps` and
`ghost-opacity-wheel`):
- **`CellState`** (game-free): `UNKNOWN`, `IGNORED`, `MATCHING`, `MISSING`, `WRONG_BLOCK`, `WRONG_ORIENTATION`,
  `WRONG_WATER`, `EXTRA`, stored as its ordinal in one byte per cell. `showsGhost()` drives the ghost mask,
  `needsItem()` the material counts and `mistake()` the box sections. `classify(Expected, Found)` decides a cell.
- **`Expected` / `Found`:** a block identity (registry int), an orientation key and waterlogging. `CellKeys` builds
  the orientation key from a fixed list of `Property` objects, matched by identity, so the same property on two
  different blocks gives the same key. `BlockStateProperties.AXIS` is shared by logs and stripped logs.
- **`WorldComparison`:** per palette entry of the swapped `GhostView` it keeps an `Expected` and a `Demand` (an item
  index and amount from `placed.getBlock().asItem()`). `set` moves demand in and out of `MaterialCounts` (per layer
  and per item) when `needsItem` changes.
- **`ComparisonState`:** counts progress from `MATCHING` only, keeps the ghost mask bit as `showsGhost()`, and
  marks box sections when `mistake()` changes. `MistakeBoxes.color` maps states to colours, 0 meaning no box.
- **`Materials`:** builds the `MaterialList` from the summed counts over the visible layers, with
  `CarriedItems::count` as the had function.
- **`MaterialRows` / `MaterialsScreen`:** a row per schematic block, with the numbers of the item of the block it
  shows as (`MaterialsScreen.itemOf`), and the carried count for items the list does not need.
- **`GhostRenderer.inHand`:** an alpha function over ghost `Block`s that compares them with
  `PlacementSession.placedBlock` of the held item and of the hotbar.
- Minecraft is not on the unit test classpath, so rules are kept game-free over block-ID strings.

## Goals / Non-Goals

**Goals:**
- Adding a row is one entry in one list, with no change elsewhere.
- No per-cell cost: equivalence is resolved once per palette entry, the cell storage stays one byte.
- Rule matching, classification, counting and sharing out carried items are game-free and unit-tested.

**Non-Goals:**
- Converting block states. The table only relates block IDs, see D2.
- Rows from config or datapacks.
- Tracking how long a position has needed finishing, or why (grass spread, farmland dried out).

## Decisions

### D1. The table: game-free rules over block IDs

A new `placement/BlockEquivalents` holds an ordered list of rules. The first rule that matches a planned block ID
wins. A rule is `Rule(String planned, String placedWith, List<String> same, List<String> finishFrom, Finish finish)`.
Each string is a block ID template, where `*` matches a non-empty run without `:`, and the captures fill the other
templates' `*` in order. `Finish` is `SHOVEL`, `HOE`, `AXE` or `WATER`, or none.

```
minecraft:grass_block        placed with minecraft:dirt   same minecraft:dirt
minecraft:dirt               placed with minecraft:dirt   same minecraft:grass_block
minecraft:dirt_path          placed with minecraft:dirt   finish SHOVEL from minecraft:dirt, minecraft:grass_block,
                                                          minecraft:podzol, minecraft:mycelium, minecraft:coarse_dirt, minecraft:rooted_dirt
minecraft:farmland           placed with minecraft:dirt   finish HOE from minecraft:dirt, minecraft:grass_block, minecraft:dirt_path
*:stripped_*_log             placed with *:*_log          finish AXE from *:*_log
*:stripped_*_wood            placed with *:*_wood         finish AXE from *:*_wood
*:stripped_*_stem            placed with *:*_stem         finish AXE from *:*_stem
*:stripped_*_hyphae          placed with *:*_hyphae       finish AXE from *:*_hyphae
minecraft:stripped_bamboo_block  placed with minecraft:bamboo_block  finish AXE from minecraft:bamboo_block
minecraft:*_concrete         placed with minecraft:*_concrete_powder  finish WATER from minecraft:*_concrete_powder
```

`BlockEquivalents.of(String plannedId)` returns `Equivalence(String placedWith, Set<String> same,
Set<String> finishFrom, @Nullable Finish finish)`, and `Equivalence.self(id)` (placed with itself, nothing else)
when no rule matches. `Equivalence.accepted(plannedId)` is the ordered, de-duplicated list of block IDs whose
carried items count toward the planned block's have: the planned block, `placedWith`, then `same`, then
`finishFrom`, in rule order (see D4). Lookups are memoised per ID. The game-facing side drops IDs the registry does not know, and
falls back to `self` when `placedWith` is unknown or has no item. This is the spec's "rows the game cannot use".

The farmland row also accepts a dirt path, since a hoe tills dirt paths too. The path row lists every block a
shovel flattens into a path. Planned podzol, mycelium, coarse dirt and rooted dirt have no row and stay themselves. The suffix list keeps the stripped
rule off unrelated modded `stripped_*` blocks.

Alternatives:
- Block tags (`minecraft:logs`) and the game's own `AxeItem` stripping map. Rejected: neither is available to unit
  tests, the stripping map is protected, and tags don't say which block is the stripped one.
- One row per concrete colour and per wood type. Rejected: 16 + 40 near-identical rows, and modded wood would be
  missed.

### D2. State properties: compare shared properties, never convert

The ghost always shows the planned block. The table changes what is counted and what is accepted, not what is
drawn, so no block state is ever built from another block's state. Orientation is compared through `CellKeys`'
orientation key, which is built from `Property` identity. That gives the carry-over the rows need for free:
- A stripped log and its log both have `AXIS`, so the axis is compared, and a log on another axis is wrong
  orientation.
- Dirt, grass, dirt paths, farmland and concrete (powder) have no orientation properties. `snowy` and `moisture`
  are not in the list, so they never matter.

A future row whose blocks don't share property objects but should be compared (for example a modded block with its
own `axis` property) needs no new mechanism: its orientation keys simply won't match, and it shows as wrong
orientation. Such rows are reviewed when they are added.

Alternative: map states property by property, as swaps do. Rejected: nothing in scope needs it, and it would force
a resolved `BlockState` per relation.

### D3. Comparison: a NEEDS_FINISHING state and the extended `Expected`

- `CellState` gains `NEEDS_FINISHING` as its last constant, so stored ordinals of the others don't change and the
  byte per cell is enough. Comparison results are not saved, so nothing migrates.
  - `showsGhost()` is false, so the mask hides the ghost.
  - `needsItem()` is false, so its demand leaves the material counts.
  - A new `boxed()` is `mistake() || this == NEEDS_FINISHING`. `ComparisonState.set` marks box sections on
    `boxed()`, and `MistakeBoxes` draws boxes for `boxed()` states. `mistake()` keeps meaning red-family states.
  - Progress counts `MATCHING` only, so finishing is not done without any change to the counters.
- `Expected` gains `int[] same`, `int[] finishFrom` and `int finish` (the `Finish` ordinal, or -1). `CellKeys.expected`
  fills them from `BlockEquivalents.of(BlockStates.id(entry))`, where the entry is the swapped one from `GhostView`,
  so swaps apply first. The arrays are sorted registry ids, built once per palette entry.
- `classify` for a `BLOCK` expectation:
  1. air → `MISSING`
  2. the found block is the planned block or in `same` → the existing orientation, water, `MATCHING` order
  3. the found block is in `finishFrom` → `WRONG_ORIENTATION` or `WRONG_WATER` first, else `NEEDS_FINISHING`
  4. otherwise → `WRONG_BLOCK`

Alternative: a separate per-cell "finishing" flag next to the state. Rejected: it doubles state handling for one
value that fits in the existing byte.

### D4. Material counts: demand and have per planned block

Need and have are kept per planned block, the block a palette entry shows as after its swap, not per item.
Planned blocks that merely share a placing item (grass and dirt paths with dirt, concrete with its powder) accept
different carried items, so per-item numbers would be wrong for one of them.

- **Demand.** `WorldComparison` indexes the distinct planned blocks of its palette, and each entry's `Demand` is
  that planned-block index and an amount. The amount rules (slab type, candles, upper halves) still read the
  planned state. `MaterialCounts.needed` is per layer and per planned block instead of per item; `set` moves
  demand when `needsItem` changes, as today. Planned blocks no item places stay in the can't-carry array.
- **Accepted items.** Per planned block, `WorldComparison` keeps its placing item (the item of `placedWith`) and
  its accepted items: the items of `Equivalence.accepted`, in that order, dropping blocks the registry doesn't
  know or that have no item. So its own item comes first, then the placing item, then the items of `same`, then
  those of `finishFrom`.
- **Sharing.** A new game-free `placement/CarriedShares.share(int[] need, int[][] accepts, IntUnaryOperator
  carried)` gives out the carried stock:
  1. Planned blocks are served one after another, fewest accepted items first, ties by block ID.
  2. Each takes from its accepted items in their order, up to its need.
  3. A carried item is taken at most once, so nothing is double-counted.

  It returns `given[block]` and `left[item]` (carried stock nobody took). The order makes the common cases exact:
  powder is served before concrete, dirt and grass before farmland, farmland before paths.
- **Material list (per item).** `Materials.list` sums `needed` over the visible layers, runs `share`, and builds
  one entry per placing item: needed is the sum over its planned blocks, had is their `given` plus the item's
  `left`, and short is needed minus their `given`. That equals needed minus had, never below 0, because an item
  is only left over when every planned block it places is served.
- **Finishing.** `MaterialCounts` gains a third array, `finishing`, per layer and per planned block with a
  `Finish`. `WorldComparison.set` adds or removes 1 when the state enters or leaves `NEEDS_FINISHING`. `Totals`
  gains `finishing`, and `WorldComparison.finish(index)` gives the block's `Finish`.

Alternative: count carried items toward every item they could stand in for, per item. Rejected: carried concrete
would count toward planned powder, and one stack could cover two rows at once.

### D5. Overlay and materials screen

- `PlacementOverlay` sums `Totals.finishing` over the visible layers per `Finish` and adds one yellow line per step
  with a count above 0, after the mistake counts: "3 blocks need a shovel", "need a hoe", "need an axe",
  "need water". For a crosshair target in `NEEDS_FINISHING` it shows the expected and the found block as for a
  mistake, plus the step.
- `MaterialRows` reads the numbers of the planned block a row shows as, not of its item: needed and short from
  the shared-out result, had as that block's `given` plus its placing item's `left`. Rows showing as the same
  planned block (two blocks swapped to one) share numbers; a dirt path row and a grass row do not, although both
  show dirt as the item. The leftover can show in more than one row, but no short counts it. Rows of planned
  blocks with nothing needed show the leftover as had, which replaces `PlacementSession.carried` there.
- `MaterialRows.Row` gains `finishing` (count) and `finish` (step) from the summed finishing totals of the shown-as
  block. The screen adds a "Finish" column while locked, with the count and the step name (or an icon of the
  tool), and empty when 0. `MaterialsScreen.itemOf` goes through the table, so a dirt path row shows dirt as its
  item.
- The finishing numbers follow the visible layers, like the shortage lines and the Need column. The colour counts
  for mistakes stay whole-placement as specified in `world-comparison`.

### D6. In-hand filter

`GhostRenderer.inHand` maps the held and the hotbar items to sets of planned blocks instead of single blocks. A
held item highlights the planned blocks it places and every planned block whose accepted items (D4) include it,
so the filter and the have use one relation: dirt highlights grass, dirt, path and farmland, podzol highlights
paths, white concrete only white concrete. The map is built once per comparison from its palette, next to the
`Expected` terms (`WorldComparison.highlights(Block)`), so a frame does a map lookup per hotbar slot as today.
Unlocked placements have no in-hand filter, so no comparison means no change.

## Risks / Trade-offs

- [Yellow boxes can be mistaken for the orange wrong orientation] → the colour is tuned in `runClient` next to
  orange, and the overlay names the step under the crosshair. Changing the colour later is one constant.
- [Grass on planned dirt is matching, so a lawn that should stay dirt for a path later is invisible] → accepted: the
  path row marks it once the path is planned.
- [Sharing carried items is greedy, so an unusual mix of carried items can show a shortage that another split
  would avoid] → the order serves the most constrained planned blocks first and takes each block's own item
  first, which is exact for concrete with powder and for dirt, grass, paths and farmland. The order is
  unit-tested and stated in the spec.
- [Materials screen rows no longer share numbers by item, as `material-swaps`' "Needs on a locked placement" asks]
  → on archive it is superseded by `client/block-equivalents` "Needs per planned block in the materials screen".
  Rows of one planned block still share.
- [The `stripped_*` templates can match modded blocks that are not stripped wood] → limited to the log, wood, stem
  and hyphae suffixes, and dropped when the unstripped block is unknown or has no item.
- [`classify` gets longer per cell] → two small sorted-array searches, only for found blocks that differ from the
  planned one. Most of those are rows without equivalents and empty arrays.

## Migration Plan

Nothing is saved. Comparisons are rebuilt on lock and on join, so existing placements pick up the table at once.
Archive after `material-swaps` and `ghost-opacity-wheel`, in that order. Check then that `material-swaps`' "Needs on
a locked placement" is superseded by the per-planned-block numbers and the Finish column (see proposal.md,
Capabilities).

## Open Questions

- The exact yellow and whether the Finish column shows the step as a tool icon or as text. Both are tuned in
  `runClient`.
