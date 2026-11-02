# Design

## Context

For motivation see proposal.md, Why. For behaviour see the deltas for `cloud/accounts`, `cloud/identity`,
`cloud/schematic-storage`, `client/cloud-account` and `web/player-ui`. The decisions on accounts, links and sign-in
are recorded in `ROADMAP.md` step 5.

**Depends on `web-library`.** This design builds on what it adds, taken from its design (names may still move while
it is implemented):
- **Accounts:** `accounts` with 32-hex IDs, `identities` with the unique `(provider, subject)` index, and
  `Accounts.FindByIdentityAsync`, `CreateWithIdentityAsync` and `AddIdentityAsync` (limit 10, duplicate key →
  `identity_already_linked`). `account_audit` with `account_created`, `account_migrated`, `identity_added` and
  `identity_removed`.
- **Principals:** game sessions (access JWT with `sub` = accountId, `iid`, `idp`, `name`, `sid`, plus refresh token)
  and website sessions (`WebSession` cookie with the same claims, checked against `sessions` on every request).
  Refresh and the cookie check already reject a session whose identity is gone or points to another account.
- **GitHub sign-in:** `startGitHubSignIn` → GitHub → callback → `GET /v1/web/sign-in/complete`, which maps the
  external principal; unknown + `login` ends at `/?error=no_account`. A `FakeGitHub` test helper drives the flow.
- **Request check:** cookie requests with unsafe methods need the same origin and `X-Heavymatica-Request: 1`.
- **Website:** `src/web/player`, static Astro with Svelte islands and the `openapi-fetch` client; the AppHost runs its
  dev server on `http://localhost:4321` with `/v1` proxied to the cloud.

What exists in the mod:
- `CloudAccount` runs challenge → Mojang join → verify, and stores `StoredSession(uuid, name, …)` per Minecraft UUID.
- `CloudClient` refreshes once on any 401 and retries. `CloudException` ignores problem bodies it cannot parse.
- `LibraryScreen` has search, refresh, sign in/out and the import buttons in its top bar. `DevScreens` runs scripted
  steps.

## Goals / Non-Goals

**Goals:**
- Link codes that stay safe when the code is visible on a stream or a screenshot.
- One link flow for both directions and both places a pending sign-in can start (game and website).
- Older mods keep working without an update.

**Non-Goals:**
- Anything in the account foundation, the migration or GitHub sign-in itself (`web-library`).
- Merging two accounts that both have data.
- Multi-instance coordination. Rate limits are in memory, per instance; the issue limit is counted from the audit
  log.

## Decisions

### D1. Dependency and archive order

- Every capability touched exists after `web-library`. Requirements that `web-library` adds or rewrites are changed
  here as MODIFIED, with their full text as it reads after both changes, so archiving `web-library` first and then
  this change gives the right result.
- Archive order: `web-library` → `account-links`. The in-flight client changes (`material-swaps`,
  `ghost-opacity-wheel`, `block-equivalents`, `ghost-targeting`) touch none of these capabilities and can be archived
  in any order relative to them.
- `client/cloud-account` is modified only here; `web-library` leaves the mod's behaviour unchanged.

### D2. Data model additions

```
link_codes      { _id: codeId, codeHash, issuer, status, createdAt, expiresAt, redeemer?, redeemedAt?,
                  confirmBy?, resolvedAt? }
                  unique codeHash among open codes (partial index); TTL on expiresAt + 1 day
account_audit   + accountId optional (pending actors), codeId?, ip?, expireAt?  TTL on expireAt
```

- Unlinking deletes the identity document, so an unlinked identity is simply unknown again.
- **Account display** in prompts: an account is described by its Minecraft names and its web sign-ins (provider and
  login), never an email.

### D3. Pending principals

| Principal | Transport | Content |
|---|---|---|
| Pending game sign-in | pending JWT (bearer): audience `heavymatica-pending`, 10 min | `idp`, `isub` (provider subject), `name` |
| Pending web sign-in | `WebPending` cookie, 10 min, HttpOnly, same flags as `WebSession` | the same claims, in a Data Protection ticket |

