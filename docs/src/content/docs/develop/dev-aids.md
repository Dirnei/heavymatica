---
title: Development aids
description: Environment variables and scripted steps that drive a dev client without clicking.
---

The dev client runs take a few environment variables, so screens can be checked without clicking:

| Variable | Effect |
|---|---|
| `HEAVYMATICA_CLOUD_URL` | The cloud address of the dev client, `http://localhost:5180` when unset. |
| `HEAVYMATICA_DEV_SCREEN` | A `;`-separated script of UI steps (`signin`, `library`, `search=...`, `clickCard=0`, `clickButton=<translation key>`, `drop=<dir>`, `screenshot=<name>`, `quit`, ...; see `DevScreens`). Screenshots land in `runs/client/screenshots/`. |
| `HEAVYMATICA_THUMBNAIL_SPIKE` | Renders the thumbnail of the named schematic file once the game has loaded and writes `<file>.png` next to it. |
| `HEAVYMATICA_QUICKPLAY` | Joins this server on start, for example `localhost:25565`. |
| `HEAVYMATICA_QUICKPLAY_WORLD` | Opens this singleplayer world on start, for example `Testworld`. |
| `HEAVYMATICA_DEV_USER` | Plays as another offline player, `<name>[,<uuid>]`. A UUID the cloud has not seen signs in as an unknown Minecraft account. |

Example:

```
HEAVYMATICA_DEV_SCREEN="signin;library;wait=60;screenshot=library;quit" ./gradlew :heavymatica:fabric:runClient
```

## Screen steps

`details` opens the selected entry's details page, `materials` the materials screen and `pick=<block>` its block picker
(the first row when empty), `settings` and `settingsSection=<section>` the settings screen, and `mods` with
`selectMod=<mod id>` and `modConfig=<mod id>` the mods list (Mod Menu on Fabric).

## Building steps

`anchorLocal=<name>`, `alignSelected=<x>,<y>,<z>` and `lockSelected` set up a locked placement from a local entry,
`lookAt=<x>,<y>,<z>` aims the crosshair, `logClientBlock`, `logHit` and `logBuild` log the world block, vanilla's hit
and the Easy Place / placement restriction settings with the action bar, and `serverCommand=<command>` runs a command
on a dedicated server (with `HEAVYMATICA_QUICKPLAY`).

## Settings in the game

Client settings can also be changed in the game: the gear in the library, Mods → Heavymatica → Config on NeoForge, or
Mod Menu's config button on Fabric (dev runs with `-PwithModMenu`). The cloud address and `devSessionJoinUrl` stay in
the config file only.
