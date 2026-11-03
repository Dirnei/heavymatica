# Design

## Context

- `src/mod/heavymatica` is a single ModDevGradle project. 25 of its 215 main classes import NeoForge; the 78 test classes
  don't. Minecraft 26.1 ships unobfuscated, so Fabric Loom (`net.fabricmc.fabric-loom`) and ModDevGradle compile against
  the same class and member names.
- NeoForge is used for:
  - mod and client entry points (`Heavymatica`, `HeavymaticaClient`)
  - the event bus: client tick, server tick and stopping, login and logout, render frame, two world render stages
    (after translucent blocks, after level), mouse scrolling, intercepted attack/use/pick clicks
  - key mappings with custom `IKeyConflictContext`s (`RemoteKeys`, `BuildKeys`), so remote keys share physical keys
    with vanilla ones
  - GUI layers placed above `HOTBAR` and `CROSSHAIR`, above another Heavymatica layer, and above everything
  - `RegisterRenderPipelinesEvent` (`GhostPipeline`)
  - `ModConfigSpec` (`HeavymaticaConfig`), read by path from `SettingsScreen`
  - the config screen extension point
  - item capability / `ItemAccess` (`CarriedItems`)
  - `ModList.isLoaded` (`ShaderPacks`)
  - NeoForge's mods screen, used only by the dev-only `DevScreens`
  - an access transformer (6 entries)
  - `jarJar` for Jackson and qrcodegen
- Jade and Iris exist on both loaders with the same plugin APIs for what `HeavymaticaJadePlugin` and `IrisBridge` use.
- Release notes list commits under `src/mod/heavymatica`; the publish step uploads one jar with `loaders: neoforge`.

## Goals / Non-Goals

**Goals:**

- One codebase, two jars, the same features, released together.
- NeoForge behavior unchanged, including the config file, live reload and the mods screen config button.
- The NeoForge and Fabric parts stay thin: wiring only, no feature logic.
- Common code and its tests compile and run without either loader.

**Non-Goals:**

- Live reload of the config file on Fabric.
- Quilt, Forge or other Minecraft versions.
- `heavymatica_machines` on Fabric.
- Porting the dev-only `DevScreens` mods-screen case to Fabric (it stays NeoForge only).

## Decisions

### D1: Three Gradle projects under `src/mod/heavymatica`

`:heavymatica:common`, `:heavymatica:neoforge` and `:heavymatica:fabric`, in the folders `common/`, `neoforge/` and
`fabric/`.

- `common` uses ModDevGradle with `neoFormVersion`, which compiles against vanilla only. Nearly all code and every test
  live there.
- `neoforge` uses ModDevGradle. `fabric` uses Loom.
- Both loader projects compile `common`'s sources and resources into their own jar, as the MultiLoader Template does,
  so each jar is self-contained and Loom and ModDevGradle each see one mod source set.
- The root `build.gradle`'s shared `subprojects` block moves into per-loader convention scripts.
- The mod id stays `heavymatica`. Jars are named `heavymatica-neoforge-<version>.jar` and
  `heavymatica-fabric-<version>.jar`.
- Folders stay under `src/mod/heavymatica`, so the release notes filter keeps working.

*Alternative:* Architectury. Rejected: it adds a runtime API mod players would have to install, and our loader surface
is small.

### D2: Loader hooks through a `Platform` service

`common` declares small interfaces and the loader projects implement them. One `Platform` service, loaded once
through `ServiceLoader` from `META-INF/services` in each loader project, hands out the others:

- `Platform`: loader name, `isModLoaded`, config directory, render pipeline registration, the empty flower pot of a
  potted plant, dev aids.
- `ClientEvents`: tick, frame start, login and logout, world render stages, mouse scroll, attack/use/pick interception.
- `ServerEvents`: server tick, server stopping.
- `HudLayers`: register a layer above a named anchor (hotbar, crosshair, another Heavymatica layer, top).
- `KeyRegistry`: register a category and key mappings with a `KeyContext`.

Feature classes call these instead of `NeoForge.EVENT_BUS`. On NeoForge each hook is a direct pass-through to the event
it replaces, so NeoForge behavior doesn't change.

### D3: Fabric implementations

- Ticks, login and logout, server lifecycle: Fabric API lifecycle and connection events.
- HUD layers: Fabric API's HUD element registry, attached after the vanilla hotbar and crosshair elements, keeping the
  current order.
- World render stages: after translucent terrain is Fabric API's `LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN`; after
  the level (before the hand) and the start of a frame have no Fabric API event, so a `GameRenderer` mixin injects at
  the same points as NeoForge.
- Attack/use/pick interception and mouse scroll: small client mixins into `Minecraft` (start attack, start use item,
  pick block) and `MouseHandler` (scroll). Fabric API's use/attack callbacks fire too late to cancel the key action the
  way NeoForge's event does.