- The pending JWT is signed with the same ES256 key and a different audience, so the default bearer scheme never
  accepts it. A second JWT bearer scheme, `pending`, validates it.
- The pending operations use a policy that accepts `Bearer`, `pending`, `WebSession` or `WebPending`. Every other
  endpoint keeps `web-library`'s fallback policy (`Bearer` or `WebSession`), so pending principals never reach data.
- Both are stateless. "Create account" is idempotent through the unique identity index: a second create for the same
  identity finds it linked and returns a session for that account. A replay therefore cannot create two accounts.
- The request check of `web-library` applies to `WebPending` as well.

### D4. Sign-in API for the game

| Operation | Auth | Result |
|---|---|---|
| `POST /v1/auth/sign-in` (`signIn`) `{challengeId, name}` | none, `sign-in` rate limit | `SignInResult { session?, pending? }` |
| `POST /v1/auth/verify` (`verify`), **deprecated** | none, `sign-in` rate limit | `Session`; unknown identity → new account |
| `POST /v1/auth/pending/session` (`completeSignIn`) `{createAccount}` | pending | `Session`; 409 `account_choice_required` when not linked and `createAccount` is false |

- `signIn` returns `pending` only when the identity is unknown and linking is available (D8). Otherwise it creates the
  account at once and returns `session`, exactly like `verify`.
- `PendingSignIn` is `{ pendingToken, expiresAt, identity { provider, name? }, joinAvailable }`.
- A new endpoint instead of a flag on `verify`: the response shape differs (OpenAPI Generator picks one 2xx type per
  operation), and older mods keep calling `verify` with the old behaviour.
- `completeSignIn` covers both ends of the choice: "Create" sends `createAccount: true`; after a confirmed join the
  client calls it with `false` and receives the session of the joined account, with no second Mojang handshake.

### D5. First sign-in on the website

- `complete` (from `web-library`) changes one branch: unknown identity + `login` + linking available signs in to
  `WebPending` and redirects to `/?choice=1` (keeping `returnTo`). With linking off it still ends at
  `/?error=no_account`. `register` is unchanged and still creates at once.
- New operations, tag `Web`, auth `WebPending`:
  - `POST /v1/web/pending/account` (`createWebAccount`): creates the account (idempotent as in D3), swaps
    `WebPending` for `WebSession`, 200 with `Account`.
  - `POST /v1/web/pending/session` (`completeWebSignIn`): after a confirmed join, swaps `WebPending` for a
    `WebSession` of the joined account; 409 `account_choice_required` while the identity has no account.
  - `GET /v1/web/pending` (`getWebPending`): `{ identity { provider, name }, expiresAt, joinAvailable }` for the
    choice page.
- The landing page shows the choice when `getWebPending` answers: "Create a new account" or "Join an account from the
  game", the latter going to `/link/`. Joining from the website uses use (a): the game of the account to join shows
  a code, and the pending GitHub sign-in redeems it.

### D6. Link codes

**API** (new tag `Links`, controller `ILinksController`):

| Operation | Auth | Notes |
|---|---|---|
| `POST /v1/link-codes` (`createLinkCode`) | account or pending | 201 `LinkCode`; cancels the issuer's open code; 503 `linking_unavailable` |
| `GET /v1/link-codes/{codeId}` (`getLinkCode`) | issuer or redeemer | `LinkCode` with status, redeemer, deadlines |
| `POST /v1/link-codes/{codeId}/confirm` (`confirmLinkCode`) | issuer (same identity) | `LinkCode` with status `linked` |
| `DELETE /v1/link-codes/{codeId}` (`cancelLinkCode`) | issuer | cancel while open, decline while redeemed |
| `POST /v1/link-codes/redeem` (`redeemLinkCode`) `{code}` | account or pending, `link-redeem` rate limit | `LinkCode` with status `redeemed` |

