## Context

- **The mod already restores without network:** `HeavymaticaClient.setup` builds one `CloudAccount` from
  `HeavymaticaConfig.cloudUrl()` and calls `account.restore()` and `library.loadStored()`. Both make no network call:
  restore only reads the session file.
- **The library screen starts the cloud traffic:**
  - `LibraryScreen.init` calls `refreshCloud()` when the cloud state is `NONE` or `OFFLINE`.
  - That calls `library.refresh()`, which is a no-op when signed out, and `account.refreshAccount()`.
  - The Refresh, Sign in/out and Account buttons are always added.
- **Placements can download behind the library's back:** `PlacementSession` loads cloud versions through
  `Library.file`. On a cache miss that asks the cloud for links and downloads, even without a session. Without a token
  the call fails, but it still reaches the network. `Library.thumbnail` already checks `account.signedIn()`, and `file`
  doesn't.
- **The config holds the address:** `HeavymaticaConfig` defines `cloud.cloudUrl` with the default
  `http://localhost:5180`, plus the dev-only `devSessionJoinUrl`. There is no other way to override the address.
- **Dev runs pass settings through system properties:** `src/mod/build.gradle`'s `runs.configureEach` already forwards
  `HEAVYMATICA_THUMBNAIL_SPIKE` and `HEAVYMATICA_DEV_SCREEN` as system properties. `runs.client` reads
  `HEAVYMATICA_QUICKPLAY*` from the environment.
- **The cloud is generated from the contract:** NSwag generates one partial controller interface per first tag from
  `src/api/heavymatica-cloud.v1.yaml`. Features implement it (`AccountApi : IAccountController`) and are registered in
  `Infrastructure/Contract/ContractServices`.
- **Anonymous access is per tag:** the fallback policy requires an authenticated user. Anonymous actions get
  `AllowAnonymous` metadata from a per-controller `Configure…Endpoint` hook, which is how Auth does it. Options bind
  from config sections (`AccountsOptions.Section = "Accounts"`).
- **The Java client is generated from the same contract** (`openApiGenerate`, all APIs). `CloudClient.Apis` holds the
  generated APIs.
- **The AppHost passes the cloud address to the website** as `HEAVYMATICA_CLOUD_URL`. The player website calls `/v1`
  on its own domain.

## Goals / Non-Goals

**Goals:**
- One availability decision in one place, free of the game and unit-tested. Every cloud part of the UI asks it.
- No request to the cloud before the player opens the library, unless a stored session needs one.
- A dev run can never reach production by accident.

**Non-Goals:**
- Refusing sign-ins or new accounts while `closed`.
- Hosting, DNS, TLS and the reverse proxy for the production layout.
- The settings screen that shows `cloud.access` (change `ui-glass-redesign`), and the redesign of the cloud screens
  (change `cloud-ui-redesign`).
- Any server-side (game server) use of the cloud.

## Decisions

### D1: Production layout and the mod's default

The production layout, recorded here because the default address is baked into every released jar:

| Address | Serves |
|---|---|
| `https://heavymatica.com` | website, with `/v1` forwarded to the cloud (the website needs the same origin for its sign-in) |
| `https://api.heavymatica.com` | the cloud API directly. The mod's default `cloudUrl` |
| `https://admin.heavymatica.com` | the admin site (`AdminOptions.Host`) |
| `http://localhost:5180` | local development |

The mod uses `api.` so that the website's hosting can change without breaking jars already out there.

- *Alternative: `https://heavymatica.com`.* Rejected: old jars would tie the website forever to forwarding `/v1`.

### D2: A dev-run property that wins over the config

`HeavymaticaConfig.cloudUrl()` returns the system property `heavymatica.cloud.url` when it is set, and only otherwise
the config value. `runs.client` in `src/mod/build.gradle` sets that property to `HEAVYMATICA_CLOUD_URL` from the
environment, falling back to `http://localhost:5180`. The AppHost already uses that variable name for the website.

The config file keeps the production default, so nothing in it needs editing for dev. Released jars never see the
property.

- *Alternative: write localhost into the dev run's config file.* Rejected: a fresh clone writes the production
  default on its first start, and the config would only be right after a manual edit.

### D3: `CloudAvailability`, a small state machine in `account`

`CloudAvailability`, free of the game, takes:
- a status fetcher (`Supplier<CompletableFuture<ServiceStatus>>`)
- the running mod version
- a `Supplier<CloudAccess>` for the setting
- an `Executor` for completion

It has two public operations:
- **`libraryOpened()`:** with `AUTO`, it starts a fetch unless an answer is already kept or a fetch is running.
- **`refresh()`:** with `AUTO`, it always fetches again.

**Answer states:** `UNKNOWN` (not asked or pending), `OPEN`, `CLOSED`, `UNREACHABLE`, `TOO_OLD`.
- Only `OPEN`, `CLOSED` and `TOO_OLD` are kept for the game session.
- `UNREACHABLE` is asked again on the next `libraryOpened()`.
- A timeout of 5 s counts as `UNREACHABLE`.

