# Contributing to Heavymatica

Thanks for helping. The full developer documentation is at
[docs.heavymatica.com/develop](https://docs.heavymatica.com/develop/contributing/): architecture, layout, the mod
projects, the cloud, the websites, the API, development aids and releasing. What comes next is in
[ROADMAP.md](ROADMAP.md).

## Making a change

- Bugs and ideas go into [issues](https://github.com/Dirnei/heavymatica/issues).
- Features and behaviour changes are planned as an OpenSpec change in `openspec/changes/` before code is written.
  Small fixes don't need one.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat(placement): add remote overlay`),
  subject line only, at most 74 characters. `feat` and `fix` subjects end up in the release notes, so write them for
  players.
- Comment only what the code can't say itself, without reasoning, history or OpenSpec references.

## Build

Requirements: JDK 25, the .NET SDK from `src/cloud/global.json` with Docker, and Node 22.

```
cd src/mod && ./gradlew build                  # both mod jars and all mod tests
dotnet test src/cloud/Heavymatica.slnx         # the cloud (starts MongoDB and MinIO containers)
cd src/web/player && npm run check && npm test # the player website
cd docs && npm ci && npm run build             # the documentation site
```

## Run locally

1. Start the cloud with `aspire run` from `src/cloud`. It listens on http://localhost:5180 and starts the websites'
   dev servers too (player website on http://localhost:4321).
2. Set `devSessionJoinUrl = "http://localhost:5180/dev/mojang"` in the dev client's
   `src/mod/heavymatica/<loader>/runs/client/config/heavymatica-client.toml`, since dev clients have no Microsoft
   session.
3. Start a client from `src/mod`: `./gradlew :heavymatica:fabric:runClient` or `./gradlew :heavymatica:neoforge:runClient`.
   Add `-PwithModMenu` (Fabric), `-PwithJade` or `-PwithShaders` to run with those mods.
4. Open the library with `H` in a world.

Tips:

- Dev clients always talk to `http://localhost:5180`; `HEAVYMATICA_CLOUD_URL` picks another local cloud.
- `HEAVYMATICA_QUICKPLAY_WORLD=<world>` opens a singleplayer world on start; `HEAVYMATICA_DEV_SCREEN` scripts UI
  steps and screenshots. See [Development aids](https://docs.heavymatica.com/develop/dev-aids/).
- Changing a key? `./gradlew :heavymatica:common:test -PwriteDocs` updates the docs' key reference.
- The docs site runs with `npm run dev` in `docs` on http://localhost:4323.
