# Design

## Context

For motivation see proposal.md, Why. For behaviour see the deltas for `client/world-comparison`,
`client/material-list`, `client/ghost-targeting` and `client/placement-remote`.

The current state this builds on (after `block-equivalents` and `ghost-targeting`):
- **`CellState`** (game-free), stored as its ordinal in one byte per cell: `UNKNOWN`, `IGNORED`, `MATCHING`,
  `MISSING`, `WRONG_BLOCK`, `WRONG_ORIENTATION`, `WRONG_WATER`, `EXTRA`, `NEEDS_FINISHING`. `showsGhost()` drives
  the ghost mask and targeting, `needsItem()` the material counts, `boxed()` the box sections.
- **`Expected` / `Found`:** block id, orientation key, waterlogging, and for `Expected` the equivalents' `same`,
  `finishFrom` and `finish`. `classify` decides: missing, then identity (same → done = matching, finishing source →
  done = needs finishing, else wrong block), then orientation, then water, then done.
- **`CellKeys.ORIENTATION`** includes `SLAB_TYPE`, so top, bottom and double are three orientations. The same list
  drives `WorldComparison.details`, the property text in the HUD and the overlay.
- **`WorldComparison`** keeps one `Demand` per palette entry with the full amount (candles, pickles, layers, double
  slab = 2) and moves it in or out of `MaterialCounts` in `set` when `needsItem()` flips. Nothing remembers anything
  per cell besides the state byte.
- Minecraft is not on the unit test classpath; `CellState`, `ComparisonState`, `MaterialCounts` and `Demand` are
  game-free.

## Goals / Non-Goals

**Goals:**
- Amount is one more comparison step inside `classify`, game-free and unit-tested, including the slab rules.
- The material counts stay exact under any sequence of state and amount changes, with no full rescan.
- The state stays one byte per cell; the extra memory is only for cells that are not enough yet.

**Non-Goals:**
- Other "amount-like" properties: cake bites, composter level, respawn anchor charges, flower pot contents, turtle
  eggs, pink petals and leaf litter. They can join the amount list later (see Open Questions).
- Candle `lit` state; it stays ignored like any other non-orientation property.

## Decisions

### D1. Amount and slab half in the comparison terms

`Expected` and `Found` get two ints: `amount` and `half`.
- `amount`: `CANDLES`, `PICKLES` or `LAYERS` when the block has one, 2 for `SLAB_TYPE=DOUBLE`, 1 otherwise. One
  game-facing `CellKeys.amount(BlockState)` computes it, and `WorldComparison.demand` uses the same function, so the
  planned amount and the demand cannot disagree.
- `half`: 1 for a bottom slab, 2 for a top slab, 0 for a double slab and every other block.
- `SLAB_TYPE` leaves `CellKeys.ORIENTATION`. `details` still prints it, from a separate shown-properties list, so
  the HUD keeps saying `type=bottom` / `type=double`.

The existing `Expected.block(...)` and `Found.block(...)` factories keep their signatures with amount 1 and half 0,
so blocks without amounts behave exactly as before.

*Alternative:* keep `SLAB_TYPE` in the orientation key and special-case slabs in `classify` by decoding the key.
Rejected: the key is an opaque interned int and `classify` is game-free.

### D2. The check order

`classify` for a planned block:
1. world air → `MISSING`
2. identity: same or accepted → done = `MATCHING`; finishing source → done = `NEEDS_FINISHING`; else `WRONG_BLOCK`
3. orientation key differs → `WRONG_ORIENTATION`
4. both halves non-zero and different (top against bottom single slab) → `WRONG_ORIENTATION`
5. found amount > planned → `TOO_MANY`
6. found amount < planned → `INCOMPLETE`
7. waterlogging differs → `WRONG_WATER`
8. done

Amount comes before water, not after: a double slab cannot be waterlogged, so a waterlogged bottom slab under a
planned double would otherwise show "must not be waterlogged", although placing the second slab fixes both; and a
double slab where a waterlogged single is planned would show "needs water", which can never be done without first
breaking it. For candles and pickles the order makes no practical difference. Finishing stays last: the finishing
sources (dirt, logs, concrete powder) have no amount, and when a block is both short and unfinished, completing it
first is the natural order.

Slabs: a single slab under a planned double has half 1 or 2 against 0, so step 4 never fires, and its amount 1 < 2
gives `INCOMPLETE` whichever half it fills: a slab can always be placed into the free half. A double slab where a
single is planned has half 0, skips step 4 and is `TOO_MANY` (2 > 1).

