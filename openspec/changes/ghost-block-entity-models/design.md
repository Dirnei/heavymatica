# Design

## Context

Three loops mesh schematics today, and each decides on its own what to draw: `GhostMeshBuilder` (one buffer per
ghost), `GhostCells` (one buffer per block type with layer offsets and the cell index in the lightmap slot, for locked
placements and the comparison mask) and `ThumbnailRenderer.drawBlocks` (render thread, into a `MultiBufferSource`). All
three skip everything whose render shape isn't `MODEL`, and only the first two have the working-tree `GhostExtras`
(fluids, outline-shape fallback). `GhostDraw` binds the block atlas for every draw. Tints come from two copies of a
constant plains grass colour (`GhostView`, `SchematicView`); `GhostView` now looks up the plains biome.

Facts about 26.1 that shape the approach:

- `ModelManager.getBlockModelSet()` maps every block state to a `BlockModel`. Block-entity blocks map to a
  `SpecialBlockModelWrapper` around a `SpecialModelRenderer` (see `BuiltInBlockModels`), with facing, chest type and bed
  part dispatched by state and the Christmas chest as a condition. `BlockModelResolver.update` fills a
  `BlockModelRenderState`, whose `submit` pushes the special renderer's work into a `SubmitNodeCollector`.
- Special renderers only *submit*: `submitModel` (a `Model`, its state, a pose, a sprite or render type),
  `submitModelPart`, `submitCustomGeometry`, `submitText`. Vanilla's `SubmitNodeStorage` records these, and
  `ModelFeatureRenderer` later replays them: `model.setupAnim(state)`, then `model.renderToBuffer(pose,
  sprite.wrap(buffer), ...)`. Every submit ends up as plain `VertexConsumer.addVertex` calls.
- Models are shared with item rendering, and `setupAnim` mutates their parts. Nothing in this path is thread-safe.
- A sprite knows its atlas (`TextureAtlasSprite.atlasLocation()`); chests, beds, signs, banners, shulkers and pots use
  sprites from their sheets. Skulls submit with a plain entity `RenderType` and no sprite; `RenderType` keeps its
  texture in a private `RenderSetup`.
- End portal and gateway faces draw through a dedicated shader effect, not a texture.

## Goals / Non-Goals

**Goals:**
- One block geometry source used by ghost meshes, ghost cells and thumbnails, so the three never disagree about what a
  block looks like.
- Reuse vanilla's display models instead of a per-block list of our own.
- Keep the ghost cheap: geometry baked once per mesh build, nothing per frame beyond the draw calls.

**Non-Goals:**
- Block-entity data: sign text, banner patterns, pot sherds, player-head skins, shulker contents. The display models
  draw the default look.
- Animation: chest lids stay closed, bells and conduits stay at their resting pose.
- End portal and end gateway faces (shader effect, nothing to bake), and items in item frames or on shelves.
- Rendering block entities per frame.

## Decisions

### D1: A shared `blocks` package owns block geometry

New package `io.github.dirnei.mc.heavymatica.blocks`, used by `placement` and `thumbnail`:

- `TexturedOutput`: `VertexConsumer buffer(Identifier texture)`. It has the shape of vanilla's
  `FluidRenderer.Output` and `MultiBufferSource`. Callers decide what a texture becomes: a ghost buffer per texture, or
  a `RenderType` for the thumbnail.
- `BlockGeometry`: `boolean emit(BlockAndTintGetter view, BlockPos pos, BlockState state, TexturedOutput out)`. It
  writes, in view coordinates:
  1. the block model's quads (`ModelBlockRenderer`, as today) into the block atlas output;
  2. the state's special geometry from `SpecialGeometry` (D2), translated to the position;
  3. the fluid, through `FluidRenderer` with its section-relative positions moved back to view positions;
  4. if none of these wrote anything and the render shape isn't `INVISIBLE`, the outline shape in the map colour.
  It returns whether anything was written, which keeps the "blocks" count of the rendering budget meaningful. States
  with render shape `INVISIBLE` emit only their fluid, so barriers, light blocks and structure voids stay hidden.
- `SchematicTints`: plains-biome tint lookup (`ColorResolver` against the plains `Biome` of the loaded level, grass
  green without one), used by `GhostView` and `SchematicView` instead of their constants.

Callers keep their own concerns: hidden-block culling, the per-cell view of `GhostCells`, budgets, the thumbnail camera.
`GhostExtras` from the working tree is split into `BlockGeometry` (fluid, fallback shape) and removed.

Alternative: keep three loops and add the new cases to each. Rejected: it is how the thumbnail fell behind already.

### D2: `SpecialGeometry` bakes display models once per block state, on the render thread

`SpecialGeometry` is a cache `BlockState → Baked`, where `Baked` is a list of `(texture, float[] vertices)` in
block-local coordinates (position, colour, uv, normal per vertex). Baking a state:

