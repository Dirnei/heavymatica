# Tasks

## 1. Cloud

- [x] 1.1 Development website sign-in endpoint in the Identity slice, mapped only in Development
- [x] 1.2 Tests: signs in a known Minecraft identity, unknown identity 404, missing in Production, not on the admin listener

## 2. Dev client

- [x] 2.1 `HEAVYMATICA_DEV_SCREEN=@<file>` step files with comments and blank lines
- [x] 2.2 `createWorld=<name>,<seed>` step
- [x] 2.3 Fabric `docs` client run in `runs/docs` with a fixed window size
- [x] 2.4 Any further steps the walkthrough needs

## 3. Script

- [x] 3.1 `docs/screenshots/run.mjs`: port check, own cloud and containers, run folder setup, client run, website run, copy, cleanup on failure
- [x] 3.2 Client steps file: world, rules, house, capture and upload, library, placement, wheel, layers, comparison, mistake pointer, materials, targeting HUD, settings
- [x] 3.3 Website steps with Playwright: library, details, upload (the landing page is left out while its "not connected to the game" notice is outdated; the link code page needs a code from a game run)

## 4. Docs

- [x] 4.1 Run the script and commit the screenshots
- [x] 4.2 Add the screenshots to the player pages
- [x] 4.3 Developer docs: how to run the script and what it needs

## 5. Verification

- [x] 5.1 Run the script twice; the scenes match; the developer's run folder and cloud data are untouched
- [x] 5.2 Cloud tests and `./gradlew build` pass; the docs build passes
