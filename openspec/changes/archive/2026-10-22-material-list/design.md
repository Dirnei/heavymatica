# Design

## Context

For motivation see proposal.md, Why. For behaviour see `specs/client/material-list` and the delta for
`client/placement-remote`.

This change builds on `world-comparison`:
- `WorldComparison` holds one locked placement's expected palette entries and its `ComparisonState`.
- `ComparisonState` stores a `CellState` per position, plus per-layer counters kept up to date by diffing old and
  new state in `set`.
- `Comparisons` scans every tick.
- `PlacementOverlay` already reads the selected placement's comparison.
- `Layers.visible` gives the visible layer range.

What the codebase offers for items:
- `PlacementSession.placedBlock(ItemStack)` maps an item to its block, for the in-hand filter.
- `SchematicModel.blockCounts()` counts per block ID, properties ignored. That's not enough here: the item count
  depends on properties such as slab type and candle count.
- NeoForge 26.1 registers `Capabilities.Item.ITEM` (a `ResourceHandler<ItemResource>` with an `ItemAccess`
  context) for shulker boxes (`CONTAINER` component) and bundles (`BUNDLE_CONTENTS`). Mods that keep their contents
  on the stack register it too.

Unit tests run without Minecraft classes, so counting logic stays game-free and the block-to-item rules are checked
in `runClient`.

## Goals / Non-Goals

**Goals:**
- Changing the layer window, climbing in follow feet or picking up items never rescans the placement. The list is a
  sum over counters that already exist.
- A cell's demand changes only when its comparison state changes, in O(1).
- One code path reads every carried container.

**Non-Goals:**
- Exact counts for blocks with unusual item rules beyond the listed exceptions, such as cake slices or turtle eggs
  with an age. Those count as one item per position.
- Remembering containers in the world.

## Decisions

### D1. Demand per palette entry

When a comparison is created, every expected palette entry gets a `Demand`. A `Demand` is one of:
- **none**: air, ignored, the upper half of a door or tall plant, the head of a bed
- **item**: an item index plus an amount
- **can't carry**: a block index plus an amount

Items and blocks are numbered per comparison, so counters can be plain `int` arrays.

Rules, in order:
1. `DOUBLE_BLOCK_HALF = upper` or `BED_PART = head` → none.
2. `block.asItem()` is air → can't carry, amount 1. This covers water, lava, fire, portals and piston heads.
3. `SLAB_TYPE = double` → item, amount 2.
4. `CANDLES`, `PICKLES` or `LAYERS` (snow) → item, amount from the property.
5. Otherwise → item `block.asItem()`, amount 1.

Alternative: map per cell at scan time. Rejected: it repeats the same work for every position of the same entry.

### D2. Counting needed items per layer

`CellState` gains `needsItem()`, which is true for MISSING, WRONG_BLOCK and UNKNOWN.

A new game-free `MaterialCounts` holds `needed[layer][item]` and `cantCarry[layer][block]`. It offers `add`,
`remove` and `sum(from, to)`, which returns the totals per item over a layer range.

`WorldComparison` keeps it current:
- When it is created, every non-air position starts UNKNOWN and needs its item. The creation loop that already walks
  every cell to count progress also adds each cell's demand.
- `ComparisonState.set` returns the previous state. Whenever `needsItem()` flips, `WorldComparison` adds or removes
  that cell's demand at its layer.

Memory is `height × distinct items` ints. A 256-layer placement with 300 distinct items needs about 300 KiB.

Alternative: count by walking the cells in the visible layers on demand. Rejected: follow feet changes the range
whenever the player moves up or down, and walking 16M cells is too slow for that.

### D3. What the player has

A game-facing `CarriedItems` walks the player's slots: inventory, hotbar and offhand.
- For each stack it adds the stack's count.
- It then asks `stack.getCapability(Capabilities.Item.ITEM, ItemAccess.forPlayerSlot(player, slot))` and adds every
  resource in that handler.
- It recurses into contained stacks through `ItemAccess.forHandlerIndex(handler, index)`, up to depth 3.

This one path covers shulker boxes, bundles and capability-based backpacks.

The walk runs at most every 10 ticks, and when the screen opens. A full inventory with containers is a few thousand
stacks at most.

Alternative: read the `CONTAINER` and `BUNDLE_CONTENTS` components directly. Rejected: NeoForge already bridges
them to the capability, and a second path would count shulker boxes twice.

### D4. The list

`MaterialList.of(counts.sum(range), carried)` builds rows of item, needed, had and short. It sorts by short
descending, then needed descending, then item ID, and keeps the can't-carry rows separate.

It is game-free apart from the item keys. The rows are generic over the key type so the sorting and short-clamping
are unit-testable with strings.

### D5. Overlay and screen

- The overlay adds up to three shortage lines under the mistake counts, then either "Nothing short" or "+N more (K)".
  Item names come from `Item.getName`.
- A new `RemoteKeys.MATERIALS` key, default K, in the `HOLDING_REMOTE` context:
  - It opens `MaterialListScreen` for the selected locked placement.
  - Otherwise it shows "Lock the placement first" in the action bar.
- `MaterialListScreen` is a plain scrolling list with an item icon and the columns item, needed, have and short.
  Short is shown in red when above 0. A "Can't carry" header follows. The rows are rebuilt every 10 ticks from the
  live counts, so the screen follows the inventory and the world while open.
- The screen doesn't pause the game in singleplayer, so the scanner keeps running.

## Risks / Trade-offs

- [`ItemAccess.forPlayerSlot` is built for interaction. A mod's handler could assume server-side use or try to
  modify the stack] → the walk only reads resources and amounts and never calls insert or extract. A handler that
  throws is caught and that stack counts as itself only.
- [A backpack that exposes both a component and a capability could be counted twice] → only the capability is read,
  so there is a single source per stack.
- [Unknown cells count as needed, so right after locking a large placement the list is too long until the first
  scan pass ends] → accepted, the overlay already says "Scanning...". Too much is safer than too little.
- [Block-to-item edge cases not covered by D1 (cake, turtle eggs, multi-block modded blocks)] → they count one item
  per position. The rules table can grow later without spec changes.

## Migration Plan

None. Nothing is saved, and the key is new. Archive `world-comparison` before this change, since both modify the
overlay requirement and this delta builds on that change's text.
