# Proposal

## Why

With Iris and a shader pack active (tested: Complementary Unbound with Euphoria Patches), placement ghosts and mistake
boxes are invisible; only the placement's box outline shows. Iris replaces the shader of every render pipeline it knows
with the pack's own program, and has no mapping for our custom ghost and mistake-box pipelines, so those draws are
lost. Players who build with shaders on can't use the mod at all.

## What Changes

- While a shader pack is in use, ghosts, mistake boxes and placement outlines are drawn after the shader pack has
  finished its final pass, as an overlay with our own pipelines unchanged: opacity, layers, the in-hand filter and the
  world-comparison mask keep working, and real blocks still hide ghosts.
- Under a shader pack, ghosts are not lit by the pack: they look as they do without shaders.
- Without Iris, or with Iris but no pack active, rendering stays as it is today.
- Iris becomes an optional client dependency: compiled against, never bundled, the mod loads without it.
- A `-PwithShaders` Gradle switch starts the dev client with Sodium, Iris and Euphoria Patches, with the Complementary
  Unbound pack placed in the dev run's `shaderpacks` folder, so shader rendering can be checked locally.

## Capabilities

### New Capabilities

- `client/shader-packs`: how placement visuals (ghosts, mistake boxes, outlines) render while a shader pack is in use,
  and that the mod works with and without a shader mod installed.

### Modified Capabilities

None. The ghost, opacity and world-comparison requirements stay as they are; this change makes them hold under shader
packs too.

## Impact

- Code: `placement/GhostRenderer`, `GhostDraw`, `MistakeBoxes`, `PlacementSession` (render hook registration); a new
  small Iris bridge class that is only loaded when Iris is present; possibly a mixin or render hook for the
  post-shader draw point, decided by the spike in the design.
- Build: `compileOnly` Iris from the Modrinth maven; `-PwithShaders` adds Sodium, Iris and Euphoria Patches as
  `localRuntime`; versions in the subproject's `gradle.properties`.
- Metadata: optional `iris` client dependency in `neoforge.mods.toml`.
- No cloud, API or web changes.
