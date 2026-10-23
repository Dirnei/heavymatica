# Design

## Context

For motivation see proposal.md, Why. For behaviour see `specs/cloud/accounts`, `specs/web/player-ui` and the deltas
for `cloud/identity` and `cloud/schematic-storage`. The decisions on accounts, sign-in and the stack are recorded in
`ROADMAP.md` step 5.

What exists today:
- **Accounts:** `AccountDocument` (`accounts` collection) is keyed by the dashed Minecraft UUID and holds `name`,
  `usedBytes`, `quotaBytes` and `createdAt`. `Accounts.SignedInAsync` upserts it on every verified sign-in.
  `Accounts.ToContract` parses the ID back into `Account.uuid`.
- **Ownership:** `SchematicDocument.Owner` and `VersionDocument.Owner` hold the account ID string. The blob keys are
  `accounts/{owner}/blobs/{sha256}` and `accounts/{owner}/thumbnails/{schematicId}/{n}.png`. `BlobSweeper` deletes
  blobs that no version references once they are older than `OrphanAge` (1 h). `Quota` increments
  `accounts.usedBytes` conditionally. Downloads go through 5-minute pre-signed S3 links (`createLinks`).
- **Tokens:**
  - ES256 JWTs with `sub` = account ID (the UUID), `name` and `sid`.
  - `SessionDocument` holds `AccountId` and the hash of the current refresh token (`{sessionId}.{secret}`).
  - `AuthApi` is anonymous, and challenge and verify carry the `sign-in` rate limit (20 per IP per minute).
  - The fallback authorization policy requires an authenticated user of the JWT bearer scheme.
- **Contract:** contract-first OpenAPI with one tag per controller. NSwag generates `I<Tag>Controller` and the
  slices implement it. Spectral rules require a single tag, a default Problem response and `/v1/` paths. The Java
  client is generated with OpenAPI Generator (`native`).
- **Uploads** take the raw file as `application/octet-stream` with the name in the query, at most 16 MiB, streamed.
  An identical re-upload returns the existing latest version.
- **AppHost:** MongoDB, MinIO and the cloud project on `http://localhost:5180`, with explicit environment variables
  instead of Aspire client integrations.
- **No production data yet:** the cloud has never been deployed publicly. The migration still has to be correct,
  because the developer's local volumes and any early test deployment hold real data.

## Goals / Non-Goals

**Goals:**
- One account model that `account-links` (linking) and `admin-web` (account lookup, quota) can extend without
  reshaping it.
- A migration that keeps every library, file and signed-in mod session, and is safe to re-run.
- A website session that never exposes a token to page scripts and cannot be driven by another site.
- Older mods keep working without an update.

**Non-Goals:**
- Pending sign-ins, link codes and unlinking (`account-links`). The model leaves room for them, nothing more.
- Admin principals, passkeys and the admin host (`admin-web`).
- Multi-instance coordination beyond the migration lock. Rate limits stay in memory, per instance.
- Server-side rendering. The website is static files plus API calls.

## Decisions

### D1. Capability split

- `cloud/accounts` is a new capability for the account model, identities, the limit, the audit log and the
  migration. `account-links` adds links, link codes and unlinking to it, and `admin-web` adds quota changes.
- `cloud/identity` keeps proving an identity and issuing sessions: the Mojang handshake and tokens as today, plus
  GitHub sign-in and the website session.
- `web/player-ui` is new and covers only what the player sees in the browser. A new `web/` group sits next to
  `client/` and `cloud/`; the admin UI later becomes `web/admin-ui`.

Alternative: everything in `cloud/identity`. That would mix who is signing in with what an account owns, and every
later change to either would touch one large spec.

### D2. Data model

```
accounts            { _id: accountId, usedBytes, quotaBytes, createdAt }
identities          { _id: identityId, accountId, provider, subject, name?, linkedAt, lastSignInAt }
                      unique (provider, subject); index accountId
account_audit       { _id, accountId, at, kind, actor: { provider, identityId } }  index (accountId, at)
account_migrations  { _id: legacyUuid | "lock", accountId?, step?, startedAt, finishedAt?, leaseUntil? }
sessions            + identityId, kind (game | web), lastUsedAt
data_protection_keys { _id, xml, createdAt }
```

