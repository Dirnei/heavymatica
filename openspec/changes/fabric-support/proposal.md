# Proposal

## Why

Heavymatica only runs on NeoForge, but most Litematica users, the players it is built for, play on Fabric. Nearly all of
the mod is plain Minecraft code (25 of 215 classes touch NeoForge), and Minecraft 26.1 is no longer obfuscated, so both
loaders see the same class names. A Fabric version with the same features is within reach.

## What Changes

- Heavymatica ships for Fabric and NeoForge from one release: same version, same features, same changelog, two jars.
- The mod source splits into a loader-free `common` part and thin `neoforge` and `fabric` parts. The loader parts only
  register events, key mappings, HUD layers, render pipelines, the config and optional integrations.
- Settings get a loader-free definition in `common`. NeoForge keeps building the same `ModConfigSpec` from it, so
  `config/heavymatica-client.toml` keeps its sections, keys, comments, defaults and validation, and existing files load
  unchanged. Fabric reads and writes the same file in the same format.
- On Fabric, the settings screen opens from Mod Menu when it is installed, and always from the library.
- On Fabric, the material list counts carried containers through Fabric's item storage API, as NeoForge does through its
  item capability.
- Jade and Iris stay optional integrations on both loaders. Fabric API is required on Fabric.
- CI builds and tests both jars. A release tag publishes both to GitHub, CurseForge and Modrinth, each marked with its
  loader.
- Settings changed by editing the file while the game runs are only picked up on NeoForge; Fabric reads the file at
  start. Settings changed in the game apply at once on both.

## Capabilities

### New Capabilities

- `client/mod-loaders`: which loaders Heavymatica runs on, feature parity between them, the shared settings file, the
  client-only promise on any server, the optional integrations per loader and how releases ship.

### Modified Capabilities

- `client/settings-screen`: the mods list entry point names the loader's own mods list (NeoForge's mods screen, Mod Menu
  on Fabric).
- `client/material-list`: carried containers are counted through the loader's item container API instead of NeoForge's
  item capability only.

## Impact

- `src/mod/heavymatica` becomes three Gradle projects (`common`, `neoforge`, `fabric`). Paths in docs, CI and the dev
  runs change; release notes still follow commits under `src/mod/heavymatica`.
- Build: ModDevGradle for `neoforge` and for compiling `common` against vanilla, Fabric Loom for `fabric`. New
  dependencies on Fabric: Fabric Loader, Fabric API, NightConfig TOML (bundled), Mod Menu (optional, compile only).
- NeoForge-only features become hooks the Fabric part implements with Fabric API events or small mixins: key conflict
  contexts, intercepted attack/use/pick clicks, mouse scrolling, HUD layer order, world render stages, render pipeline
  registration, access transformers (become a class tweaker on Fabric).
- `.github/workflows/build.yml` and `release.yml`, `CONTRIBUTING.md`, `README.md` (loader badge).
- The later `heavymatica_machines` mod is not part of this change. It depends on Servus: Foundation, which is NeoForge
  only.
