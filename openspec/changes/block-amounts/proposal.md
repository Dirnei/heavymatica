# Proposal

## Why

The comparison only looks at a block's identity, its orientation and its waterlogging. Candles, sea pickles, snow
layers and double slabs also have an amount, and it is ignored: 1 candle where 3 are planned counts as matching, so
the ghost hides, the material list asks for nothing and progress counts it as done. 4 candles where 3 are planned
match too. A single slab where a double slab is planned is shown as turned (orange), and the list does not ask for
the second slab, because slab type is treated as an orientation.

## What Changes

- **Amounts are compared.** A block's amount is its candles, pickles or snow layers, or 1 for a single slab and 2
  for a double slab; every other block has 1. It is compared after block identity, orientation and the block
  equivalents' same and finishing rules.
- **Not enough yet (new state).** Same block, same orientation, fewer than planned:
  - the ghost keeps showing, as for a missing block, and the position can be targeted and picked like one
  - the material list asks only for the rest, such as 2 candles where 1 of 3 stands
  - progress does not count it
  - the targeting HUD, Jade and the overlay say how many more are needed, such as "2 more (1 of 3)"
- **Too many (new state).** More than planned is a mistake shown in orange like a wrong orientation, with its own
  count in the overlay and a hint such as "Too many: 4 instead of 3". The player breaks and replaces the block.
- **Slabs.** Slab type stops being an orientation between a single and a double slab:
  - a top or bottom slab where a double is planned is not enough yet, "1 more", since a slab can be placed into
    either free half
  - a double slab where a single is planned is too many
  - top against bottom between two single slabs stays a wrong orientation
- **The first fix decides** in the order wrong block, wrong orientation, too many, not enough yet, wrong water,
  needs finishing. A double slab cannot be waterlogged, so the amount comes before the water.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `client/world-comparison`:
  - "Cell states", "Mistake states", "Orientation properties", "The first fix decides the state" and "Showing the
    states": the amount, the not enough yet and too many states, and slab type
  - new "Block amounts" requirement
- `client/material-list`: "Positions that need an item": a position with not enough yet needs the rest.
- `client/ghost-targeting` (added by `ghost-targeting`): "Mistakes are targeted as the planned block" and "Target
  state in the HUD": too many and not enough yet.
- `client/placement-remote`: new "Amounts in the overlay" requirement: the too many count and the amount line for
  the crosshair target.

## Impact

- **Mod code only (`src/mod/heavymatica`):**
  - `placement`: `CellState` (two new states, amount and slab half in `Expected` / `Found`, `classify`),
    `CellKeys` (amounts, slab type out of the orientation key), `ComparisonState` (the found amount of positions
    with not enough yet), `MaterialCounts` / `Demand` (a position's contribution by state and found amount),
    `WorldComparison` (per-cell demand bookkeeping), `MistakeBoxes` (orange for too many)
  - `targeting`: `TargetLines`, `TargetHud` (and through it the Jade plugin)
  - `remote/PlacementOverlay`: the too many count and the amount line
  - language keys; the dev comparison log shows the new totals
- No saved data, cloud, API or network changes. The cell state stays one byte per position.
- **Depends on `ghost-targeting`** and what it builds on. Archive order: `material-swaps` → `ghost-opacity-wheel` →
  `block-equivalents` → `ghost-targeting` → `block-amounts`.
