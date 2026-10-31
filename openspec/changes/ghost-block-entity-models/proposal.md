# Proposal

## Why

Ghosts (and upload thumbnails) only draw block models. In 26.1 a whole family of common blocks has no block-model
geometry because block-entity renderers draw them: chests, beds, signs, banners, skulls, shulker boxes, decorated pots,
bells, conduits, copper golem statues and more. Fluids go through vanilla's separate fluid renderer. A player following a
schematic therefore sees holes where the bed, the chests and the water should be, while the material list and the target
HUD still ask for them. A first fix in the working tree meshes fluids, draws the missing blocks as flat outline boxes and
looks up biome tints, but beds and chests still don't look like beds and chests.

Vanilla 26.1 already knows how to draw every one of these blocks without a block entity: `ModelManager.getBlockModelSet()`
maps each block state to a display model (the one used for a chest in a minecart or a block an enderman holds), with
facing, chest halves and bed parts dispatched by state. Reusing it gives real models and textures for all of them at once.

## What Changes

- Ghosts draw block-entity blocks with their real models and textures (chests including ender, trapped and copper
  chests and both halves of double chests, beds, shulker boxes, standing, wall and hanging signs, banners, mob heads and
  skulls, decorated pots, bells, conduits, copper golem statues, enchanting table books),
  taken from vanilla's block display models. They are baked once into the ghost geometry, not rendered per frame.
- Ghost geometry can carry several textures: besides the block atlas, the chest, bed, sign, shulker, banner and decorated
  pot sheets and standalone entity textures (skulls). Each texture is drawn as its own range with that texture bound.
- Fluids (water, lava, and the fluid in waterlogged blocks) are part of the ghost, with biome tints resolved against the
  plains biome (water blue, grass and foliage green). This keeps the working-tree fix and folds it into the new design.
- Blocks that still yield no geometry (a block from a mod whose display model is empty) fall back to their outline shape
  in their map colour, so nothing in the schematic is invisible.
- Without block-entity data the ghost shows the default look: no sign text, no banner patterns, no pot sherds, a default
  player head. The Christmas chest skin follows vanilla.
- Upload thumbnails use the same block geometry, so they show these blocks and fluids too.
- Meshing for ghosts, ghost cells and thumbnails moves into one shared block geometry source instead of three loops that
  each decide on their own which blocks to draw.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client/schematic-placement`: "Ghost rendering" now requires block-entity blocks with their own models and textures,
  fluids with biome-style tints, and a visible fallback for blocks without geometry.
- `client/schematic-import`: "Thumbnails made on upload" now requires the thumbnail to show block-entity blocks and
  fluids like the ghost does.

## Impact

- Mod, client only: `placement` (`GhostMeshBuilder`, `GhostCells`, `GhostMesh`, `GhostDraw`, `GhostMeshes`,
  `GhostView`, the working-tree `GhostExtras`), `thumbnail` (`ThumbnailRenderer`, `SchematicView`), and a new shared
  package for block geometry.
- Vanilla APIs reused: `BlockModelSet`/`BlockModelResolver`, `BlockModelRenderState`, `SubmitNodeStorage`,
  `SpecialModelRenderer`s, `Model`/`ModelPart`, `TextureAtlasSprite.wrap`, `FluidRenderer`. One NeoForge access
  transformer exposes `RenderType`'s texture binding for submits that carry no sprite (skulls); no mixins.
- More GPU buffers per ghost (one range per texture) and a few more draw calls; meshing stays off the render thread
  except for a small per-state bake that has to run there.
- No cloud, website or contract changes. The cloud-side thumbnails in `cloud-thumbnails` are flat colours and unaffected.
