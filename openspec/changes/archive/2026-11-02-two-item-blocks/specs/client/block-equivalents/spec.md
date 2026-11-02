## ADDED Requirements

### Requirement: Two-item blocks
The client SHALL know the planned blocks that are built from two items: a base block placed first and a content added to it. Each such block SHALL have its base block and its content item:
- every potted plant: the empty flower pot, and the plant's item (a potted dead bush takes a dead bush, a potted azalea an azalea)
- every candle cake: the cake, and the candle of the same colour (a plain candle for the uncoloured candle cake)
- the water cauldron, the lava cauldron and the powder snow cauldron: the cauldron, and a water bucket, a lava bucket or a powder snow bucket

Blocks that mods add to the game's flower pots and candle cakes SHALL be two-item blocks the same way. A two-item block whose content has no item SHALL be placed, counted and compared as itself.

#### Scenario: Potted dead bush
- **WHEN** a locked placement expects a potted dead bush
- **THEN** its base is the flower pot and its content a dead bush

#### Scenario: Red candle cake
- **WHEN** a locked placement expects a red candle cake
- **THEN** its base is the cake and its content a red candle

#### Scenario: Water cauldron
- **WHEN** a locked placement expects a water cauldron at any water level
- **THEN** its base is the cauldron and its content a water bucket

#### Scenario: Modded pot
- **WHEN** a mod registers a potted fern-like plant with the game's flower pot
- **THEN** that potted plant is a two-item block with the mod's plant as content
