# Tasks

## 1. Split into common and neoforge

- [x] 1.1 Create `src/mod/heavymatica/common` and `src/mod/heavymatica/neoforge` and `git mv` the sources. Everything
  goes to `common` except the templates and `accesstransformer.cfg`, which go to `neoforge`. `Heavymatica` stays in
  common as constants and `neoforge` gets its own `@Mod` entry class. Commit the move alone. Verify `git log --follow` on a moved file shows its history.
- [x] 1.2 Set up the build:
  - `common` builds with ModDevGradle `neoFormVersion` (vanilla only).
  - `neoforge` includes `common`'s sources and resources.
  - The shared `subprojects` block moves into a convention script.
  - The OpenAPI generation stays in `common`.
  - The dev runs, Jade and shader dev flags keep working from `:heavymatica:neoforge`.
  - Verify `./gradlew build` from `src/mod` passes. Until group 2 removes them, NeoForge imports in common classes
    compile through a temporary `compileOnly` dependency on NeoForge in `common`.
- [x] 1.3 Verify the NeoForge jar is named `heavymatica-neoforge-<version>.jar`, contains the bundled Jackson and
  qrcodegen, and starts in `runClient` with the library, ghosts and key hints working.

## 2. Loader hooks in common

- [x] 2.1 Add the `Platform`, `ClientEvents`, `ServerEvents`, `HudLayers` and `KeyRegistry` interfaces with
  `KeyContext`, loaded through `ServiceLoader`. Verify with a unit test that a missing service fails with a clear
  message.
- [x] 2.2 Implement them in `neoforge` as pass-throughs to the current events:
  - client tick, frame, login and logout, the two world render stages, scroll and interaction
  - server tick and stopping
  - GUI layers with the same anchors
  - key mappings with conflict contexts mapped from `KeyContext`

  Verify the NeoForge client behaves as before.
- [x] 2.3 Move the feature classes onto the hooks. Covers:
  - `PlacementSession`, `CaptureRenderer`, `GhostTargeting`, `CreativePaste`, `RemoteInput`, `BuildInput`
  - the five HUD layers and `WheelAfterglow`
  - `RemoteKeys`, `BuildKeys`, `OPEN_LIBRARY`
  - `ShaderPacks`
  - `GhostPipeline` registration

  `HeavymaticaClient`'s wiring moves to a loader-free `HeavymaticaClientCore`, which `neoforge` calls. Verify `common`
  has no `net.neoforged` import apart from `HeavymaticaConfig`, `SettingsScreen`, `CarriedItems` and `DevScreens`.
- [x] 2.4 Move `CarriedItems`' container lookup behind a `ContainerAccess` hook, implemented in `neoforge` with
  `ItemAccess` and the item capability. Verify the existing material list tests pass and nested shulker boxes still
  count once.
- [x] 2.5 Keep the mods-screen case of `DevScreens` in `neoforge` and leave the rest in `common`. Verify
  `HEAVYMATICA_DEV_SCREEN` still opens its screens on NeoForge.

## 3. Settings model and NeoForge backend

- [x] 3.1 Add the settings model to `common`:
  - sections and typed settings (bool, int range, double range, enum, string with check, number list), each with key,
    comments and default
  - a `SettingsBackend` interface (get, set, save)

  Verify unit tests cover defaults, range checks and lookup by path.
- [x] 3.2 Rewrite `HeavymaticaConfig` on the model with the same keys, sections, comments, defaults and order. Its public
  static API stays as it is. Verify no caller outside `HeavymaticaConfig` changes.
- [x] 3.3 Implement the NeoForge backend that builds the `ModConfigSpec` from the model and registers it as CLIENT.
  Verify with a test that writes a default file from the generated spec and from a frozen copy of today's
  hand-written spec, and asserts both files are byte for byte equal.
- [x] 3.4 Point `SettingsScreen` at the model instead of `ModConfigSpec`, including section reset and the
  remote-item-from-hand and opacity-steps rows. Verify on NeoForge: changing, resetting and reopening settings works,
  and the file shows the change at once.
- [x] 3.5 Verify on NeoForge that an existing `heavymatica-client.toml` from the current release loads with every value
  kept. Also verify that editing the file while the game runs is still picked up.
- [x] 3.6 Verify `common` has no `net.neoforged` import left, and remove its temporary NeoForge dependency.

## 4. Fabric project