- "Account or pending" means any of the four schemes of D3. In practice the game issues and confirms (bearer or
  pending bearer) and the website redeems (`WebSession` or `WebPending`); the API does not tie an operation to a
  transport, so tests and the dev redeemer can use bearer tokens throughout.
- **`LinkCode`** is `{ id, code?, linkUrl?, status, expiresAt, confirmBy?, issuer: LinkParty, redeemer?: LinkParty }`.
  - `status` is `open`, `redeemed`, `linked`, `declined`, `cancelled` or `expired`.
  - `code` and `linkUrl` are returned to the issuer only, and only while the code is open.
  - `LinkParty` is `{ provider?, name?, accountNames?, accountProviders? }`. It names an identity (provider and name)
    or an account (its Minecraft names and its web providers with logins), and never an email.
- **Codes:**
  - 8 characters from `23456789ABCDEFGHJKMNPQRSTVWXYZ` (30 symbols: no 0/O, 1/I/L or U), shown as `XXXX-XXXX`.
  - That is about 6.6 × 10¹¹ codes. With a 5-minute window and 30 attempts per IP per 10 minutes, guessing is
    hopeless.
  - Input is normalised by upper-casing and dropping spaces and dashes. Only the SHA-256 of the normalised code is
    stored. On a hash collision with an open code, issuing retries.
- **Link address:** `{Accounts:LinkBaseUrl}/link/?code=K7QM-3XRP`. The code in the URL can end up in browser history.
  That is acceptable because it is single-use, expires in 5 minutes and needs the issuer's confirmation.
- **Issuer binding:**
  - `issuer` stores the kind (`account` or `pending`) plus `accountId` and `identityId`, or `provider` and `subject`
    for a pending issuer.
  - Confirm and cancel must come from a principal with the same account and identity, or the same pending provider
    and subject (a fresh pending token after a new sign-in is fine).
  - Another device of the same account signed in with a different identity cannot confirm. Neither can a stolen
    session of another identity.
- **Redemption:**
  - The redeemer kind must complement the issuer: an account code needs a pending redeemer, and a pending code needs
    an account redeemer.
  - The redemption is one conditional `FindOneAndUpdate` (`status: open`, `expiresAt > now` → `redeemed`, with the
    redeemer and `confirmBy = now + 5 min`), so exactly one redeemer wins.
  - A wrong kind answers 409 `link_code_not_applicable` and leaves the code open.
  - Unknown, expired, cancelled and used codes all answer 400 `link_code_invalid`.
- **Confirmation:**
  - Conditional update `redeemed` → `linked` while `confirmBy > now`, then `Accounts.AddIdentityAsync`.
  - If that fails on the unique index (the identity was linked elsewhere in between), the code becomes `declined` and
    the answer is 409 `identity_already_linked`. The limit answers 409 `too_many_identities`.
  - A code whose `confirmBy` has passed reads as `expired`.
- **Why the issuer confirms:**
  - The code is shown in the game, and Minecraft is streamed a lot. Without confirmation, a viewer could redeem a
    visible code. In use (a) they would gain the account. In use (b) they would capture someone's Minecraft identity.
  - With confirmation, the player sees "Link the GitHub sign-in alex-builds?" or "Join the account of Alex, Alex2?"
    and declines.
  - Alternative: bind the code to the redeemer's IP or require a second factor. Both are weaker or heavier than one
    click in a screen the player already has open. See Open Questions.
- **Rate limits:**
  - `link-redeem` chains two ASP.NET Core fixed-window partitions: per principal (accountId, or provider and subject)
    with 10 per 10 minutes, and per IP with 30 per 10 minutes. It runs before the handler, so no code is checked once
    the limit is hit.
  - Issuing is limited to 10 per issuer per hour, counted from the audit log. That keeps it correct across restarts.