### D3. Two new states: `INCOMPLETE` and `TOO_MANY`

Both are appended after `NEEDS_FINISHING`, so existing ordinals keep their meaning (`INCOMPLETE`, then `TOO_MANY`).
- `INCOMPLETE`: `showsGhost()` true (mask bit set, ghost drawn, targetable, pickable through the existing
  `TargetRules.drawn` and `GhostPick`), `needsItem()` true, not `boxed()`, not a `mistake()`, not counted as done.
- `TOO_MANY`: a `mistake()` and `boxed()`, orange box (the wrong orientation colour), `needsItem()` false.

*Too many as its own state rather than `WRONG_ORIENTATION` with a hint:* every consumer that would need the hint
(HUD line, overlay count, overlay target line) is a switch over the state. Reusing `WRONG_ORIENTATION` would force
each of them to re-derive the reason from the found and planned block states, and the overlay's "turned" count
would count positions that are not turned. A separate state costs one enum value (the byte has room for 256), one
case in `MistakeBoxes.color` and one count in the overlay. Everything else (box, no ghost, no demand, not done)
follows from `mistake()` / `boxed()` as for wrong orientation.

### D4. Per-cell demand for `INCOMPLETE`

A position's contribution to `MaterialCounts` becomes a function of its state and the amount found:
- `needsItem()` and not `INCOMPLETE` → the entry's full `Demand.amount`
- `INCOMPLETE` → `Demand.amount - found`, never below 0
- otherwise → 0

`Demand.amountFor(CellState, int found)` computes it (game-free).

`ComparisonState` keeps a sparse `HashMap<Integer, Integer>` from cell index to found amount, holding only
`INCOMPLETE` cells. `set(index, state, found)` records the amount when the new state is `INCOMPLETE` and drops the
entry otherwise; `found(index)` returns the recorded amount, or 0. `WorldComparison.set` reads the old found amount
before `set` and calls `MaterialCounts.move(layer, demand, old, oldFound, next, nextFound)`, which adds the
difference of the two contributions. `set` also has to run when only the found amount changes (1 → 2 candles): the
state stays `INCOMPLETE` but the map entry and the counts change, while the mask, totals and boxes do not.

*Alternative:* store the demand added per cell. Rejected: the found amount is smaller to reason about, and the
demand is recomputable from the palette entry.

*Alternative:* a dense `byte[]` of found amounts per cell. Rejected: doubles the per-cell memory for a state that is
rare (a handful of candles or slabs in a build).

The bookkeeping (state change, found amount change, back to unknown when a chunk unloads) is unit-tested through
`ComparisonState` and `MaterialCounts.move` together, replaying the transitions `WorldComparison.set` performs.

### D5. Text

- HUD and Jade (through `TargetHud.lines`): `INCOMPLETE` → `"%s/%s"` (found, planned) in the
  normal text colour; `TOO_MANY` → the same `"%s/%s"` in orange. `TargetLines` returns the key and
  colour; `TargetHud.stateLine` fills the amounts from the target's planned and found block states with
  `WorldComparison.amount(BlockState)`.
- Overlay: a `"%s too many"` count in orange after the existing counts; for a crosshair target that is `INCOMPLETE`
  or `TOO_MANY`, the expected and found lines plus the same amount line as the HUD.

Minecraft has no plural forms, so the text avoids pluralising the block name; the HUD's first line already names it.

## Risks / Trade-offs

- [Ghost drawn over a real block for `INCOMPLETE`] The ghost of 3 candles overlaps the 1 real candle. → It is the
  intended cue ("this position is not done"); the ghost is translucent and the HUD says how many more.
- [Snow layers grow and shrink by themselves in snowy biomes] Positions can flip between `INCOMPLETE`,
  `MATCHING` and `TOO_MANY` without the player. → Correct per the rules; the background rescan keeps the counts
  exact through D4.
- [A modded block with `CANDLES`, `PICKLES` or `LAYERS`] It gets the amount rule. → Intended; the properties are
  shared objects, matched by identity as with orientation.
- [Sparse map growth] One entry per incomplete cell. → Bounded by the cells with amounts, which are few.

## Open Questions

- Whether cake bites, turtle eggs, pink petals or leaf litter should become amounts. Each is one property in the
  amount list plus a scenario; nothing in this design changes.