1. `BlockModelResolver.update(renderState, state, BlockDisplayContext.create())`; a state without a special renderer
   bakes to an empty entry (most states).
2. `renderState.submit(poseStack, capture, FULL_BRIGHT, NO_OVERLAY, 0)` into our own capturing
   `SubmitNodeCollector`, which draws each model, model-part and custom-geometry submit at once the way
   `ModelFeatureRenderer` would (`setupAnim`, `renderToBuffer` through `sprite.wrap`) into capture consumers keyed
   by texture and layer. Text, item and block-model submits are ignored; block models come from the block model
   renderer. Vanilla's `SubmitNodeStorage` keeps its recorded submits in private maps, so replaying from it would
   need more access transformers than collecting directly.

Baking runs on the render thread, because models are shared with item rendering and `setupAnim` mutates them. Before
`GhostMeshes` schedules a mesh or cells build, it calls `SpecialGeometry.prepare(states)` with the view's distinct
placed states (a palette is small); the worker then only reads finished `Baked` entries, which are immutable. The
thumbnail renders on the render thread and prepares inline. The cache is cleared when the model set changes, through
the same check `GhostMeshes` already uses to drop meshes after a resource reload.

Alternatives:
- Bake on the worker thread. Rejected: races with item rendering on shared model parts.
- Run vanilla's `ModelFeatureRenderer` against a capturing `MultiBufferSource`. Rejected: it needs outline and
  crumbling buffer sources and sorts by render type; the replay is about 30 lines and keeps the texture per submit.
- Our own model per block-entity type (chest, bed, ...). Rejected: a long list to maintain, and it would miss modded
  blocks that register display models.

### D3: The texture of a submit comes from its sprite, else from its render type

A submit with a sprite uses `sprite.atlasLocation()`, and its UVs are already atlas UVs through `sprite.wrap`. A submit
without one (skulls, some modded renderers) uses the render type's `Sampler0` texture. `RenderType.state` and the
texture binding in `RenderSetup` are private, so one NeoForge access transformer (`META-INF/accesstransformer.cfg`)
makes them readable. A submit whose texture can't be determined is dropped; if that leaves the state empty, D1's
fallback shape applies.

Alternatives: reflection (rejected: unchecked at compile time, breaks silently); skipping sprite-less submits
(rejected: loses every mob head). The project avoids mixins; an access transformer only changes visibility and is
checked when the game starts.

### D4: Ghost geometry is split by texture

- `GhostMesh` holds a list of `(texture, vertex buffer, quad count)` instead of one buffer.
- `GhostCells` keys its cells by `(block, texture)`. Layer ranges and the cell index in the lightmap slot work as
  before, so the comparison mask and layer slicing need no change. A waterlogged chest has two cells, the chest sheet and
  the block atlas (its water).
- `GhostDraw.quads` takes the texture; `close()` binds `Sampler0` per texture, grouping draws by texture to keep
  binds few. The block atlas keeps its mipmapped sampler; other textures use their own sampler.
- Vertex format and pipelines stay `DefaultVertexFormat.BLOCK` and `GhostPipeline`. Special vertices drop the overlay
  coordinate, which the ghost never used, and get the same full-bright or cell-index light value as model quads.

Alternative: stitch the sheets into one private ghost atlas. Rejected: duplicates vanilla's atlases and has to be
rebuilt on every resource reload, for a saving of a handful of draw calls.

### D5: Thumbnails go through the same source

`ThumbnailRenderer.drawBlocks` keeps its camera, budget and hidden-block check and calls `BlockGeometry.emit` with a
`TexturedOutput` mapping the block atlas to the moving-block render types it uses now (by quad layer) and every other
texture to `RenderTypes.entityCutout(texture)`. Fluids use the translucent moving-block type.

## Risks / Trade-offs

- [A modded special renderer submits in a way the replay doesn't know (items, custom render pipelines)] → those parts
  are missing; when nothing at all is captured the outline fallback shows the block.
- [Special geometry looks slightly different from the placed block: no text or patterns, closed lids] → intended, see
  Non-Goals. The material list and comparison are unaffected.
- [Render-thread baking stalls a frame for a large palette] → baking is per distinct state, once per resource load;
  a few hundred states with special models are microseconds each. `prepare` can bake a bounded number per frame if
  needed, and the build simply starts a frame later.
- [The access transformer target changes in a future version] → the game fails to start in development right away; the
  fallback is to treat sprite-less submits as "no texture".
- [More draw calls per ghost] → at most one per texture per ghost (about eight); ghosts are few.
- [Cutout textures in the translucent ghost] → the ghost pipelines use `ALPHA_CUTOUT 0.01`, which keeps fully
  transparent texels (banner and sign edges) out of the depth pre-pass.

## Migration Plan

Client-only and stateless: ghosts and thumbnails are rebuilt from the schematic on demand. Nothing to migrate; rolling
back means reverting the client.