- **Audit:** new kinds `link_code_issued`, `link_code_redeemed`, `link_code_redeem_failed`, `link_code_confirmed` and
  `link_code_declined`, plus `identity_added` from `AddIdentityAsync`. Code entries carry the client IP and
  `expireAt = at + 90 days`. A pending actor has no account ID yet, so `accountId` is optional on code entries.

### D7. Unlinking

`DELETE /v1/me/identities/{identityId}` (`unlinkIdentity`, tag `Account`):
- 409 `last_identity` when it is the only one; 404 when the identity belongs to another account.
- Otherwise it deletes the identity, revokes its game and web sessions, and `Accounts` writes `identity_removed`.

Unlinking the identity of the calling session is allowed. Its next refresh or cookie check then fails, and the
client shows the player as signed out. `GET /v1/me` gains `identities[]` (`id`, `provider`, `name`, `current`) and
`linkingAvailable`.

### D8. Linking on and off

- Linking is on when `Accounts:LinkBaseUrl` is set. The AppHost sets it in development to the website's dev origin
  (`http://localhost:4321`). Production sets it to the public website origin when this change ships.
- With linking off:
  - `signIn` never returns `pending`, and a GitHub `login` with an unknown identity ends at `?error=no_account`.
  - `linkingAvailable` is false, so the mod shows no link button, and `createLinkCode` answers 503.
- The switch exists so the cloud can be deployed before the mod with the new screens, and so tests can cover both
  paths.

### D9. Link code page on the website

- `/link/` reads `?code=` and fills the input. Without a `WebSession` or `WebPending` it shows the GitHub sign-in
  buttons with `returnTo=/link/?code=…`, so the code survives the round trip through GitHub.
- On submit it calls `redeemLinkCode`, then polls `getLinkCode` every 2 seconds while the status is `redeemed`, and
  shows what will be linked from `issuer` ("Your Minecraft account Steve will join this account" or "This GitHub
  sign-in will join the account of Alex, Alex2").
- On `linked`: a pending redeemer calls `completeWebSignIn` and goes to `/library/`; an account redeemer shows
  "Linked" with a link to the library. On `declined` or `expired` it says so and offers another code.
- Problem codes map to messages through the website's shared function (`link_code_invalid`,
  `link_code_not_applicable`, `rate_limited`, `identity_already_linked`, `too_many_identities`).

### D10. Mod flow

- **`CloudAccount`:**
  - Sign-in calls `signIn` instead of `verify`.
  - A `pending` answer moves it to a new `CHOOSING` status. The pending token lives in memory only and is never
    written to disk.
  - `createAccount()` and `completeJoin()` call `completeSignIn`.
  - Link code calls go through the authenticated client (account) or through a client whose `TokenSource` returns the
    pending token and never refreshes.
  - `StoredSession` stays keyed by Minecraft UUID: `Account.uuid` is still the Minecraft UUID for Minecraft sessions.
- **Screens (`ui`):**
  - `AccountChoiceScreen` (Create, Join when `joinAvailable`, Cancel) opens when sign-in reaches `CHOOSING`.
  - `AccountScreen` opens from a new "Account" button in the library top bar. It shows the identities, the one in
    use, the usage, "Link another sign-in" (when `linkingAvailable`), and Unlink per row with a confirmation.
  - `LinkCodeScreen` serves both issuers. It shows the code in large text, the QR code, the countdown and a status
    line. It polls `getLinkCode` every 2 seconds while open. On `redeemed` it shows the redeemer and Confirm and
    Decline. On `expired` it offers "New code".
  - Closing the screen cancels the open code. A pending join that is confirmed calls `completeJoin` and returns to
    the library signed in.
- **No input widget** for codes exists anywhere in the mod.

### D11. QR encoder

The mod uses **Nayuki's QR Code generator for Java** (`io.nayuki:qrcodegen:1.8.0`, Maven Central, MIT, about 30 KB).
It has no dependencies, is a JPMS module and is well tested across languages. It goes into the jar with `jarJar`,
like Jackson.
- Encoding: `QrCode.encodeText(url, Ecc.MEDIUM)`. The link address is about 50 characters, so version 3 or 4 (29 to
  33 modules).