- **Account IDs** are 32 lower-case hex characters from `RandomNumberGenerator`, like the session IDs.
  - They can never be mistaken for a dashed UUID, so legacy documents are easy to detect.
  - Code that still treats an account ID as a UUID fails visibly.
  - Alternative: keep the UUID as the migrated accounts' ID. No data would move, but the account ID would stay
    equal to a Minecraft UUID that `account-links` may later unlink. That is the confusion "Account IDs are not
    identities" rules out.
- **Identities:**
  - `provider` is `minecraft` or `github` (later `microsoft`, `google`). It is a plain string in the contract, so a
    new provider never needs a contract change.
  - `subject` is the dashed UUID, or the GitHub numeric user ID as a decimal string. The GitHub login can change and
    an email can move between accounts; the numeric ID never does.
  - `name` holds the last known Minecraft name or GitHub login, updated on every sign-in, for display only.
  - No email or other profile data is stored, and the GitHub handler requests no scopes, so GitHub does not even
    send a private email.
  - The unique `(provider, subject)` index makes "at most one account" atomic. Two concurrent registrations of the
    same GitHub user cannot both create an account: the second insert fails, and the handler signs in to the first.
- **The 10-identity limit** is enforced in `Accounts.AddIdentityAsync` with a count before the insert. Nothing in this
  change calls it with a second identity; it exists so `account-links` only adds callers. A race could reach 11,
  which is harmless: it is a sanity bound, not a security boundary.
- **Account display:** the account has no name of its own. `Account.name` in the contract stays the name of the
  identity in use.
- **Audit kinds** in this change: `account_created`, `account_migrated`, `identity_added`, `identity_removed`. The
  last two are written by the identity methods, so `account-links` gets them for free. There is no API to read the
  log; `admin-web` adds one.

### D3. Principals, tokens and sessions

| Principal | Transport | Claims |
|---|---|---|
| Game session | access JWT (bearer) + refresh token | `sub` = accountId, `iid` = identityId, `idp` = provider, `name`, `sid` |
| Website session | cookie, server-side session | the same claims, in a Data Protection ticket |

- **Access tokens** gain `iid` and `idp`; `sub` becomes the account ID.
- **Tokens from before the migration** have no `iid`. `OnTokenValidated` rejects them, so the API answers 401
  `unauthenticated`. The mod's `CloudClient` already refreshes once on 401. The refresh token's session was
  rewritten by the migration, so it rotates normally and returns a token with the new `sub`. The cost is one extra
  round trip per signed-in client within 15 minutes of the migration.
  - Alternative: map a legacy `sub` through the Minecraft identity while validating. Rejected: it keeps a
    UUID-as-account path alive for no lasting benefit.
- **Refresh** checks that the session's identity still exists and still points to the session's account, else it
  revokes the session and answers `refresh_token_invalid`. Nothing removes identities in this change; the check is
  there so `account-links`' unlinking ends sessions without touching refresh again.
- **Website sessions** reuse the `sessions` collection with `kind: web`, no refresh hash, and `lastUsedAt`.
  - The cookie holds an encrypted ASP.NET Core authentication ticket with the claims above.
  - `OnValidatePrincipal` loads the session on every request: revoked, older than 30 days since `lastUsedAt`, or
    whose identity no longer points to the account rejects the request and deletes the cookie. `lastUsedAt` is
    written at most once an hour, and the cookie is renewed with it (sliding 30 days).
  - Alternative: a stateless cookie. It could not be ended by sign-out on another device or by unlinking, and the
    per-request lookup is one indexed read.
- **One authorization path for both:** the fallback policy lists both schemes (`Bearer`, `WebSession`), so every
  existing operation accepts either. `IdentityServices.AccountId(user)` reads `sub` from whichever principal
  authenticated. The cookie scheme's login redirect and access-denied handlers answer 401 or 403 Problem JSON
  instead of redirecting.

### D4. GitHub sign-in

- **Handler:** `AspNet.Security.OAuth.GitHub` (aspnet-contrib). Its sign-in scheme is a short-lived external cookie
  (`WebExternal`, 5 minutes), not the website session. After the callback, our own `complete` endpoint maps the
  external principal to an account, signs in to `WebSession` and deletes the external cookie.
  - Scopes: none. `NameIdentifier` is the numeric user ID; `urn:github:login` is the login for display.
  - `SaveTokens` is off: GitHub's access token is never stored. The cloud only needs the user ID once.
