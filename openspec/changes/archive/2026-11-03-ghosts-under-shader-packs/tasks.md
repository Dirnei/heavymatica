# Tasks

Tasks marked **(in-game check)** run in `runClient -PwithShaders` with Complementary Unbound selected in the Iris
screen, unless they say otherwise, scripted through `DevScreens` where possible, with screenshots.

## 1. Shader dev setup

- [x] 1.1 Add `iris_version`, `sodium_version` and `euphoria_patches_version` to the subproject's `gradle.properties`, `compileOnly maven.modrinth:iris`, and a `-PwithShaders` switch adding Sodium, Iris and Euphoria Patches as `localRuntime`; add `iris` as an optional client dependency in `neoforge.mods.toml` (D6); verify `gradlew build` passes and the built jar's `neoforge.mods.toml` lists `iris` as optional
- [x] 1.2 Add a task that downloads Complementary Unbound `r5.9.3` from Modrinth into `runs/client/shaderpacks/` when missing, wired before `runClient` only with `-PwithShaders` (D6); verify a clean `runs/client/shaderpacks/` gets the zip, a second run does not download again, and `git status` shows nothing new
- [x] 1.3 **(in-game check)** Start `runClient -PwithShaders`, select Complementary Unbound with Euphoria Patches, and reproduce the bug: an anchored placement shows only its outline; verify with a screenshot, and that plain `runClient` still starts without Iris

## 2. Spike: draw point and depth

- [x] 2.1 Add `ShaderPacks.inUse()` with the Iris call in a separate class loaded only when `ModList` has `iris` (D4); verify `runClient` without Iris loads the mod and `inUse()` is false, and with `-PwithShaders` it follows turning the pack on and off in the Iris screen (logged once per change)
- [x] 2.2 **(in-game check)** Try the D2 candidates in order with a throw-away full-screen tint and a test quad at a known block: find the first draw point where the tint is on top of the shaded image and the quad lines up with the block; record which one in design.md D2
- [x] 2.3 **(in-game check)** At that draw point, check whether the main depth texture still holds the scene (the test quad is hidden behind a wall); record the result in design.md D3, and if it doesn't, decide between the depth copy and the risk fallback with the player before going on

## 3. Overlay drawing

- [x] 3.1 Give `GhostRenderer.render` a frame record (model-view, pose stack, camera) instead of the event, and call it from `AfterTranslucentBlocks` only when `ShaderPacks.inUse()` is false (D5); verify the existing placement tests pass and plain `runClient` **(in-game check)** draws ghosts, mistake boxes and outlines as before
- [x] 3.2 Register the draw point from 2.2 and call `GhostRenderer.render` there only when `ShaderPacks.inUse()` is true, with the depth from 2.3 (D2, D3); remove the spike code; verify **(in-game check)**: an anchored placement shows its ghost and outline, a locked placement with a wrong block shows its mistake box, matching positions show no ghost, and a wall hides the ghost behind it
- [x] 3.3 **(in-game check)** Verify the rest of the spec under the pack: ghost opacity set to 80% from the wheel shows at 80%, a layer window shows only those layers, the in-hand filter dims and hides as without shaders, the ghost at night looks as without shaders, and ghost edges do not visibly swim while moving
- [x] 3.4 **(in-game check)** Verify runtime switching: turn the pack off and on, switch to another pack and select a broken pack zip while a placement is in view; the ghost stays visible each time, in the next frame

## Workflow follow-up

- Archive the change after the in-game checks are done and reviewed.
