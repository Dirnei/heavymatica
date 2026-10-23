# Proposal

## Why

Today the cloud library is only visible from the game, and a cloud account is a Minecraft UUID: the UUID is the
account ID, the owner of every schematic and the subject of every token. Step 5 of `ROADMAP.md` starts with the
website: players register with GitHub, upload schematics and manage them in the browser. That needs an account that
is separate from any one identity, and a web sign-in the cloud handles itself. `web-library` is change 1 of 3 in
step 5. `account-links` (connecting the game) and `admin-web` build on it.

## What Changes

- **Accounts separate from identities.**
  - An account has an opaque ID and holds a list of linked identities: Minecraft identities (UUID verified through
    Mojang, last known name) and web identities (provider and the provider's stable user ID, never the email).
  - Up to 10 identities per account, and an identity belongs to at most one account. In this change every account
    has exactly one identity; adding more comes with `account-links`.
  - Schematics, versions, stored files, thumbnails and quota usage belong to the account ID. Quota stays a
    per-account entitlement.
  - An audit log records account creation, migration and identity changes.
- **Migration (BREAKING for stored data, not for clients).**
  - On first start, every existing UUID-keyed account becomes an account with a new ID and one Minecraft identity.
  - Ownership, stored files, usage, quota and sign-in sessions move to the new ID, under a lock, in steps that
    resume safely after an interruption.
- **Tokens.** Access tokens name the account ID as subject plus the identity that signed in. Tokens issued before the
  migration are rejected once, and the mod's existing refresh-on-401 renews them from the migrated session.
- **Nothing changes for the game.** The mod keeps signing in through Mojang, and its accounts are migrated
  transparently. Until `account-links` exists, a GitHub account and a Minecraft account are separate accounts with
  separate libraries.
- **GitHub sign-in in the cloud.**
  - OAuth2 through the aspnet-contrib GitHub handler, run by the cloud itself. The identity is (`github`, numeric
    user ID); the email is never requested or stored.
  - "Register" creates a new account holding that GitHub identity. "Sign in" signs in to the account linked to it.
  - The cloud handles the redirect and sets a session cookie for the website: same origin, HttpOnly, no tokens in
    the browser, with protection against cross-site requests. Sign-out ends the session.
  - The GitHub client ID and secret are Aspire parameters. Without them, web sign-in is unavailable and the rest of
    the cloud works as before.
- **Cookie access to the existing API.** The account and schematic operations accept the website session as well
  as the mod's bearer token. Two new operations start the GitHub sign-in and sign out of the website.
- **The website (`src/web/player`).**
  - Astro, built statically, with Svelte islands, served by the cloud on the same origin as the API. A typed client
    is generated from `src/api/heavymatica-cloud.v1.yaml`.
  - Pages: landing with register and sign-in, library list with search, schematic details (metadata, dimensions,
    block counts, versions, thumbnail), upload (new schematic or new version), rename, delete schematic or version,
    and storage usage against the quota.
  - Aspire runs the Astro dev server next to the cloud during development.

Out of scope:
- Minecraft linking, link codes and the first-sign-in choice (`account-links`)
- Microsoft and Google sign-in
- share links with previews
- admin features and passkeys (`admin-web`)
- billing

## Capabilities

### New Capabilities
- `cloud/accounts`: the account model and its linked identities, account IDs that are not identities, the identity
  limit, the audit log of account events, and the migration of existing Minecraft-keyed accounts.
- `web/player-ui`: the player website: where it is served, sign-in and register pages, the library list and search,
  schematic details, upload, rename, delete, storage usage and sign-out.

### Modified Capabilities
- `cloud/identity`:
  - "Identity verified through the Mojang session server": the UUID is the Minecraft identity, no longer the account
    key.
  - "Account created on first sign-in": a Minecraft sign-in reaches the account linked to its UUID, or creates one.
  - "Short-lived access tokens": the subject is the account ID and tokens name the identity. A website session is
    accepted wherever an access token is.
  - "Sign-out": also ends a website session.
  - New: "GitHub sign-in on the website", "Website session", "Website requests protected from other sites" and
    "Older clients keep working".
- `cloud/schematic-storage`:
  - "Upload creates a schematic or a new version", "Owner-only access", "Library listing and search" and "Storage
    quota": the owner is the account, and the quota is an entitlement of the account.

## Impact

- **Contract (`src/api/heavymatica-cloud.v1.yaml`):**
  - a `cookieAuth` security scheme next to `bearerAuth` on every authenticated operation
  - new `Web` tag with `startGitHubSignIn` and `signOutWeb`
  - `Account` gains `id` and `provider`; `uuid` becomes optional (present for Minecraft sessions)
  - new problem codes `request_forbidden` and `web_sign_in_unavailable`
  - the server controllers (NSwag) and the Java client are regenerated; the Java generator gets
    `enumUnknownDefaultCase`, so new enum values never break older mods
- **Cloud (`src/cloud/Heavymatica.Cloud`):**
  - `Features/Account`: account and identity documents, the audit log and the migration
  - `Features/Identity`: token claims, session identity, the GitHub handler, the cookie scheme, the request check
    for cookie sessions and Data Protection keys in Mongo
  - new `Features/Web`: the sign-in start and sign-out operations and serving the built website
  - `Features/Schematics`: unchanged queries; owners are still strings, now account IDs
  - new package `AspNet.Security.OAuth.GitHub`
- **AppHost:** `github-client-id` and `github-client-secret` parameters, and the Astro dev server through Aspire's
  JavaScript hosting integration.
- **Website (`src/web/player`, new):** Astro, Svelte, `openapi-typescript` and `openapi-fetch`, Vitest.
- **Data:** new `identities`, `account_audit`, `account_migrations` and `data_protection_keys` collections.
  `accounts`, `schematics`, `versions` and `sessions` are rewritten by the migration. S3 objects are copied to the
  new `accounts/{accountId}/` prefix, and the blob sweeper removes the old copies.
- **Mod:** only the regenerated client and its generator option. No behaviour change.
- **Tests:** xUnit integration tests in `Heavymatica.Cloud.Tests` (accounts, tokens, the migration and its
  rehearsal, GitHub sign-in against a fake GitHub, cookie access and the request check, static serving), Vitest unit
  tests for the website's helpers, and manual browser checks.
