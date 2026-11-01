# Tasks

## 1. Shared block geometry

- [x] 1.1 Add the `blocks` package with `TexturedOutput` and `SchematicTints` (plains biome of the loaded level, grass green without one) and switch `GhostView` and `SchematicView` to it (D1); verify `./gradlew :heavymatica:compileJava` and, in `runClient`, that a ghost's water is blue and grass and leaves keep plains greens
- [x] 1.2 Add `BlockGeometry.emit` with block-model quads, fluids (section-relative positions moved back) and the outline-shape fallback, `INVISIBLE` states emitting only their fluid, and remove `GhostExtras` (D1); verify `./gradlew :heavymatica:test` passes and the code compiles with no remaining `GhostExtras` reference

## 2. Display models

- [x] 2.1 Add the access transformer for `RenderType.state` and the `RenderSetup` texture binding, picked up from `META-INF/accesstransformer.cfg` and declared in `neoforge.mods.toml` (D3); verify `runClient` starts with no access transformer errors in the log
- [x] 2.2 Add `SpecialGeometry`: bake a state through `BlockModelResolver`, `BlockModelRenderState.submit` into a capturing `SubmitNodeCollector` that writes model, model-part and custom-geometry submits into per-texture vertex arrays (sprite atlas, else the render type's `Sampler0`), render thread only, cleared when the model set changes (D2, D3); verify with a `HEAVYMATICA_DEV_SCREEN` step that logs, for red bed head and foot, a double chest's halves, an oak wall sign, a skeleton skull and a stone block, the textures and vertex counts of their baked geometry (stone empty, the others non-empty with the expected sheets)
- [x] 2.3 Emit `SpecialGeometry` from `BlockGeometry.emit`, translated to the position, with the fallback applying only when model, special and fluid geometry are all empty (D1); verify `./gradlew :heavymatica:compileJava` and `:heavymatica:test`

## 3. Ghosts

- [x] 3.1 Split `GhostMesh` into per-texture buffers and give `GhostDraw` the texture per draw, binding `Sampler0` per texture with the block atlas keeping its mipmapped sampler (D4); verify in `runClient` that an unlocked placement of a room with a red bed, a double chest, a wall sign, a banner, a skull, a shulker box, a decorated pot, a bell and a water pool shows each with its own texture and a screenshot shows no block-atlas textures on the chest or bed
- [x] 3.2 Build `GhostMeshBuilder` on `BlockGeometry`, keeping hidden-block culling and the block count for the budget, and have `GhostMeshes` call `SpecialGeometry.prepare` with the view's distinct states before scheduling a build (D1, D2); verify the "Bed", "Double chest", "Sign without its text", "Rotated placement", "Water source" and "Barrier" scenarios in `runClient`
- [x] 3.3 Key `GhostCells` by block and texture, emit through `BlockGeometry` with the cell index in the lightmap slot, and prepare special geometry before cells builds (D4); verify in `runClient` the "Layer view" and "Waterlogged stairs" scenarios on a locked placement, and that placing the right chest in the world hides its ghost cell through the comparison mask

## 4. Thumbnails

- [x] 4.1 Draw thumbnail blocks through `BlockGeometry` with a `TexturedOutput` mapping the block atlas to the moving-block render types and other textures to `RenderTypes.entityCutout`, preparing special geometry inline (D5); verify with `HEAVYMATICA_THUMBNAIL_SPIKE` on a schematic with a bed, a chest and a pool that the written PNG shows all three (view it with Read)

## 5. Integration

- [x] 5.1 Check the remaining scenarios end to end in `runClient`: "Grass and leaves", "Modded block without a display model" (simulate by forcing a state's special geometry empty in a dev run) and "Ghost and thumbnail agree", and that a resource reload (F3+T) rebuilds ghosts with special models intact; verify by screenshots in `runs/client/screenshots/` (checked by the user in `runClient`; the modded-block fallback is covered by the code path only, no modded block was available)
