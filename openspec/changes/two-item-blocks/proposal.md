## Why

Potted plants, candle cakes and filled cauldrons have no item of their own: the player places a base (flower pot,
cake, cauldron) and then adds the content (the plant, a candle, a bucket). The client treats these blocks as "can't
carry", so middle click can't pick them, the material list leaves them out of the shortages, and an empty pot where a
potted dead bush is planned shows as a red wrong block, although it is only half built.

During the same playtest, mistake boxes behind other blocks were invisible, so wrong blocks inside or behind a build
could only be found by walking around it.

## What Changes

- Add two-item blocks: a planned block that is built from a base block and a content. The table covers every potted
  plant (flower pot + plant), every candle cake (cake + candle of its colour) and the water, lava and powder snow
  cauldrons (cauldron + water, lava or powder snow bucket).
- An empty base where a two-item block is planned is "not enough yet" (orange box, ghost kept), not wrong block. A
  two-item block where only its empty base is planned is "extra" (magenta box), since its content does not belong there. A different content (another plant, another
  candle colour) stays wrong block.
- The material list counts a two-item block as one base item and one content item, and once the base stands only the
  content item. Two-item blocks no longer appear under "can't carry".
- Middle click on a two-item ghost picks the base item while the base is missing or wrong, and the content item once
  the empty base stands.
- The HUD and the overlay name the missing content for a two-item block that is not enough yet.
- Mistake boxes also show through blocks in front of them, at half strength. **BREAKING** for the "Box behind a wall"
  scenario, which said they must not.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client/block-equivalents`: adds the two-item rows (potted plants, candle cakes, filled cauldrons) next to the
  existing equivalence rows.
- `client/world-comparison`: an empty base is not enough yet instead of wrong block, a filled block on a planned empty
  base is extra, and boxes show through other blocks.
- `client/material-list`: two-item blocks count as their base and content items, and a not enough yet one needs only
  its content.
- `client/ghost-targeting`: which item pick block chooses for a two-item ghost, and the HUD line for its not enough yet
  state.

## Impact

- Mod only, `heavymatica` client: `placement` (`BlockEquivalents` or a new two-item table, `CellKeys`, `CellState`,
  `Demand`, `MaterialCounts`, `WorldComparison`, `MistakeBoxes`, `GhostPipeline`), `targeting` (`GhostPick`,
  `GhostTargeting`, `TargetHud`), `remote/PlacementOverlay`, language files.
- No cloud, contract or schematic format changes. Saved placements and schematics stay as they are.