- [x] 4.1 Add `src/mod/heavymatica/fabric`:
  - Loom (`net.fabricmc.fabric-loom`) with Fabric Loader and Fabric API versions for 26.1.2 in `gradle.properties`
  - `common`'s sources and resources included
  - `fabric.mod.json` from a template: id `heavymatica`, environment `client`, depends on `fabricloader`,
    `fabric-api` and `minecraft` 26.1.2, suggests `jade`, `iris` and `modmenu`
  - `include` for Jackson, qrcodegen and NightConfig TOML
  - `runClient` and `runServer` with the same dev properties as NeoForge

  Verify `./gradlew :heavymatica:fabric:build` produces `heavymatica-fabric-<version>.jar` with the bundled libraries.
- [x] 4.2 Add the class tweaker with the six access-transformer entries. Verify `common` code that uses them compiles in
  `fabric` and `runClient` starts.
- [x] 4.3 Implement `Platform`, `ClientEvents` (Fabric API events for tick, frame, login and logout) and `ServerEvents`
  (Fabric API server tick and stopping). Verify Creative paste runs and stops on logout in a singleplayer world.
- [x] 4.4 Implement world render stages with Fabric API world render events or a `LevelRenderer` mixin at the matching
  point. Verify ghosts, comparison colours, layers and the capture box look as on NeoForge.
- [x] 4.5 Implement `HudLayers` with Fabric API's HUD element registry in the same order relative to the hotbar,
  crosshair and each other. Verify the overlay, key hints, target HUD, pointer HUD, build options HUD and wheel
  afterglow show and stack as on NeoForge.
- [x] 4.6 Implement interception of attack, use and pick block (mixin into `Minecraft`) and mouse scroll (mixin into
  `MouseHandler`). Verify on Fabric:
  - the remote's clicks and scroll act on placements and don't reach the world
  - Easy Place and the placement restriction behave as on NeoForge
  - pick block on a ghost picks the planned item
- [x] 4.7 Implement `KeyRegistry` with a `KeyMapping` mixin that applies `KeyContext`, so mappings bound to the same key
  pick the one whose context is active. Verify on Fabric:
  - the default bindings match NeoForge's
  - remote and build keys work while vanilla keys share their bindings
  - vanilla keys act normally without the remote
- [x] 4.8 Register `GhostPipeline` on Fabric. Verify ghosts render with and without Iris and a shader pack.
- [x] 4.9 Implement `ContainerAccess` with Fabric's item storage for player inventory slots. Verify on Fabric: the
  material list counts shulker boxes and bundles, nested containers count once, and the counts match NeoForge's for
  the same inventory.
- [x] 4.10 Implement the Fabric settings backend with NightConfig on `config/heavymatica-client.toml`: load at start,
  fall back to defaults for invalid or missing values and write them back, save on each change. Verify with unit tests
  that:
  - a file written by the NeoForge backend loads with the same values
  - an out-of-range value falls back and is written back
  - a fresh file matches the NeoForge default file
- [x] 4.11 Add the optional Mod Menu entry point opening `SettingsScreen`. Verify the config button opens it and Done
  returns to Mod Menu, and that the game starts without Mod Menu.
- [x] 4.12 Build the Jade plugin and Iris bridge against the Fabric versions (Modrinth maven, `compileOnly`, dev flags
  `-PwithJade` and `-PwithShaders`). Verify Jade's tooltip shows ghosts on Fabric, and that the game starts without
  Jade and Iris.
- [x] 4.13 Verify a Fabric client joins a vanilla server and a NeoForge server without Heavymatica, and the library,
  placements and comparison work there.

## 5. CI, release and docs

- [x] 5.1 Update `.github/workflows/build.yml` to upload both jars. Verify a push builds both and the artifact holds
  both files.
- [ ] 5.2 Update `.github/workflows/release.yml`:
  - build both jars with the release version
  - publish with one `mc-publish` step per jar: NeoForge with `loaders: neoforge`; Fabric with `loaders: fabric` and
    dependencies `fabric-api(required)`, `jade(optional)`, `modmenu(optional)`
  - same version, type and changelog on both

  Verify with a test tag on a fork or a dry run that both jars are attached and marked with their loader.
- [x] 5.3 Update `CONTRIBUTING.md`: layout, build and run commands for both loaders, where loader code goes, the dev
  config path per loader. Update `README.md`: a Fabric loader badge and install notes naming Fabric API. Verify every
  documented command runs as written.

## 6. Integration check

- [ ] 6.1 Play through on Fabric and NeoForge from the same commit:
  - import, place, rotate and lock a schematic
  - build a layer by hand with the material list
  - use Easy Place, capture and paste
  - open the settings from the mods list (Mod Menu on Fabric) and from the library

  Verify both loaders behave the same, and note any difference as a bug before archiving.

## Workflow follow-up

- Commit after all tasks are done (project rule for applied changes).
- Archive the change once the user has tested both loaders in game.
