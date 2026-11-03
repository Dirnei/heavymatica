# Contributing to Heavymatica

What is built and what comes next: [ROADMAP.md](ROADMAP.md).

## Layout

```
src/
  api/           OpenAPI contract of the Heavymatica Cloud (source of truth, code is generated from it)
  cloud/         .NET solution: the cloud service, its tests and the Aspire AppHost
  web/player/    the player website (Astro with Svelte islands), served by the cloud
  mod/           Gradle build of the mods: per mod a loader-free common project and one project per loader
  conformance/   schematic sample files and expected results, shared by the cloud and mod tests
  branding/      the logo source and its exports (`python src/branding/generate.py`)
openspec/        specs and changes
```

## Building

- Cloud: `dotnet build src/cloud/Heavymatica.slnx`, run locally with `aspire run` from `src/cloud`.
- Mod: `./gradlew build` from `src/mod` builds and tests both jars: `heavymatica/neoforge/build/libs` and
  `heavymatica/fabric/build/libs`.
- Contract lint: `npx @stoplight/spectral-cli lint src/api/heavymatica-cloud.v1.yaml` from the repository root.

## Mod projects

`src/mod/heavymatica` holds three Gradle projects:

- `common`: all features and their tests. It compiles against vanilla Minecraft only, so no loader API may appear here.
  What a loader has to provide (events, HUD layers, key mappings, render pipelines, carried containers) goes through
  the interfaces in `io.github.dirnei.mc.heavymatica.platform`, found with `Platform.get()`.
- `neoforge`: the NeoForge entry points and those interfaces on NeoForge's events. Settings are NeoForge's own config,
  built from the shared settings schema in `HeavymaticaConfig`.
- `fabric`: the same on Fabric API, with small mixins where Fabric API has no matching event. Settings are written by
  `TomlSettingsFile` in the same format as NeoForge's.

Both loader projects compile `common`'s sources into their own jar. Vanilla members that need wider access are listed
twice: in `neoforge/src/main/resources/META-INF/accesstransformer.cfg` and in
`fabric/src/main/resources/heavymatica.classtweaker`. `NeoForgeSettingsTest` and `TomlSettingsFileTest` check that both
loaders write the same default settings file (`common/src/test/resources/settings/heavymatica-client.default.toml`).

Dev runs take `-PwithJade`, `-PwithShaders` (Sodium, Iris, Euphoria Patches and Complementary Unbound) and, on Fabric,
`-PwithModMenu`, for example `./gradlew :heavymatica:fabric:runClient -PwithModMenu`.

## Releasing

A release is a tag push, nothing else. No file is edited and nothing is committed:

```
git tag -a heavymatica-v0.3.0 -m "Optional text above the notes"
git push origin heavymatica-v0.3.0
```

- The version comes from the tag (`heavymatica-v<major>.<minor>.<patch>`, optionally with a `-suffix`). Any other form
  fails. Builds without a tag report `0.0.0-dev`.
- The notes list the `feat` and `fix` commits since the previous `heavymatica-v*` tag that touch `src/mod/heavymatica`.
  An annotated tag's message goes above them; a lightweight tag (`git tag heavymatica-v0.3.0`) adds nothing. Preview
  them with `sh .github/scripts/release-notes.sh <tag>` after tagging locally.
- The first release has no previous tag, so its notes are the tag message alone: tag it annotated.
- With no `feat` or `fix` commit and no message, the release fails before anything is uploaded.
- Changes outside `src/mod/heavymatica` that alter the jar, such as `src/mod/build.gradle`, `src/mod/gradle.properties`
  or the contract in `src/api`, are not listed. Mention them in the tag message.
- `.github/workflows/release.yml` builds and tests the tagged commit, then creates the GitHub release with the Fabric
  and the NeoForge jar and uploads both to CurseForge and Modrinth, each marked with its loader. On Modrinth the
  version numbers carry the loader (`0.3.0+fabric`, `0.3.0+neoforge`), since each jar is its own version there. A
  failing build of either jar stops the whole release. Versions 0.x are published as alpha; from 1.0 a version containing `alpha` or
  `beta` is published as that, anything else as release.
- Repository settings: variables `CURSEFORGE_ID_HEAVYMATICA` and `MODRINTH_ID_HEAVYMATICA` with secrets
  `CURSEFORGE_TOKEN` and `MODRINTH_TOKEN`. A platform without its project ID variable is skipped.
- If publishing fails, re-run the failed job in the Actions tab. To redo a wrong tag, delete its GitHub release, then
  the tag (`git tag -d <tag>` and `git push origin :refs/tags/<tag>`), and push it again.

Every push and pull request is built and tested by `.github/workflows/build.yml`.

## Local development

1. Start the cloud: `aspire run` (or `dotnet run --project Heavymatica.Cloud.AppHost`) from `src/cloud`. MongoDB, MinIO and the
   service start together; the service listens on http://localhost:5180.
2. Point the dev client at the cloud's DevMojang, since dev clients have no Microsoft session: in
   `src/mod/heavymatica/neoforge/runs/client/config/heavymatica-client.toml` (or `fabric/runs/...`) set
   `devSessionJoinUrl = "http://localhost:5180/dev/mojang"`.
3. Start the client: `./gradlew :heavymatica:neoforge:runClient` or `./gradlew :heavymatica:fabric:runClient` from
   `src/mod`, then open the library with `H` in a world. Each loader has its own run folder, `heavymatica/<loader>/runs`.

