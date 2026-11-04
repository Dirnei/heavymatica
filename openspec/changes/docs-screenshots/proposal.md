# Proposal

## Why

The documentation site explains every feature in words only. Players learn a placement UI faster from pictures, and
pictures taken by hand go stale with every UI change. Screenshots that a script takes from a fresh world and a fresh
local cloud can be retaken after any change, by anyone with the repository.

## What Changes

- A screenshot script, run locally, takes every documentation screenshot from scratch: it starts a local cloud with
  throwaway storage, starts a Fabric client in its own run folder as a fixed documentation player, creates a world
  from a seed in the repository, builds and captures a small house with commands, and walks through the features while
  taking screenshots. A browser then takes the player website's screenshots, signed in as the same player.
- The world, the schematics and the cloud data are not committed. The seed, the game rules, the commands and the steps
  are; the resulting PNGs are committed into the docs.
- New development steps for the dev client: create a world from a seed, read a step script from a file, and anything
  else the walkthrough needs.
- The cloud gets a development-only website sign-in: in the Development environment, a browser can sign in to the
  account of a known Minecraft identity without GitHub. It does not exist outside Development.
- The player pages of the documentation show the screenshots.

## Capabilities

### New Capabilities

- `web/docs-screenshots`: how documentation screenshots are produced: reproducible from the repository, isolated from
  the developer's own data, and committed as images.

### Modified Capabilities

- `cloud/identity`: adds a development-only website sign-in by Minecraft identity.

## Impact

- `src/cloud/Heavymatica.Cloud`: a Development-only endpoint and its tests.
- `src/mod/heavymatica/common`: new `DevScreens` steps. `src/mod/heavymatica/fabric/build.gradle`: a second client run
  (`runs/docs`).
- `docs/screenshots/`: the scene (seed, rules), the client steps, the website steps and the script; Playwright as a
  docs dev dependency; images under `docs/src/assets/screenshots/`.
- Running the script needs Docker, the .NET SDK, JDK 25 and Node, and the developer's own local cloud stopped, since
  the script uses the same ports.
