# Design

## Context

- `DevScreens` runs a `;`-separated step script from the `heavymatica.dev.screen` system property: screens, keys,
  commands (`command=`), the wheel, capture, placements, `screenshot=<name>` into `<run dir>/screenshots/`, `quit`.
  It can open an existing world (`openWorld=`) but not create one.
- Loom passes system properties with quotes stripped, so long scripts with quotes are fragile on the command line.
- The AppHost already supports `Heavymatica:EphemeralStorage=true` (no data volumes) and starts the cloud on 5180,
  the player website's dev server on 4321 and the admin site on 4322.
- Website sign-in only exists through GitHub. DevMojang (`/dev/mojang`) is the Development-only stand-in for the game.
- Dev clients sign in through DevMojang when `devSessionJoinUrl` is set and can play as a fixed profile with
  `HEAVYMATICA_DEV_USER=<name>,<uuid>`.

## Goals / Non-Goals

**Goals:**

- One command makes every screenshot from a fresh checkout.
- Nothing of the developer's dev runs or dev cloud is touched.

**Non-Goals:**

- Running in CI (needs a GPU-capable desktop for Minecraft; images are committed instead).
- Pixel-identical images between runs; the scenes must be the same, not the bytes.
- NeoForge screenshots (the features look the same; Fabric is what most readers use).

## Decisions

### D1: A Node script orchestrates, without Aspire

`docs/screenshots/run.mjs` (`npm run screenshots` in `docs`):

1. Refuses to start when its ports (5190 cloud, 27190 MongoDB, 9190 MinIO) are in use.
2. Builds the player website (`astro build --outDir`) and the cloud (`dotnet build -c Release -o`) into
   `docs/screenshots/.work`, starts two containers with fixed names (`heavymatica-docs-mongo`,
   `heavymatica-docs-minio`) and runs the cloud executable in Development, serving the built website itself
   (`Web:Root`).
3. Resets `src/mod/heavymatica/fabric/runs/docs`, writes its `options.txt` (GUI scale, no onboarding or tutorial,
   fixed FOV, sound off) and `config/heavymatica-client.toml` (DevMojang address), and runs
   `./gradlew :heavymatica:fabric:runDocs` with `HEAVYMATICA_DEV_SCREEN=@<steps file>` and
   `HEAVYMATICA_DEV_USER=HeavyDocs,<fixed uuid>`.
4. Runs the website steps with Playwright, signed in through the development sign-in.
5. Converts the screenshots to WebP into `docs/src/assets/screenshots/` and stops the cloud and removes its two
   containers, also on failure.

The developer's own local cloud can keep running: nothing is shared with it.

*Alternatives:* a second AppHost. Rejected: the developer's running AppHost locks the Debug build output, Astro allows
one dev server per folder, and stopping a second Aspire instance cleanly (`aspire stop`) cannot tell it apart from the
developer's. A shell script. Rejected: it has to run on Windows, macOS and Linux and manage processes.

### D2: A docs client run

Loom gets a second client run, `docs` (task `runDocs`), with run folder `runs/docs`, a fixed window size (`--width 1600
--height 900`) and the screenshot cloud's address. The developer's `runs/client` is never used.

### D3: Step files

`HEAVYMATICA_DEV_SCREEN=@<path>` reads the script from a file: one step per line, blank lines and `#` comments
ignored. The `;` form keeps working.

### D4: World from a seed

A new step `createWorld=<name>,<seed>` creates a creative world with cheats, the default world type and structures,
and enters it. The scene's commands then fix the rest: `gamerule` (no daylight, weather or mob spawning, no fire
spread), `time set`, `weather clear`, `tp` and `kill @e[type=!player]`. The seed lives in the steps file.

### D5: Scenes are built with commands

The house in the screenshots is built by `fill`/`setblock` commands in the steps file, then captured with capture
mode, saved and uploaded, so the screenshots show capture as well. The build is then cleared and placed again as a
ghost, partly built with commands to show the comparison colours. No schematic file is committed for it.

### D6: Development website sign-in

`GET /dev/web/sign-in?minecraft=<profile id>&returnTo=<path>` in the Identity slice, mapped next to DevMojang and only
in Development. It signs in the Minecraft identity's account through the same path as a GitHub sign-in
(`Accounts.SignInAsync` with the Minecraft provider, `GitHubSignIn.StartWebSessionAsync`) and redirects to
`returnTo` (made safe like the GitHub flow). An unknown identity answers 404. The admin listener already refuses
everything outside its own paths. Browsers send localhost cookies to every port, so a session started on the cloud's
port is valid on the website's dev server.

### D7: Website screenshots with Playwright

Playwright (Chromium) is a docs dev dependency used only by the script. The steps file is a small module listing
pages, actions and screenshot names. The browser window is 1280×800.

### D8: Images

WebP files (quality 82) in `docs/src/assets/screenshots/`, referenced from pages with Markdown image syntax. The raw
PNGs of one run are about 29 MB; as WebP about 2.6 MB, so reruns don't bloat the repository. A full run replaces the
folder, so renamed or dropped shots don't linger.

### D9: Steps the walkthrough needed

`captureCorner=<x>,<y>,<z>` (sets a capture corner on a block, through `CaptureController.clickAt`), `capture=click`
and `setText=<text>` (the capture's name). The seed is 8675309: a forest by a river; the scene clears a meadow at
4..34, 62..92 and builds the cottage at 15..23, 69..77, 79..87.

## Risks / Trade-offs

- [The seed's terrain changes with a Minecraft update] → Rerun the script after updating; that is its purpose.
- [Timing: chunk loading, rendering and uploads take varying time] → Steps wait on conditions where steps exist
  (`waitWorld`, `wait=`), with generous waits before screenshots.
- [A development sign-in that skips authentication] → Same guard and tests as DevMojang: Development only, 404
  otherwise, never on the admin listener.

## Migration Plan

Nothing to migrate. The script is run by hand after UI changes and before releases.
