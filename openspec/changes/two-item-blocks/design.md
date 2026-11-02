# Design

## Context

The comparison works per palette entry. `CellKeys` turns a block state into `Expected` and `Found` terms (block id,
orientation key, amount, slab half, waterlogging, plus the equivalents row's `same` and `finishFrom` ids).
`CellState.classify` maps a pair to a state. `WorldComparison.indexPlanned` numbers the planned blocks that an item
places. Each palette entry gets one `Demand`: a planned block index with an amount, or a can't-carry block.
`MaterialCounts` keeps per-layer totals of those demands, and `Demand.amountFor` gives what a state still needs.
`GhostPick` picks from `comparison.accepts(plannedIndex)`.

A potted plant, a candle cake and a filled cauldron have no item (`asItem()` is air). So `indexPlanned` skips them,
`demand` makes them can't carry, and the pick target has no planned index. A planned block maps to exactly one item
line, but these blocks need two different items.

Mistake boxes are drawn with `GhostPipeline.MISTAKE_BOX`: depth test less-or-equal, no depth write.

## Goals / Non-Goals

**Goals:**
- Two-item blocks take part in the comparison, material counts, pick and HUD with the existing states and colours; no
  new cell state.
- The material list adds two-item demands into the ordinary item lines (flower pots, dead bushes), so sharing,
  shortages and the materials screen work unchanged.

**Non-Goals:**
- Filling cauldrons with bottles, or comparing their level.
- Lit candles on candle cakes. `lit` is not an orientation property and stays ignored, as for candles.
- Contents placed by other means (a plant in a modded planter block).

## Decisions

### D1: A two-item table next to the equivalents, built from the game where possible

`TwoItemBlocks.of(Block)` returns `(base Block, content Block, content Item)` or null, cached per block. Sources:
- **Flower pots:** `FlowerPotBlock` with a non-air `getPotted()` → base `getEmptyPot()`, content `getPotted()`. This
  covers NeoForge's `addPlant` registrations from mods.
- **Candle cakes:** for every `CandleBlock` in the registry, `CandleCakeBlock.byCandle(candle).getBlock()` maps back
  to the candle. The map is built once.
- **Cauldrons:** a fixed list: `water_cauldron` → water bucket, `lava_cauldron` → lava bucket,
  `powder_snow_cauldron` → powder snow bucket, all on the cauldron. The content block is the fluid or powder snow
  block, so it can be numbered like other planned blocks.

If the content item is air, `of` returns null and the block stays as itself (spec: content without an item).

*Alternative:* id templates in `BlockEquivalents.RULES` (`*:potted_*` → `*:*`). Rejected: ids don't line up
(`potted_azalea_bush` holds `azalea`), and mod pots would be missed.

### D2: Comparison through a base id on `Expected` and `Found`

`Expected` gets `base`, the registry id of the planned block's empty base, or -1. `Found` gets `base`, the registry id
of the world block's empty base when it is a two-item block, or -1. `CellKeys` fills both from `TwoItemBlocks`.
`classify` extends the block check:
- `found.block == expected.base` → `INCOMPLETE`. The orientation check still runs before it, since pots and cakes
  have none and cauldrons none.
- `found.base == expected.block` → `TOO_MANY`.
- Anything else that differs stays `WRONG_BLOCK`. This includes another content: a different block id with a different
  or equal base.

An eaten cake is not a valid base: `CellKeys` only reports the cake as found when `bites == 0`. With a bite, it
classifies as a plain other block, so wrong block. The incomplete found amount is 0, so `Demand.amountFor` stays
consistent.

*Alternative:* a new cell state "needs content". Rejected: the user expects the orange not-enough-yet look, and the
existing state already keeps the ghost and blocks progress.

### D3: A demand with a base part and a content part

`Demand` gets an optional second part: `content` (a planned block index, or -1). For a two-item palette entry,
`demand` returns `planned(baseIndex, 1)` with `content = contentIndex`. `Demand.amountFor` becomes per part:
- **Base part:** needed in missing, wrong block and unknown, and not in incomplete.
- **Content part:** needed in missing, wrong block, unknown and incomplete.

`MaterialCounts.add`, `remove` and `move` apply both parts. `indexPlanned` adds the base block (flower pot, cake,
cauldron) and the content block as planned blocks even when the schematic never places them on their own, with their
own items as placing items. Their lines then add up with plain flower pots or dead bushes elsewhere in the placement.

*Alternative:* store two demands per palette entry in parallel arrays. Rejected: every caller of `demands[entry]`
would need to know about it. A two-part `Demand` keeps `MaterialCounts.move` as the one place that does the
arithmetic.

### D4: The pick and the HUD follow the state

`WorldComparison` exposes `contentOf(entry)` next to `plannedOf(entry)`. `plannedOf` points at the base for two-item
blocks, so targeting finds a planned index. `GhostPick.decide` uses `contentOf` when the target's state is
`INCOMPLETE` and the entry has a content, and `plannedOf` otherwise. `PickDecision` is unchanged.

The in-hand filter's highlights map the content block to its two-item blocks as well, so holding a dead bush
highlights potted dead bushes.

`TargetHud.stateLine` and `PlacementOverlay` use a new text for two-item positions:
- **Not enough yet:** "Needs %s", with the content item name.
- **Too many:** "%s must go".

They no longer use the amount text there.

### D5: Boxes behind blocks drawn in a second, weaker pass

`MistakeBoxes.draw` draws the same section buffers twice:
- **First pass:** `MISTAKE_BOX_HIDDEN`, depth test greater-than, colour modulator alpha 0.5.
- **Second pass:** the existing less-or-equal pipeline at full strength.

Neither pass writes depth. Each fragment lands in exactly one pass, so hidden boxes are weaker and visible ones
unchanged.

*Alternative:* no depth test at all. Rejected: visible and hidden boxes would look the same, and the player loses
which side of a wall a mistake is on.

## Risks / Trade-offs

- [A base block planned on its own, such as a plain flower pot, now gets a too-many check against filled pots] →
  Only blocks from the table qualify, and the HUD names what must go.
- [Content blocks numbered as planned blocks show up in the materials screen as rows the schematic never names
  directly (dead bush, water)] → That is the intent: they are what the player has to bring. Rows still sort by item.
- [Water and lava as planned content blocks next to the same blocks as can't-carry positions] → Planned and
  can't-carry indices are separate lists, so a water source still counts under can't carry and a water cauldron as a
  water bucket.
- [Hidden boxes of large mistake areas can clutter the view through terrain] → Half strength. Boxes stay limited to
  the visible layers.