- Key conflict contexts: a mixin into `KeyMapping` that applies Heavymatica's `KeyContext`. When a key is pressed, among
  the mappings bound to it, those whose context is active win, as on NeoForge. Only Heavymatica's mappings carry a
  context; vanilla and other mods' mappings behave as usual.
- Render pipelines: `RenderPipelines.register` from the client entry point, before the first resource load.
- Client services start once the loading overlay is gone: Fabric's client ticks already run during the first resource
  load, before the block models exist.
- Access transformer: a class tweaker with the same six entries, referenced from `fabric.mod.json`.
- Carried containers: Fabric's item storage lookup through a player inventory slot context, mirroring
  `ItemAccess.forPlayerSlot`. The recursion and depth limit stay in common.
- Config screen: an optional Mod Menu entry point (`compileOnly`) that opens `SettingsScreen`.
- Bundling: Loom `include` for Jackson, qrcodegen and NightConfig TOML.

### D4: Settings defined once, loader-free

`common` gets a small settings model: sections with typed settings (`bool`, `int` with range, `double` with range,
`enum`, `string` with a check, `list` of numbers with an element check). Each setting has a key, comments and a default.

- `HeavymaticaConfig` keeps its static getters, setters and `migrateCompactOverlay`, and defines its settings with
  this model in the same order, with the same comments and paths. Callers don't change.
- `SettingsScreen` reads settings by path from the model instead of from `ModConfigSpec`.
- The model talks to a `SettingsBackend` (get, set, save) chosen by the loader.
- **NeoForge backend**: walks the model and builds the `ModConfigSpec` with the same `push`/`pop`, `comment`, `define`,
  `defineInRange`, `defineEnum` and `defineList` calls as today. It registers the spec as a CLIENT config. Get, set and
  save go to the `ConfigValue`s, so the file, live reload and validation are NeoForge's own, as now.
  - A unit test runs both the generated spec and a copy of today's hand-written spec and asserts they write the same
    file. This catches drift in comments, order or ranges.
- **Fabric backend**: NightConfig `CommentedFileConfig` on `config/heavymatica-client.toml`. NightConfig is what
  NeoForge itself uses, so the format and comment style match.
  - Loads at client start and checks each value with the model's check. Invalid or missing values go back to their
    default, which is then written.
  - Writes the file on every save.
  - No file watcher.

*Alternative:* Forge Config API Port on Fabric. Rejected by the user: an extra required mod for Fabric players.

### D5: One release, two jars

- `build.yml` runs `./gradlew build`, which builds and tests `common` once and assembles both jars. It uploads both jars.
- `release.yml` builds with the release version.
- The publish step runs `mc-publish` twice, once per jar:
  - NeoForge: `loaders: neoforge`, dependency `jade(optional)`.
  - Fabric: `loaders: fabric`, dependencies `fabric-api(required)`, `jade(optional)`, `modmenu(optional)`.
- Both steps use the same version, version type and changelog.
- The publish job only starts after the build job passes for both jars, so a failing loader blocks the release.

## Risks / Trade-offs

- **Key conflict emulation on Fabric is the riskiest part.** Vanilla keeps one mapping per key in its lookup map, so
  sharing a key needs a mixin into how presses reach mappings. If another mod rewrites the same code, the two can
  clash. *Mitigation:* keep the mixin minimal (choose the active mapping among those bound to the key), and test the
  remote and build keys against bound vanilla keys on Fabric.
- **World render stages might not line up exactly** with NeoForge's. Ghosts could then sort differently against
  translucent blocks or shader output. *Mitigation:* compare the ghost and capture visuals side by side on both loaders,
  with and without Iris.
- **Two loaders double the in-game testing per release.** *Mitigation:* the loader parts stay thin and common tests cover
  the logic. The checklist in `tasks.md` names what to check on Fabric.
- **Settings drift between the two backends.** *Mitigation:* the spec-equivalence test (D4) and a Fabric backend test
  that round-trips a NeoForge-written file.
- **Moving files rewrites history paths.** *Mitigation:* use `git mv` in a separate commit before any edits, so
  `git log --follow` keeps working.

## Migration Plan

1. Move all sources into `common/` and `neoforge/` (no Fabric yet). Run the NeoForge client: settings file, key hints,
   ghosts and comparison must be unchanged.
2. Add the hooks (D2) and the settings model (D4) with the NeoForge backend. Run the equivalence test and the NeoForge
   client again.
3. Add `fabric/`.
4. Update CI, release, docs.

Existing players need to do nothing. The NeoForge jar's file name changes, which the platforms handle as a normal
update.

## Open Questions

None. Vanilla 26.1 already lets several mappings share a key; the key mixin only filters by context. Dev scripts press
unbound mappings one at a time on Fabric, because vanilla would press every unbound mapping at once.
