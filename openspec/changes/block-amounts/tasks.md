# Tasks

## 1. Comparison (game-free)

- [x] 1.1 Append `INCOMPLETE` and `TOO_MANY` to `CellState` with `showsGhost()` / `needsItem()` true for `INCOMPLETE`, `mistake()` / `boxed()` true for `TOO_MANY` (D3); verify `CellStateTest` for the flags of both states and that the existing ordinals are unchanged
- [x] 1.2 Add `amount` and `half` to `Expected` and `Found`, keeping the old factories at amount 1 and half 0, and extend `classify` with the half and amount steps in the order of D2; verify `CellStateTest` for 1, 3 and 4 candles against 3 planned, snow 2 and 5 against 5, a bottom and a top slab under a planned double (incomplete), a double under a planned bottom (too many), top against bottom singles (wrong orientation), a waterlogged bottom slab under a planned double (incomplete), 3 dry pickles against 2 waterlogged (too many), 1 dry pickle against 2 waterlogged (incomplete), a wrong facing with too many (wrong orientation), a finishing source with an amount short (incomplete), and blocks without amounts behaving as before
- [x] 1.3 Add the sparse found-amount map to `ComparisonState` with `set(index, state, found)` and `found(index)`, setting nothing else when only the amount changes (D4); verify `ComparisonStateTest` that entering `INCOMPLETE` sets the mask bit and keeps `matching` unchanged, that the found amount is recorded, updated in place and dropped on leaving `INCOMPLETE`, and that `TOO_MANY` marks the box section and clears the mask bit
- [x] 1.4 Add `Demand.amountFor(state, found)` and `MaterialCounts.move(layer, demand, old, oldFound, next, nextFound)` (D4); verify unit tests for the contribution of every state, and a replayed sequence on one 3-candle cell (unknown 3 → incomplete 1 → 2 → incomplete 2 → 1 → matching 0 → too many 0 → missing 3 → unknown 3) and a double slab cell (missing 2 → incomplete 1 → matching 0) whose needed totals are exact at every step

## 2. Game side

- [x] 2.1 In `CellKeys`, take `SLAB_TYPE` out of `ORIENTATION`, add `amount(BlockState)` and the slab half, and fill them into `Expected` and `Found`; keep `SLAB_TYPE` in `WorldComparison.details`; use the shared amount in `WorldComparison.demand` (D1); verify the build passes
- [ ] 2.2 In `WorldComparison`, pass the found amount through `compare` and `compareSection` into `set`, which reads the old found amount and calls `MaterialCounts.move`; give `MistakeBoxes.color` orange for `TOO_MANY` (D3, D4); verify the build passes, and in `runClient` (in-game check) with a test schematic of 3 white candles, 3 sea pickles in water, snow with 5 layers, a double stone slab and a bottom stone slab: 1 candle keeps the ghost and the Materials screen's Need shows 2 candles, a second candle makes it 1, a third hides the ghost; 4 candles show an orange box; a top or bottom slab under the double keeps the ghost with Need 1 stone slab; a double slab on the bottom slab shows an orange box; progress only counts complete positions (runClient check pending)

## 3. Text

- [ ] 3.1 Add the `INCOMPLETE` and `TOO_MANY` lines to `TargetLines` and fill their amounts in `TargetHud.stateLine` with `WorldComparison.amount`, with the language keys (D5); verify `TargetLinesTest` for the keys and colours of both states, and in `runClient` (in-game check) the HUD "Two more candles", "Too many candles" and "Half a double slab" scenarios, the Jade tooltip showing the same line, and middle click on 1 of 3 candles picking white candles (runClient check pending)
- [ ] 3.2 Add the too many count and the amount line for an incomplete or too many crosshair target to `PlacementOverlay`, with the language keys (D5); verify the build passes, and in `runClient` (in-game check) the "Too many count", "Looking at too many candles" and "Looking at one of three candles" scenarios (runClient check pending)
- [ ] 3.3 Confirm the dev comparison log lists `INCOMPLETE` and `TOO_MANY` totals (it iterates `CellState.values()`); verify in `runClient` (in-game check) that both totals appear for the test schematic (runClient check pending)

## Workflow follow-up

- Archive `material-swaps`, `ghost-opacity-wheel`, `block-equivalents` and `ghost-targeting` (in that order) before
  archiving this change.
- Commit the implementation once all tasks are done.
- Archive the change with `/opsx:archive` when asked.