- Rendering: `QrView` turns the module matrix into horizontal runs and draws them with `GuiGraphics.fill`, dark on
  white with a 4-module quiet zone. The module size is a whole number of GUI pixels, so the code stays sharp at every
  GUI scale. The matrix is built once per code.
- Alternatives:
  - ZXing core (Apache-2.0, about 550 KB, mostly decoding the mod doesn't need).
  - A hand-written encoder: Reed-Solomon, masking and version tables are easy to get subtly wrong, and phones then
    fail to read some codes.
  - If `jarJar` of a modular jar causes trouble at runtime, the fallback is to vendor Nayuki's six MIT source files
    under `io.github.dirnei.mc.heavymatica.qr`, with the licence header kept.

### D12. Contract compatibility

- Additive only for older mods: new optional fields on `Account`, new operations and problem codes, `verify`
  deprecated but unchanged.
- `web-library` already set `enumUnknownDefaultCase: true` in the Java generator, so `LinkCode.status` and the new
  problem codes map to an unknown value in later mods instead of failing to deserialize.
- New problem codes: `account_choice_required`, `linking_unavailable`, `link_code_invalid`,
  `link_code_not_applicable`, `identity_already_linked`, `too_many_identities` and `last_identity`.
- New security schemes: `pendingAuth` (bearer) and `pendingCookieAuth` (cookie `heavymatica-pending`).

## Risks / Trade-offs

- [A viewer redeems a code shown on stream] → Issuer confirmation (D6), 5-minute expiry, and audit entries with IP.
- [In-memory rate limits reset on restart and don't span instances] → Acceptable for one instance. The issue limit is
  counted from the audit log. `admin-web` can move limits to Mongo when there are more instances.
- [The pending JWT and pending cookie are stateless and cannot be revoked] → They live 10 minutes, grant no data, and
  creating an account with them is idempotent.
- [A player registered on the website first and now cannot join their Minecraft account to it] → Use (b) still works:
  the game's first sign-in shows a code and the website account redeems it. Only joining the GitHub identity into an
  existing Minecraft account is blocked, because it already has an account. See Open Questions.
- [`jarJar` of `qrcodegen` fails in production jars] → Checked in the built jar and in a run (task 5.3). The fallback
  is vendored sources (D11).
- [Unlinking your current sign-in by mistake] → The confirmation names it, and the player is only signed out. They
  can sign in again and choose to join.

## Migration Plan

1. `web-library` is deployed and its migration has run.
2. Deploy the new cloud with `Accounts:LinkBaseUrl` unset: nothing changes for anyone.
3. Ship the new mod. Its choice, account and link screens appear only once linking is on.
4. Set `Accounts:LinkBaseUrl` to the public website origin, which turns on pending sign-ins, link codes and the
   website's choice.

**Rollback:** unset `Accounts:LinkBaseUrl`. Links already made stay; unknown identities are created at once again.

## Open Questions

These are the player-facing questions still open. The specs and tasks follow the current plan described for each,
and change if the answer differs.

- **Issuer confirmation.** The plan has the issuer confirm every link in the game after seeing the redeemer. Open:
  is the extra step worth it in both directions, and is one Confirm click with the redeemer's name enough, or should
  the game show more (for example the redeemer's GitHub login and avatar, or the time of redemption)?
- **In-game account screen.** The plan has an account screen in the library listing linked sign-ins with unlink. Open:
  does the game need it, or should listing and unlinking live only on the website (a website account page), with the
  game showing just the link code screen?
- **Empty new account joining another.** "Join existing" only works for an identity with no account, and
  `web-library`'s "Register" creates an account at once. Open: may an account that is still empty (no schematics,
  never used) join another account, deleting the empty one, so a player who registered on the website first can
  still join their Minecraft account?
- The exact production host for `Accounts:LinkBaseUrl` comes with the first public deployment.