- **Endpoints:**
  | Operation | Auth | Result |
  |---|---|---|
  | `GET /v1/web/sign-in/github?intent=register\|login&returnTo=` (`startGitHubSignIn`) | none, `sign-in` rate limit | 302 to GitHub; 503 `web_sign_in_unavailable` without credentials |
  | `GET /v1/web/sign-in/github/callback` | handler (state + correlation cookie) | handled by the middleware, not in the contract |
  | `GET /v1/web/sign-in/complete` | `WebExternal` cookie | maps the identity, signs in, 302 to `returnTo` or the landing page with `?error=` |
  | `POST /v1/web/sign-out` (`signOutWeb`) | website session | 204, session revoked, cookie deleted |
- **Intent** travels in `AuthenticationProperties.Items`, protected by the OAuth state. `returnTo` must be a local
  path starting with a single `/`; anything else becomes `/library/`, so the flow is no open redirect.
- **Mapping in `complete`:**
  - known identity → session for its account, `name` updated
  - unknown and `register` → new account and identity, audit `account_created`, session
  - unknown and `login` → no account, redirect to `/?error=no_account`
  - a duplicate-key error on the identity insert (concurrent register) → sign in to the account that won
- **Errors** reach the landing page as `?error=` codes: `cancelled` (GitHub's `access_denied`), `no_account`,
  `github_unavailable` (backchannel failure) and `sign_in_failed` (state or correlation failure). The handler's
  `OnRemoteFailure` maps them.
- **Credentials** come from `GitHub:ClientId` and `GitHub:ClientSecret`. The AppHost passes the Aspire parameters
  `github-client-id` and `github-client-secret` (secret). When either is empty the handler is not registered, and
  `startGitHubSignIn` answers 503, so tests and contributors without a GitHub app still run the cloud.
- **Tests** use a fake GitHub: the handler's `BackchannelHttpHandler` is a stub that answers the token and user
  endpoints with a chosen user ID and login. A test client without auto-redirect follows the 302 to read `state`,
  calls the callback with `code` and `state` and the correlation cookie, then follows `complete`. No real GitHub is
  needed, and `access_denied` and backchannel failures are easy to simulate.
  - Alternative: a `DevGitHub` slice like `DevMojang` for local runs. Not needed: the local setup uses a real OAuth
    app (D9), and the tests cover the flow.

### D5. Protecting cookie requests

- The session cookie is HttpOnly, `SameSite=Lax`, path `/`. Outside development it is `Secure` and named
  `__Host-heavymatica`, which pins it to the exact host; in development (plain http) it is `heavymatica`.
- `SameSite=Lax` is needed because the GitHub callback is a top-level navigation from github.com; `Strict` would drop
  the external cookie on the way back.
- A middleware after authentication checks every request authenticated by `WebSession` with an unsafe method
  (anything but GET, HEAD, OPTIONS):
  - `Origin` must equal the request's own origin (`Sec-Fetch-Site: same-origin` is accepted when `Origin` is
    missing), and
  - the header `X-Heavymatica-Request: 1` must be present.
  - Otherwise 403 `request_forbidden`, before any handler runs.
- A cross-site page cannot add a custom header without a CORS preflight, and the cloud sends no CORS headers at all,
  so the header alone already stops forged requests; the origin check is a second guard against misconfigured
  proxies. Bearer requests skip the check: they carry no ambient credential.
- Alternative: ASP.NET Core antiforgery tokens (double-submit cookie). It needs a token endpoint and a readable
  cookie, and gives nothing over a custom header on an API that never answers CORS.
- **Data Protection keys** are stored in `data_protection_keys` through a small `IXmlRepository`, so cookies and
  OAuth state survive restarts and work across instances. Anyone who can read the database can forge a cookie;
  they could also read every library, so this adds no new exposure.

### D6. Contract changes

- `securitySchemes.cookieAuth` (`apiKey`, in `cookie`, name `heavymatica`). Top-level `security` becomes
  `[{bearerAuth: []}, {cookieAuth: []}]`: either is enough. The cookie's production name differs (`__Host-`); the
  contract documents that in the scheme's description.
