# Design

## Context

For motivation see proposal.md, Why. For behaviour see `specs/client/shader-packs`.

Current state:
- `PlacementSession` registers `GhostRenderer.render` on `RenderLevelStageEvent.AfterTranslucentBlocks`. It draws per
  placement, in this order: mistake boxes (`MistakeBoxes`, pipeline `MISTAKE_BOX`), ghost geometry (`GhostMesh` or
  `GhostCells` through `GhostDraw`, pipelines `GHOST_DEPTH`/`GHOST` or the masked twins), then the box outline through
  vanilla `RenderTypes.lines()`.
- `GhostDraw` opens its own render pass on `Minecraft.getMainRenderTarget()` colour + depth, with the depth-only twin
  first, then the translucent colour pass. Opacity is the `ColorModulator` alpha; the masked pipelines read the cell
  index from the lightmap UV and drop cells through the `GhostMask` texel buffer.
- Iris 1.11.4 for 26.1 maps vanilla `RenderPipeline`s to its own programs by identity (`IrisPipelines`) and has no
  entry for ours. In game with Complementary Unbound + Euphoria Patches the outline (vanilla lines) shows, ghosts and
  mistake boxes don't.
- `CaptureRenderer` draws only vanilla lines and already works under Iris; it stays as it is.
- Optional mods follow the Jade pattern: `compileOnly` from the Modrinth maven, `-PwithJade` adds it as
  `localRuntime`, an optional dependency in `neoforge.mods.toml`.

## Goals / Non-Goals

**Goals:**
- One drawing routine for placement visuals, called from one of two draw points per frame depending on whether a
  shader pack is in use.
- A local client that reproduces the reported setup with one Gradle flag.

**Non-Goals:**
- Integrating with the pack's lighting (`IrisApi.assignPipeline`), see D1.
- Other shader mods (Oculus, OptiFine). Oculus does not exist for 26.1; OptiFine has no NeoForge build.
- Shadow-pass or reflection support for ghosts.

## Decisions

### D1 Overlay after the shader pack, not mapping pipelines to Iris programs

Under a pack, placement visuals are drawn after Iris's final pass, on the main render target, with the existing
pipelines unchanged.

Alternative: `IrisApi.assignPipeline(GHOST, IrisProgram.TRANSLUCENT)` and friends. Rejected: the pack's program
replaces our shaders, so the `ColorModulator` opacity is ignored (packs use vertex colour), the cell index in the
lightmap UV is read as light, the `GhostMask` never runs, and packs such as Complementary treat translucents as glass
with reflections. The player confirmed an unlit overlay is fine.

### D2 Draw point found by a spike, NeoForge event first

The post-shader draw point must come after Iris's final pass and before the hand pass clears depth. Candidates, tried
in order during the spike, first one that works wins:
1. `RenderLevelStageEvent.AfterLevel`, if NeoForge fires it after Iris has written the final image.
2. A client mixin into `GameRenderer` right after `LevelRenderer.renderLevel` returns (Iris finalizes inside
   `renderLevel`), before the hand's depth clear.

The spike also checks that the camera matrices at that point match the scene. Complementary jitters the projection
for TAA during its gbuffer passes; a sub-pixel offset on ghost edges is accepted, visible swimming is not.

**Spike result:** candidate 1 works. NeoForge fires `AfterLevel` in `GameRenderer.renderLevel` right after
`LevelRenderer.renderLevel` returns; Iris 1.11.4 runs its composite and final passes inside `renderLevel` (injected
before the model-view stack is popped), and the hand's depth clear comes after the event. Ghosts drawn there sit on
top of the Complementary + Euphoria image and line up with real blocks, with no visible offset in still frames. The
spike used the real ghost draw instead of a throw-away tint. `AfterLevel` has no pose stack and is fired after the
model-view stack is popped, so the overlay pushes the camera's model-view rotation itself for the vanilla line outline.

### D3 Depth: reuse the main depth, copy it if Iris has replaced it

Real blocks must hide ghosts (spec). Iris writes the scene's depth into the main render target's depth texture
(`depthtex0`); if that still holds the scene at the draw point, `GhostDraw` and `MistakeBoxes` use it unchanged.

