## Why

The first public alphas go out before the Heavymatica Cloud does. Today the mod points at `http://localhost:5180` and shows
Sign in to everyone, so a public jar would offer a cloud that doesn't exist. The cloud UI still has to stay in the mod,
because it is used for testing. And when the cloud does open, that should happen without shipping a new mod version. The
domain `heavymatica.com` is now bought, so the mod can get a real default address and ask the cloud whether it is open.

## What Changes

- **Status endpoint:** a new unauthenticated `GET /v1/status` in the contract and the cloud. It answers whether sign-in
  is `open` or `closed`, and optionally the minimum mod version the cloud still supports (`minClient`). Whether sign-in
  is open is a service setting, `closed` unless configured.
- **Production address:** the mod's default `cloudUrl` becomes `https://api.heavymatica.com`. Dev runs started through
  Gradle point the client at `http://localhost:5180` through a run property that wins over the config file, so a dev run
  never talks to production.
- **Check when the library opens:** the client asks for the status only when the player opens the library. That happens
  in the background, at most once per game session for a successful answer, and the library's Refresh asks again. Local
  entries show at once. Nothing is asked at game start or from any other screen.
- **Who sees the cloud:**
  - A player without a stored session sees Sign in and the other cloud parts only when the status says `open`. `closed`,
    an unreachable cloud or a timeout hide them.
  - A stored session always wins: a signed-in player keeps the cloud while it is `closed`, and gets the existing offline
    library when it is unreachable.
  - A mod older than `minClient` offers no sign-in, and keeps a stored session's last known listing offline, with a hint
    to update.
- **New client setting `cloud.access`:** `AUTO` (default, ask the status), `ON` (skip the check and always show the cloud,
  for testers while it is closed) or `OFF` (never show the cloud and never contact it, not even for the status). The
  settings screen that shows it as "Heavymatica Cloud: Automatic / Always / Never" is built in change `ui-glass-redesign`.
  This change adds the setting and its behaviour.
- **BREAKING (spec):** "First sign-in is explicit" no longer means "no network calls before the first sign-in". It
  becomes: without a stored session, nothing contacts the cloud until the player opens the library. The status request
  is the only call before sign-in, and it carries nothing about the player.
- Not in this change: refusing new accounts while sign-in is `closed`. `closed` only hides the UI. The cloud keeps
  creating accounts for anyone who signs in, as agreed for now. Hosting and deployment are also out of scope.

## Capabilities

### New Capabilities

- `cloud/service-status`: the public status endpoint, its sign-in state and minimum client version, and how they are
  configured.
- `client/cloud-availability`: when the client asks for the status, how the answer, a stored session and the
  `cloud.access` setting decide whether the cloud parts show, and which address the client uses in production and in
  dev runs.

### Modified Capabilities

- `client/cloud-account`:
  - "First sign-in is explicit" is relaxed to the library-open rule above.
  - "Account status visible" applies only while the cloud parts are shown.

## Impact

- **Contract:** `src/api/heavymatica-cloud.v1.yaml` gets a `Status` tag, the `getStatus` operation and a `ServiceStatus`
  schema. The C# controller and the Java client are regenerated from it.
- **Cloud:**
  - a new `Features/Status` slice with options, an `IStatusController` implementation and registration in
    `ContractServices`
  - the AppHost opens sign-in for local runs
  - tests under `Heavymatica.Cloud.Tests/Features/Status`
- **Mod:**
  - `HeavymaticaConfig`: new default `cloudUrl`, a run-property override, and `cloud.access`
  - a new availability component in the `account` package
  - `CloudClient.Apis` gets the status API
  - `Library.file` makes no download without a session or with access `OFF`
  - the library screen's account widgets follow the availability
- **Build:** the `runs.client` block in `src/mod/build.gradle` sets the dev cloud address.
- **Docs:** README local development, and ROADMAP "Open before a public release".
- Change `release-pipeline` writes the CurseForge/Modrinth disclosure from this behaviour: "contacts heavymatica.com
  when you open the schematic library, can be turned off".