- New tag `Web` with `startGitHubSignIn` (`security: []`, 302 and 503 responses) and `signOutWeb` (204).
- `Account`: new required `id` and `provider`; `uuid` becomes optional and means the Minecraft UUID of the identity in
  use. Older mods read only `uuid`, `name`, `usedBytes` and `quotaBytes`, which keep their meaning for Minecraft
  sessions.
- Problem codes: `request_forbidden`, `web_sign_in_unavailable`.
- The Java generator gets `enumUnknownDefaultCase: true`, so enums added later (problem codes) map to an unknown
  value instead of failing to deserialize. `CloudException` already tolerates unknown codes in this and older mods.
- No schematic operation changes. The website uses `listSchematics`, `getSchematic`, `uploadSchematic`,
  `uploadVersion`, `organizeSchematic` (rename), `deleteSchematic`, `deleteVersion`, `createLinks` (thumbnail and
  download) and `getMe` (usage), all with the cookie.

### D7. The website

- **Project:** `src/web/player`, Astro with `output: 'static'` and `build.format: 'directory'`, Svelte islands
  (`@astrojs/svelte`), TypeScript strict. `src/web/admin` follows in `admin-web`.
- **Pages:**
  | Path | Content |
  |---|---|
  | `/` | landing: register, sign in, `?error=` messages, a note that the game's library is not connected yet |
  | `/library/` | list with search box (debounced `q`), "Load more" (`nextCursor`), usage bar, upload button |
  | `/schematic/?id=` | details, version picker, block counts, thumbnail, download, rename, delete, upload version |
  | `/upload/` and `/upload/?schematic=` | file picker and drop zone, progress, result or reason |
- A static build cannot enumerate schematic IDs, so details use a query parameter instead of a path segment.
- **API client:** `openapi-typescript` generates `src/api/schema.d.ts` from `../../api/heavymatica-cloud.v1.yaml` in a
  `generate` script that `dev`, `build` and `check` run first; the output is git-ignored. `openapi-fetch` is the
  runtime client. One middleware adds `X-Heavymatica-Request: 1` and sends the page to `/?returnTo=` on 401.
- **Upload** uses `XMLHttpRequest` for progress, with the raw file as the body and the file name without extension
  as `name`. The page rejects files over 16 MiB before sending; the server stays authoritative for format, size and
  quota, and its problem codes are mapped to messages by one shared function. An identical re-upload is detected by
  the returned version number equalling the previous latest.
- **Thumbnails and downloads** come from `createLinks`. The pre-signed URLs point at the S3 endpoint, which an `<img>`
  and a download link may load cross-origin without CORS.
- **Styling:** plain CSS with a few custom properties, light and dark. No component library.
- **Tests:** Vitest for the game-free helpers (byte formatting, problem code messages, block count ordering,
  `returnTo` handling), and `astro check` for types against the generated client. Pages are checked by hand.

### D8. Serving the build

- The cloud serves the built files with `UseDefaultFiles` and `UseStaticFiles` from `Web:Root` (default `wwwroot`
  next to the executable), before authentication, so the static shells need no session. `/v1`, `/health` and `/dev`
  are never served from files. Unknown paths answer the site's `404.html`.
- The cloud's publish target runs `npm ci` and `npm run build` in `src/web/player` and copies `dist/` into `wwwroot`.
  A normal `dotnet build` does not touch npm, so cloud tests and the mod build stay Node-free.
- Pages are cached for a short time; Astro's hashed assets under `/_astro/` are immutable.

### D9. Development setup

- The AppHost adds the website with Aspire's JavaScript hosting integration (`Aspire.Hosting.JavaScript`,
  `AddJavaScriptApp` running `npm run dev` in `src/web/player`, fixed port 4321), with `WaitFor(cloud)` and the cloud's
  endpoint in `HEAVYMATICA_CLOUD_URL`.
- Astro's dev server proxies `/v1` to that URL with the original `Host` header kept, so the browser sees one origin
  (`http://localhost:4321`) for pages and API, exactly as in production. The cloud builds the OAuth redirect URI from
  that host.
- **Local GitHub OAuth app** (documented in the cloud README):
  1. GitHub → Settings → Developer settings → OAuth Apps → New OAuth App.
  2. Homepage URL `http://localhost:4321`, authorization callback URL
     `http://localhost:4321/v1/web/sign-in/github/callback`.
  3. Generate a client secret.
  4. Set the AppHost parameters: `dotnet user-secrets set Parameters:github-client-id <id>` and
     `Parameters:github-client-secret <secret>` in `Heavymatica.Cloud.AppHost`, or enter them in the dashboard.
  - GitHub matches the callback host and port exactly, so the app works only through the dev server on 4321. A
    production-like run on 5180 needs a second OAuth app.