If the spike shows the main depth is cleared or no longer the scene by then, copy the depth texture into our own at
`AfterTranslucentBlocks` (where it is still the scene's) and point the overlay passes at the copy. The copy costs one
full-screen depth copy per frame while a pack is in use and nothing otherwise.

**Spike result:** the main depth texture still holds the scene at `AfterLevel`: a stone-brick wall between the player
and the ghost hides the ghost and its outline, and the floor does not. No depth copy is needed.

### D4 Shader pack state read per frame through an isolated bridge

`ShaderPacks.inUse()` returns `IrisApi.getInstance().isShaderPackInUse()` when `ModList` has `iris`, else false. The
Iris call lives in a separate class that is only loaded after the `ModList` check, so the mod loads without Iris. It
is read once per frame and decides which draw point runs; the other one returns at once. This covers turning the pack
on or off, switching packs and compile failures (Iris then reports false) without a restart.

### D5 Renderer takes a frame context instead of the event

`GhostRenderer.render` currently reads the pose stack and model-view matrix from the `AfterTranslucentBlocks` event
and passes them on to `MistakeBoxes` and `GhostDraw`. It takes the model-view matrix instead and builds its own pose
stack (the event's is always the identity), so both draw points can call it; the camera position it already reads
itself. The outline keeps using vanilla lines, ending the batch itself, so it draws in the same
place as the ghost.

### D7 Vanilla face shading, whatever the shader mod says

Found in the spike: Iris makes `CardinalLighting.byFace` return the top face's shade for every face while a pack
asks for it (Complementary does), so ghost meshes built under a pack had no directional shading and looked about 1.6
times brighter and flat. Schematic geometry reads the record's own values instead (`FaceShade`): model quads with
shading get a colour correction of vanilla shade over the shade `byFace` applied, and the outline-shape and
placeholder cubes use the vanilla values directly. Without a pack the correction is 1 and nothing is wrapped. Meshes
then look the same whichever pack state they were built in, so turning the pack on or off needs no rebuild. Measured:
the same fully opaque ghost face is (96, 77, 47) with and without the pack.

Alternative: rebuild meshes when the pack state changes. Rejected: it keeps the flat look under the pack.

### D6 Dev setup behind `-PwithShaders`

- `iris_version`, `sodium_version` and `euphoria_patches_version` in the subproject's `gradle.properties`
  (`1.11.4+26.1-neoforge`, `mc26.1.2-0.9.2-neoforge`, `1.10.5-r5.9.3-neoforge` at the time of writing).
- `compileOnly maven.modrinth:iris` always; with `-PwithShaders`, Sodium, Iris and Euphoria Patches as `localRuntime`.
- A Gradle task, run before `runClient` when `-PwithShaders` is set, downloads Complementary Unbound `r5.9.3` from
  Modrinth into `runs/client/shaderpacks/` if it is not there yet. `runs/` is git-ignored, so the pack is never
  committed. Selecting the pack and Euphoria's settings stays a one-time step in the game's Iris screen (or
  `shaderPack=` in `runs/client/config/iris.properties`); Euphoria Patches writes its patched pack as
  `ComplementaryUnbound_r5.9.3 + EuphoriaPatches_1.10.5` next to the zip on first launch.
- A `shaders=<true|false>` dev step turns shaders on or off through `IrisApiConfig`, for scripted switching checks.
- `neoforge.mods.toml` gets `iris` as an optional client dependency.

## Risks / Trade-offs

- [Neither draw point in D2 works, or depth can't be recovered] → Fall back to drawing without depth testing under a
  pack (ghosts show through walls). That breaks a spec requirement, so the spec is updated with the player before
  shipping it.
- [Iris internals change between versions] → Only the public `IrisApi` is called; the draw point and depth behaviour
  are re-checked with `-PwithShaders` when the Iris version is bumped.
- [Euphoria Patches or Iris dev-run problems under ModDevGradle] → 26.1 is unobfuscated, so Modrinth jars load
  as they are in dev; if one still fails, the spike runs against a normal launcher instance with the built jar.
- [Ghosts look flat in dark scenes] → Accepted by the player; ghosts are a building aid, not part of the world.
- [TAA jitter makes ghost edges shimmer slightly against real blocks] → Accepted if sub-pixel; checked in the spike.

## Migration Plan

Client-only, no saved data changes. Without Iris nothing changes. Rollback is reverting the change.
