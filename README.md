# Heavymatica

Schematics for Minecraft with a simple placement UI and a cloud library that follows you to every world and server.
What is built and what comes next: [ROADMAP.md](ROADMAP.md).

Builds made in the game can be saved as schematics: hold the remote, pick Capture on the wheel (V), right-click two
corner blocks, then move the active corner with the arrows and numpad + / - (Shift: 10 blocks) and swap corners with
Shift + scroll. Capture on the wheel saves the area as a local `.litematic` library entry, ready to upload. Export in
the library writes any entry into `.minecraft/schematics/` for other tools.

## Layout

```
src/
  api/           OpenAPI contract of the Heavymatica Cloud (source of truth, code is generated from it)
  cloud/         .NET solution: the cloud service, its tests and the Aspire AppHost
  web/player/    the player website (Astro with Svelte islands), served by the cloud
  mod/           Gradle build of the NeoForge mods
  conformance/   schematic sample files and expected results, shared by the cloud and mod tests
openspec/        specs and changes
```

## Building

- Cloud: `dotnet build src/cloud/Heavymatica.slnx`, run locally with `aspire run` from `src/cloud`.
- Mod: `./gradlew build` from `src/mod`.
- Contract lint: `npx @stoplight/spectral-cli lint src/api/heavymatica-cloud.v1.yaml` from the repository root.

## Local development

1. Start the cloud: `aspire run` (or `dotnet run --project Heavymatica.Cloud.AppHost`) from `src/cloud`. MongoDB, MinIO and the
   service start together; the service listens on http://localhost:5180.
2. Point the dev client at the cloud's DevMojang, since dev clients have no Microsoft session: in
   `src/mod/heavymatica/runs/client/config/heavymatica-client.toml` set
   `devSessionJoinUrl = "http://localhost:5180/dev/mojang"`.
3. Start the client: `./gradlew :heavymatica:runClient` from `src/mod`, then open the library with `H` in a world.

### Player website

The AppHost also runs the website's dev server (`src/web/player`, Astro) on http://localhost:4321. It forwards `/v1`
to the cloud, so pages and API share one origin, as in production, where the cloud serves the built site itself
(`dotnet publish` builds it into `wwwroot`). Set `Heavymatica:WebsitePort` to use another port, or
`Heavymatica:WithWebsite=false` to leave the website out.

Sign-in on the website needs a GitHub OAuth app; without one, the website says that sign-in is unavailable and
everything else works.

1. GitHub → Settings → Developer settings → OAuth Apps → New OAuth App.
2. Homepage URL `http://localhost:4321`, authorization callback URL
   `http://localhost:4321/v1/web/sign-in/github/callback`. GitHub matches host and port exactly, so the app works only
   through the dev server; a different website port needs these URLs changed too.
3. Generate a client secret.
4. Store both in the AppHost's user secrets, from `src/cloud`:
   ```
   dotnet user-secrets set Parameters:github-client-id <client id> --project Heavymatica.Cloud.AppHost
   dotnet user-secrets set Parameters:github-client-secret <client secret> --project Heavymatica.Cloud.AppHost
   ```
   Environment variables `Parameters__github-client-id` and `Parameters__github-client-secret` work as well.
5. Restart the AppHost and open http://localhost:4321.

Linking sign-ins (link codes, and the first sign-in choice between creating an account and joining one) is on only
while `Accounts:LinkBaseUrl` is set to the website origin that redeems the codes; link codes point at
`<LinkBaseUrl>/link/?code=...`. The AppHost sets it to the dev server, `http://localhost:4321` (or
`Heavymatica:WebsitePort`). Without it, link codes answer `linking_unavailable`, `/v1/me` reports
`linkingAvailable: false`, an unknown Minecraft identity gets an account at once, and "Sign in with GitHub" without
an account says there is none yet.

Website commands run in `src/web/player`: `npm run dev`, `npm run build`, `npm run check` and `npm test`.

### Development aids

The dev client runs take a few environment variables, so screens can be checked without clicking:

| Variable | Effect |
|---|---|
| `HEAVYMATICA_DEV_SCREEN` | A `;`-separated script of UI steps (`signin`, `library`, `search=...`, `clickCard=0`, `clickButton=<translation key>`, `drop=<dir>`, `screenshot=<name>`, `quit`, ...; see `DevScreens`). Screenshots land in `runs/client/screenshots/`. |
| `HEAVYMATICA_THUMBNAIL_SPIKE` | Renders the thumbnail of the named schematic file once the game has loaded and writes `<file>.png` next to it. |
| `HEAVYMATICA_QUICKPLAY` | Joins this server on start, for example `localhost:25565`. |
| `HEAVYMATICA_QUICKPLAY_WORLD` | Opens this singleplayer world on start, for example `Testworld`. |
| `HEAVYMATICA_DEV_USER` | Plays as another offline player, `<name>[,<uuid>]`. A UUID the cloud has not seen signs in as an unknown Minecraft account. |

Example: `HEAVYMATICA_DEV_SCREEN="signin;library;wait=60;screenshot=library;quit" ./gradlew :heavymatica:runClient`