- AppHost tests set the parameters to dummy values and do not start the website resource
  (`Heavymatica:WithWebsite=false`), so they need no Node.

### D10. Migration

`AccountMigration` is an `IHostedService` registered before the others. In `WebApplication`, hosted services start
before Kestrel accepts requests, so no request sees half-migrated data. Each step is idempotent and keyed by the
legacy UUID, and `account_migrations.step` records progress.

1. Take the lock: upsert `account_migrations {_id: "lock"}` with `leaseUntil = now + 2 min` only when free or
   expired, and renew it while working. A second instance waits until the lock is released, then finds nothing left.
2. Find legacy accounts: `accounts` documents whose `_id` parses as a dashed UUID.
3. Upsert `account_migrations {_id: uuid}` with a fresh account ID set only on insert. A rerun reuses the ID.
4. Upsert the new `accounts` document (usage, quota, `createdAt` copied) and the Minecraft identity
   (`subject` = uuid, `name` = legacy name).
5. Copy every S3 object under `accounts/{uuid}/` to `accounts/{accountId}/` (server-side `CopyObject`; existing
   targets are skipped).
6. `UpdateMany` `schematics.owner` and `versions.owner` from the uuid to the account ID.
7. `UpdateMany` `sessions.accountId` to the account ID, set `identityId` and `kind: game`.
8. Delete the legacy account document, write `account_migrated` to the audit log and mark the step done.
9. Release the lock.

Notes:
- **Old objects:** after step 6 no version references `accounts/{uuid}/…`, so `BlobSweeper` removes those objects on
  its next sweep. A download link issued in the 5 minutes before the restart can fail if a sweep runs that soon;
  the mod's download then fails retryably and asks for a new link.
- **Concurrent uploads** cannot happen: the migration runs before the server listens.
- **Empty database:** step 2 finds nothing and the service starts at once.

Alternatives:
- A lazy, per-request migration on sign-in. It needs dual-read code everywhere, forever.
- An offline CLI. Easy to forget on deploy, and a deployed service would serve broken data in between.

## Risks / Trade-offs

- [The migration delays startup on a large database] → There is no production data yet. The steps are bulk
  `UpdateMany` and server-side copies per account; the rehearsal test measures it on the development data.
- [S3 copy succeeds, owner rewrite fails] → Steps resume from `account_migrations.step`; copies are skipped when
  present, and the old objects stay until no version references them.
- [A player registers with GitHub, then expects their game library on the website] → The landing page and the empty
  library say the game is not connected yet; `account-links` connects them.
- [Players create a GitHub account first and cannot later join their Minecraft account to it] → Whether an empty
  account may join another is an open question of `account-links`; nothing here blocks either answer.
- [A per-request session lookup adds latency to every website call] → One indexed read by `_id`; `lastUsedAt` is
  written at most hourly.
- [A misconfigured proxy rewrites `Origin` or drops the custom header] → Requests fail closed with 403, which shows
  up at once in the manual checks.
- [Pre-signed thumbnail URLs point at the S3 host, which must be reachable from browsers] → True for the dev MinIO on
  localhost and for a public bucket endpoint in production; a proxy endpoint can come later with share links.
- [GitHub is down] → Website sign-in fails with `github_unavailable`; existing website sessions and the game keep
  working.

## Migration Plan

1. Back up the database and bucket, then deploy the new cloud. The migration runs on start, before the API listens.
2. Mods in the field keep working: the first call after the deploy gets 401, they refresh and continue.
3. Create the production GitHub OAuth app for the public host and set its credentials; until then the website shows
   that sign-in is unavailable.

**Rollback:**
- Restore the database and bucket backup and deploy the previous build.
- The migration is forward-only. The previous build cannot read 32-hex account IDs, so a rollback without the backup
  loses access to migrated libraries.

## Open Questions

- The production host name and its GitHub OAuth app come with the first public deployment (see "Open before a
  public release" in `ROADMAP.md`).
- How long audit entries are kept after an account is deleted. Account deletion doesn't exist yet.