Dev client runs always talk to `http://localhost:5180`, whatever the config file says: released mods default to
`https://api.heavymatica.com`, and the dev run overrides it so it never reaches production by accident. Set
`HEAVYMATICA_CLOUD_URL` to use another local address. The game asks the cloud whether it is open only when the library
opens; the local cloud always says open. To test against a cloud that says closed, set `access = "ON"` in the `[cloud]`
section of the client config.

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

The cloud renders a thumbnail for every version that has none: flat-shaded isometric blocks coloured from
`Features/Schematics/Thumbnails/block-colors.json` (exported from the game by the mod's `exportBlockColors` dev step).
Website uploads are rendered within seconds; game uploads wait a grace period first, because the game attaches its
own, sharper thumbnail right after uploading, and that always replaces a rendered one. Versions stored before this
are rendered in the background, newest first. The `Thumbnails` section configures it:

| Setting | Default | Effect |
|---|---|---|
| `Thumbnails:Enabled` | `true` | `false` stops rendering; rendered thumbnails stay. |
| `Thumbnails:GracePeriod` | `00:01:00` | How long a game upload waits for its own thumbnail. |
| `Thumbnails:PollInterval` | `00:00:05` | How often an idle worker looks for due versions. |
| `Thumbnails:ClaimTime` | `00:05:00` | How long a version stays with one worker before another may retry it. |
| `Thumbnails:RenderTimeout` | `00:00:30` | How long one rendering may take; a timeout counts as a failed attempt. |
| `Thumbnails:MaxAttempts` | `3` | Failed attempts before a version is left without a thumbnail. |
| `Thumbnails:MaxDrawnBlocks` | `2000000` | Above this many blocks only every k-th block per axis is drawn. |

### Admin site

Admins work on a separate admin host: the cloud opens a second listener for the admin API (`/admin/v1`) and the admin
site, and answers there only for the admin host name. The public listener never routes to admin paths, and the admin
listener serves nothing of the player site or API. Admins have no player account; they sign in with passkeys only and
need two of them before they can do anything else. The AppHost runs the admin site's dev server
(`src/web/admin`) on http://localhost:4322; it forwards `/admin/v1` to the admin listener on port 5181.

| Setting | Effect |
|---|---|
| `Admin:Url` | The admin listener, for example `http://localhost:5181`. Without it and `Admin:Host`, nothing admin exists. |
| `Admin:Host` | The only host name the admin listener answers for, and the passkeys' relying party ID. |
| `Admin:SiteUrl` | Where admins open the site, for invitation links and the setup command; defaults to `https://<Admin:Host>/`. |
| `Admin:SiteRoot` | The built admin site the cloud serves on the admin listener (`wwwroot-admin`, filled on publish). |

The first admin registers with a setup code from the server, valid for 15 minutes and once. A new code replaces the
previous one; `--admin <id>` makes it a recovery code that adds a passkey to that admin. From `src/cloud`:
```
dotnet run --project Heavymatica.Cloud -- admin setup-code
```
It reads the same configuration as the service, so run it where the service's connection string is set (with the
AppHost running, copy `ConnectionStrings__heavymatica` from the dashboard's cloud resource). Further admins are
invited from the admin site with a link valid for 24 hours and once.

Website commands run in `src/web/player`: `npm run dev`, `npm run build`, `npm run check` and `npm test`.

### Development aids

The dev client runs take a few environment variables, so screens can be checked without clicking:

| Variable | Effect |
|---|---|
| `HEAVYMATICA_CLOUD_URL` | The cloud address of the dev client, `http://localhost:5180` when unset. |
| `HEAVYMATICA_DEV_SCREEN` | A `;`-separated script of UI steps (`signin`, `library`, `search=...`, `clickCard=0`, `clickButton=<translation key>`, `drop=<dir>`, `screenshot=<name>`, `quit`, ...; see `DevScreens`). Screenshots land in `runs/client/screenshots/`. |
| `HEAVYMATICA_THUMBNAIL_SPIKE` | Renders the thumbnail of the named schematic file once the game has loaded and writes `<file>.png` next to it. |
| `HEAVYMATICA_QUICKPLAY` | Joins this server on start, for example `localhost:25565`. |
| `HEAVYMATICA_QUICKPLAY_WORLD` | Opens this singleplayer world on start, for example `Testworld`. |
| `HEAVYMATICA_DEV_USER` | Plays as another offline player, `<name>[,<uuid>]`. A UUID the cloud has not seen signs in as an unknown Minecraft account. |

Example: `HEAVYMATICA_DEV_SCREEN="signin;library;wait=60;screenshot=library;quit" ./gradlew :heavymatica:fabric:runClient`

Client settings can also be changed in the game: the gear in the library, Mods → Heavymatica → Config on NeoForge, or
Mod Menu's config button on Fabric (dev runs with `-PwithModMenu`). The cloud address
and `devSessionJoinUrl` stay in the config file only.

For screen checks, `details` opens the selected entry's details page, `materials` the materials screen and `pick=<block>` its
block picker (the first row when empty), `settings` and `settingsSection=<section>` the settings screen, and `mods` with
`selectMod=<mod id>` and `modConfig=<mod id>` the mods list (Mod Menu on Fabric).

For building checks, `anchorLocal=<name>`, `alignSelected=<x>,<y>,<z>` and `lockSelected` set up a locked placement from a
local entry, `lookAt=<x>,<y>,<z>` aims the crosshair, `logClientBlock`, `logHit` and `logBuild` log the world block, vanilla's hit
and the Easy Place / placement restriction settings with the action bar, and `serverCommand=<command>` runs a command on a
dedicated server (with `HEAVYMATICA_QUICKPLAY`).

