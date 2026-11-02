# Tasks

## 1. Two-item table

- [x] 1.1 Add `TwoItemBlocks` in `placement`: base block, content block and content item for flower pots (`getPotted`/`getEmptyPot`), candle cakes (the inverse of `CandleCakeBlock.byCandle` over all `CandleBlock`s) and the three filled cauldrons; null when the content has no item. Cache it per block (D1). Verify with a game-free unit test of the cauldron list and the content-without-item fallback, and with a `HEAVYMATICA_DEV_SCREEN` step that logs `potted_dead_bush`, `potted_azalea_bush`, `red_candle_cake`, `candle_cake` and `water_cauldron` with their base and content.

## 2. Comparison

- [x] 2.1 Add `base` to `CellState.Expected` and `CellState.Found`. Fill them in `CellKeys`, giving a cake a base only at `bites == 0`. Extend `classify` with the empty-base rule (not enough yet) and the filled-on-planned-base rule (extra) (D2). Verify with `CellStateTest` cases for every scenario in "Two-item blocks in the comparison" and "Other plant in the pot", under `./gradlew :heavymatica:test`.

## 3. Materials

- [x] 3.1 Give `Demand` a content part and per-part `amountFor`. Make `MaterialCounts.add`, `remove` and `move` apply both parts (D3). Verify with `MaterialCountsTest`: missing → pot + plant, empty pot → plant only, other plant → pot + plant, and moves between those states.
- [x] 3.2 In `WorldComparison.indexPlanned` and `demand`, number the base and content blocks as planned blocks with their own items. Give two-item entries the two-part demand, add `contentOf(entry)`, and highlight two-item blocks for a held content (D3, D4). Verify with `MaterialListTest` for the "Potted plants" and "Water cauldrons" scenarios, and in `runClient` that the materials screen of a placement with potted plants lists flower pots and plants and nothing under can't carry.

## 4. Pick and HUD

- [x] 4.1 Make `GhostPick.decide` use the content's planned index while the target is not enough yet and the entry has a content (D4). Verify in `runClient` with the "Pot first", "Plant once the pot stands" and "No plant carried" scenarios.
- [x] 4.2 Add the "Needs %s" and "%s must go" texts. Use them in `TargetHud.stateLine` and the `PlacementOverlay` target lines for two-item positions, and add the language keys (D4). Verify in `runClient` with the "Pot without its plant" and "Plant in a planned empty pot" scenarios.

## 5. Boxes through blocks

- [x] 5.1 Draw the mistake boxes in a hidden pass (`MISTAKE_BOX_HIDDEN`, greater-than depth test, alpha 0.5) before the visible pass (D5). Verify in `runClient` that a red box behind a wall shows weaker through the wall and a box in plain sight looks as before.

## 6. Integration

- [x] 6.1 Check end to end in `runClient` with a schematic holding potted plants, a candle cake and a water cauldron:
  - The empty pot, cake and cauldron show orange.
  - Progress reaches 100% only once the contents are in.
  - The finish feedback fires.

  Verify by screenshots in `runs/client/screenshots/` and the client log.