**Derived views,** given whether a stored session exists:
- `showCloud(hasSession)`:
  - `OFF` gives false.
  - `ON` gives true.
  - `AUTO` gives `hasSession || state == OPEN`.
  - `TOO_OLD` still shows the cloud parts, so the update hint has a place.
- `offerSignIn(hasSession)`: `showCloud` and no session and not `TOO_OLD`.
- `mayContact()`: `OFF` gives false.
- `tooOld()`: true when the answer is `TOO_OLD`.

It notifies listeners on change, the same way `CloudAccount.onChange` does. A stored session counts when
`CloudAccount.status()` is not `SIGNED_OUT`. `CHOOSING` and `EXPIRED` count as having one, because both came from a sign-in.

- *Alternative: put the logic into `LibraryScreen`.* Rejected: the screen is about to be rewritten by
  `ui-glass-redesign`, and the rules need unit tests without the game.
- *Alternative: check at game start.* Rejected with the user: the first contact must follow from the player using the
  mod.

### D4: The status request goes through the anonymous client

`CloudAccount` already has `auth`, a `CloudClient` with `TokenSource.NONE`. The status fetcher uses that client's new
`StatusApi`, so no `Authorization` header can slip in. The generated Java client sends no cookies.

`minClient` is compared with the mod version (from the mod container, `ModList`) by numeric dotted parts. A suffix
such as `-alpha` is ignored, and an unparsable value is treated as "no minimum", so a typo on the server can't lock
everyone out.

### D5: Guarding the network paths

Every path that can reach the cloud asks `CloudAvailability` (or `CloudAccount`) first:

- **`Library.refresh()`:** with `mayContact()` false or `tooOld()`, it calls `loadStored()` and requests nothing.
  With `OFF`, `loadStored()` shows no cloud entries.
- **`Library.file()` and `thumbnail()`:** serve the cache. On a miss, they fail fast as unavailable when there is no
  session, `mayContact()` is false or `tooOld()`. `PlacementSession` already treats a failed load as unavailable and
  retries on library changes.
- **`CloudAccount.refreshAccount()`, `signIn()` and the link-code calls** are only reachable from UI that the
  availability hides. `refreshAccount()` also returns early when `mayContact()` is false, as a safety net.
- **`LibraryScreen.init`** calls `availability.libraryOpened()` before `refreshCloud()`. Refresh calls
  `availability.refresh()` too. The Sign in/out and Account buttons, and the account status line, follow
  `showCloud`/`offerSignIn`. With `tooOld()`, a one-line hint replaces Sign in.

### D6: The cloud side, a `Features/Status` slice

- **Contract:** a new tag `Status`, with `GET /v1/status` (`operationId: getStatus`, `security: []`) answering 200 with a
  `ServiceStatus` schema: `signIn` (enum `open`, `closed`, required) and `minClient` (string, optional, pattern
  `^\d+(\.\d+)*$`). It has a `default` Problem response, as the Spectral rules require.
- **`StatusOptions`** binds the section `Status`: `SignIn` (`open`|`closed`, default `closed`) and `MinClient`
  (optional).
- **`StatusApi : IStatusController`** returns the options.
- **`StatusServices`:**
  - `AddStatus` registers the options.
  - `ConfigureStatusEndpoint` adds `AllowAnonymous` for the controller. There's no rate limit: the answer is static and
    cheap.
- **Wiring:** `ContractServices` registers the controller, and `MapContract` adds the endpoint hook.
- **AppHost:** sets `Status__SignIn=open` on the cloud resource.
- **Tests:** `CloudFactory` keeps the default (`closed`) unless a test overrides it.

### D7: `cloud.access` in the client config

The setting goes in the existing `cloud` section as `access`, with the enum `CloudAccess { AUTO, ON, OFF }` and the
comment "Heavymatica Cloud: AUTO asks the cloud whether it is open when you open the library, ON always shows it,
OFF never contacts it." The settings screen of `ui-glass-redesign` labels the values Automatic / Always / Never.

## Risks / Trade-offs

- **[Players already signed in to a local test cloud keep `localhost` in their config file.]** NeoForge keeps
  explicit values, so only new config files get the new default. That's fine: no public player has a config yet, and
  testers are told to delete the line.
- **[A stored session hides a closed or outdated state].** A signed-in player on an old mod would keep calling the
  API. → `minClient` covers this: `TOO_OLD` stops all requests and shows the update hint.
- **[`ON` lets anyone sign in while closed].** Accepted for now, as agreed. The cloud doesn't refuse new accounts, and
  a later change can add invites.
- **[The status is cached for the whole game session].** Opening the cloud later doesn't show up until a restart or
  Refresh. → Refresh asks again, and a restart is fine at launch.
- **[`Library.file` now fails without a session where it used to try].** The network call failed anyway without a
  token, so nothing that worked stops working.

## Migration Plan

1. Contract and cloud first. The status endpoint is additive, and old mods never call it.
2. Then the mod. Without a running cloud at `api.heavymatica.com`, released jars show no cloud. That's the intended
   state until launch.
3. Rollback: a mod built without this change goes back to always showing Sign in. Nothing is stored that needs
   migrating.

## Open Questions

- Which `minClient` the cloud should name at launch. It's a deploy-time setting and doesn't change any code.
