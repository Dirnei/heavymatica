---
title: Mod projects
description: How the mod is split into a loader-free common part and thin Fabric and NeoForge parts.
---

`src/mod/heavymatica` holds three Gradle projects:

- `common`: all features and their tests. It compiles against vanilla Minecraft only, so no loader API may appear
  here. What a loader has to provide (events, HUD layers, key mappings, render pipelines, carried containers) goes
  through the interfaces in `io.github.dirnei.mc.heavymatica.platform`, found with `Platform.get()`.
- `neoforge`: the NeoForge entry points and those interfaces on NeoForge's events. Settings are NeoForge's own config,
  built from the shared settings schema in `HeavymaticaConfig`.
- `fabric`: the same on Fabric API, with small mixins where Fabric API has no matching event. Settings are written by
  `TomlSettingsFile` in the same format as NeoForge's.

Both loader projects compile `common`'s sources into their own jar. Vanilla members that need wider access are listed
twice: in `neoforge/src/main/resources/META-INF/accesstransformer.cfg` and in
`fabric/src/main/resources/heavymatica.classtweaker`.

The [architecture page](/develop/architecture/) shows the parts as a diagram.

## Settings file

`NeoForgeSettingsTest` and `TomlSettingsFileTest` check that both loaders write the same default settings file,
`common/src/test/resources/settings/heavymatica-client.default.toml`. This site's [settings page](/play/settings/) is
generated from that file: a new or changed setting shows up there once the file is updated for the tests.

## Key reference

`KeyReferenceTest` in `common` registers every key with a recording platform and compares the result with
`docs/src/data/keys.json`, which this site's [keys page](/play/keys/) is generated from. When a key is added, removed
or changes its default or context, the test fails and names the key. Rewrite the file with:

```
./gradlew :heavymatica:common:test -PwriteDocs
```

A new key context needs a stable `toString()` and a description in `docs/src/lib/keys.ts`; the docs build fails
without one.

## Dev runs

```
./gradlew :heavymatica:neoforge:runClient
./gradlew :heavymatica:fabric:runClient
```

Each loader has its own run folder, `heavymatica/<loader>/runs`. Dev runs take `-PwithJade`, `-PwithShaders` (Sodium,
Iris, Euphoria Patches and the Complementary Unbound pack) and, on Fabric, `-PwithModMenu`, for example
`./gradlew :heavymatica:fabric:runClient -PwithModMenu`.
