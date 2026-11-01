# Proposal

## Why

`web-library` gives players a website with GitHub sign-in and splits accounts from identities, but a GitHub account
and a Minecraft account stay separate, each with its own library. Players want the game and the website to show the
same library, and to keep alts on one account. That needs a safe way to join identities to an account, started from
the game, where the Minecraft identity is proven. `account-links` is change 4 of 5 in step 5 of `ROADMAP.md`.

**Builds on `web-library`** (archived): this change modifies the `cloud/accounts`,
`cloud/identity`, `cloud/schematic-storage` and `web/player-ui` requirements that `web-library` adds or rewrites.

## What Changes

- **First sign-in with an unknown identity.**
  - In the game: a new sign-in call can answer with a *pending sign-in* instead of a session. It is a short-lived
    proof of the identity, which lets the player create an account or join one.
  - On the website: "Sign in with GitHub" with an unknown GitHub identity becomes a pending sign-in too, and asks
    the same question instead of only offering to register.
  - The current `verify` call stays, deprecated, and keeps creating accounts, so older mods keep working.
  - While linking is not configured, unknown identities behave as in `web-library`.
- **Link codes.**
  - The game issues them: a signed-in account (to add another sign-in) or a pending Minecraft sign-in (to join an
    account).
  - The website redeems them on a new page, opened by the QR code: 8 characters, single use, 5 minutes, with a QR
    code of `https://<web>/link/?code=...`.
  - Nothing is linked until the issuer confirms in the game, after seeing who redeemed. This protects players who
    show the code on stream.
  - Includes the rules for who may redeem, the binding to the issuing identity and account, rate limits and audit
    entries for every code event.
- **Linking and unlinking.** A confirmed code links the identity. Any identity except the last can be unlinked, and
  its sessions end at once.
- **Cloud API** for all of the above.
- **Website:** the link code page (`/link/`) and the first-sign-in choice on the landing page.
- **Mod:**
  - the first-sign-in choice (create or join)
  - an account screen opened from the library, listing linked sign-ins with unlink
  - the link code screen with a QR code, a countdown and a confirmation, using a small bundled QR encoder
  - DevScreens hooks for in-game checks, including a dev-only redeemer that stands in for the website

Out of scope:
- the account model, its migration, tokens naming the account, GitHub sign-in and the website itself (`web-library`)
- Microsoft and Google sign-in, share links
- admin features and passkeys (`admin-web`)
- merging two accounts that both have data
- billing

## Capabilities

### New Capabilities
None. Every capability this change touches exists after `web-library`.

### Modified Capabilities
- `cloud/accounts` (added by `web-library`):
  - "Accounts hold linked identities", "Account IDs are not identities" and "Account audit log": scenarios for
    linked alts and GitHub sign-ins, and audit entries for code events.
  - New: "Linking available only when configured", "Link codes issued by the game", "Link codes are short-lived and
    single-use", "Who may redeem a link code", "The issuer confirms every link", "Link code rate limits" and
    "Unlinking identities".
- `cloud/identity`:
  - "Account created on first sign-in" (as rewritten by `web-library`): unknown identities get a pending sign-in when
    linking is available.
  - "Short-lived access tokens" (as rewritten by `web-library`): pending sign-ins may use some calls.
  - "GitHub sign-in on the website" and "Older clients keep working" (added by `web-library`): the pending path on
    the website, and `verify` keeps creating accounts.
  - New: "Pending sign-in".
- `cloud/schematic-storage` (as rewritten by `web-library`):
  - "Upload creates a schematic or a new version", "Owner-only access" and "Storage quota": scenarios for linked
    alts, unlinked identities and a quota shared by all identities.
- `client/cloud-account`:
  - "Sign in with the current Minecraft account": a known account signs in without questions.
  - New: "First sign-in choice", "Joining an existing account from the game", "Link code from the library", "Link
    codes are never entered in the game", "Linking needs confirmation in the game" and "Linked sign-ins shown and
    unlinked".
- `web/player-ui` (added by `web-library`):
  - "Landing page with GitHub register and sign-in": the first-sign-in choice for an unknown GitHub identity.
  - New: "Link code page".

## Impact

- **Contract (`src/api/heavymatica-cloud.v1.yaml`):**
  - new `Links` tag, `signIn`, `completeSignIn`, `unlinkIdentity`, a `pendingAuth` bearer scheme and a
    `pendingCookieAuth` scheme
  - `Account` gains `identities` and `linkingAvailable`
  - new problem codes, and `verify` deprecated
  - the server controllers (NSwag) and the Java client are regenerated
- **Cloud (`src/cloud/Heavymatica.Cloud`):**
  - `Features/Account`: unlinking and the identity list in `/v1/me`
  - new `Features/Links`: link codes, redemption, confirmation and their audit entries
  - `Features/Identity`: the sign-in call, pending tokens and the pending website cookie
  - the AppHost sets the link address to the website's dev origin
- **Data:** new `link_codes` collection; `account_audit` entries gain the client IP and an expiry for code events.
- **Website (`src/web/player`):** the `/link/` page and the choice on the landing page.
- **Mod (`src/mod/heavymatica`):**
  - the `account` package (sign-in flow, pending sign-in, link codes)
  - the `ui` package: choice, account and link code screens, a library top-bar button and DevScreens steps
  - language keys
  - new jar-in-jar dependency `io.nayuki:qrcodegen` (MIT)
- **Tests:**
  - xUnit integration tests in `Heavymatica.Cloud.Tests` for pending sign-ins, links, unlinking and audit, with the
    `FakeGitHub` helper from `web-library` for web identities
  - mod unit tests for the QR matrix and code formatting, and website Vitest tests
  - in-game checks through DevScreens and manual browser checks
