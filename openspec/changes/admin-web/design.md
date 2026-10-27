# Design

## Context

See proposal.md, Why, and `ROADMAP.md` step 5 for the decisions on the admin host, passkeys and hardening. What
exists:
- **Cloud:** one Kestrel listener (`http://localhost:5180` in development). The player website's static files
  (`UseWebsite`), the contract controllers (`MapContract`, NSwag from `src/api/heavymatica-cloud.v1.yaml`), GitHub
  sign-in (`MapWeb`) and DevMojang share it. A fallback authorization policy requires a player principal (`Bearer` or
  `WebSession`) everywhere except anonymous endpoints. `UseWebRequestCheck` enforces same origin and
  `X-Heavymatica-Request: 1` for unsafe cookie requests. Rate limits use ASP.NET Core's limiter (`IdentityServices`).
- **Data:** `accounts` (`usedBytes`, `quotaBytes`), `identities` (`provider`, `subject`, `name`), `account_audit`,
  `schematics`. `Quota.TryReserveAsync` compares usage with `quotaBytes` on every upload.
- **AppHost:** runs the cloud, Mongo, MinIO and the player site's dev server on port 4321, which proxies `/v1`.
- **Players' website:** `src/web/player`, static Astro with Svelte islands and `openapi-fetch`.

## Goals / Non-Goals

**Goals:**
- Admin endpoints are unreachable from the public host by construction, not by a forgotten attribute.
- Nothing an attacker can guess or replay gives admin access: no passwords, single-use hashed codes, short sessions.
- Moving the admin host behind WireGuard later changes only DNS and the proxy or firewall.

**Non-Goals:**
- Roles or per-admin permissions.
- Multi-instance rate limits and session stores beyond Mongo (sessions are already in Mongo).
- Admin actions on schematics.

## Decisions

### D1. Two listeners, split by endpoint metadata

- `Admin:Url` (for example `http://localhost:5181`) adds a second Kestrel endpoint; `Admin:Host` (for example
  `admin.heavymatica.example`, `localhost` in development) names the only accepted host. Both unset: no admin
  listener, no admin endpoints mapped.
- Every admin endpoint is mapped in one `MapGroup("/admin/v1")` with `RequireHost($"{Admin:Host}:{port}")`, plus the
  admin static site with the same host requirement. `RequireHost` matches the local port too, so the public listener
  cannot route there even with a forged Host header.
- A small middleware before routing answers 404 on the admin port for anything that is not an admin endpoint or the
  admin site. The player site, `/v1` and DevMojang therefore never answer there. This is the belt to `RequireHost`'s
  braces: the split does not depend on each player endpoint remembering an attribute.
- Health checks stay on the public listener only.
- **Alternative:** a separate admin process. It would need its own deployment, Mongo access and secrets for a
  handful of pages; one process with two listeners keeps one deployment and still keeps the routes apart.

### D2. Admin contract

- `src/api/heavymatica-admin.v1.yaml`, linted by the same `.spectral.yaml`. NSwag generates its controllers into
  `Heavymatica.Cloud.Contract.Admin`; the admin site generates its types from it. It is not embedded into
  `/openapi/v1.yaml` and not served at all.
- Operations (tag in brackets, all under `/admin/v1`):
  - [Session] `GET /session` (the signed-in admin, `passkeyCount`, `needsSecondPasskey`), `POST /sign-in/options`,
    `POST /sign-in`, `POST /sign-out`
  - [Registration] `POST /registration/options` (`{ setupCode | invitationToken, displayName? }`),
    `POST /registration` (the attestation), `POST /passkeys/options` and `POST /passkeys` for an additional passkey of
    the signed-in admin
  - [Admins] `GET /admins`, `DELETE /admins/{adminId}`, `DELETE /admins/{adminId}/passkeys/{passkeyId}`,
    `POST /invitations` (`{ kind: admin | recovery, adminId? }`), `GET /invitations`, `DELETE /invitations/{id}`
  - [Accounts] `GET /accounts?q=`, `GET /accounts/{accountId}`, `PUT /accounts/{accountId}/quota`
    (`{ quotaBytes, reason }`)
  - [Audit] `GET /audit?cursor=&limit=`
- Problem codes of its own: `setup_code_invalid`, `invitation_invalid`, `second_passkey_required`,
  `passkey_required` (would leave fewer than two), `last_admin`, `cannot_remove_self`, `passkey_failed`, plus the
  shared `unauthenticated`, `request_forbidden`, `not_found`, `invalid_request`, `rate_limited`.

### D3. Passkeys on ASP.NET Core Identity

- .NET 10's Identity passkey support does the WebAuthn work: `SignInManager.MakePasskeyCreationOptionsAsync`,
  `PerformPasskeyAttestationAsync`, `MakePasskeyRequestOptionsAsync` and `PasskeySignInAsync`, with
  `IdentityPasskeyOptions.ServerDomain = Admin:Host`.
- `AddIdentityCore<AdminUser>()` with a small Mongo store implementing `IUserStore` and `IUserPasskeyStore` over
  `admins` and `admin_passkeys`. No password, email, phone or lockout stores: Identity then has no way to sign in
  without a passkey.
- The creation and request options are kept between the two calls in Identity's short-lived passkey state cookie,
  set on the admin host.
- **Registration with a setup code or invitation:** the options call checks the code and holds its hash in the
  ceremony state; the second call checks it again and consumes it with a conditional update in the same step that
  stores the passkey. A code is spent only when a passkey was really registered.
- **Alternative:** Fido2NetLib (MIT). Equally capable, but another dependency where the framework now covers it. It
  is the fallback if the Identity API cannot work over a custom store.

### D4. Admin session

- Cookie scheme `AdminSession`: `__Host-heavymatica-admin` outside development (`heavymatica-admin` in it), HttpOnly,
  Secure outside development, `SameSite=Strict`, no `Domain`, so it is bound to the admin host.
- 30 minutes sliding, 8 hours absolute (`issued` claim checked on every request). Each sign-in starts a session in an
  `admin_sessions` collection; the cookie check rejects an ended session, which makes removing an admin end their
  sessions at once.
- The authorization policy of the admin group accepts only `AdminSession`. The player fallback policy never accepts
  it. Requests with a player cookie or bearer token on the admin API are unauthenticated.
- `UseWebRequestCheck` applies to `AdminSession` as to `WebSession`.
- `second_passkey_required`: an endpoint filter on the group answers 403 for every operation except `GET /session`,
  `POST /passkeys/options`, `POST /passkeys` and `POST /sign-out` while the admin has fewer than two passkeys.

### D5. Setup codes and invitations

- **Setup code:** `Heavymatica.Cloud admin setup-code` runs the cloud executable with a verb instead of the web host:
  it connects to Mongo with the same configuration, writes `admin_setup_codes` (one document, the hash of a 12-symbol
  code from the link-code alphabet, `expiresAt = now + 15 min`, replacing any open code) and prints the code and the
  admin address. Optional `--admin <adminId>` makes it a recovery code for that admin.
- **Invitations:** `admin_invitations { _id, tokenHash, kind (admin | recovery), adminId?, createdBy, createdAt,
  expiresAt (24 h), usedAt?, revokedAt? }`, TTL 30 days after `expiresAt`. The token is 32 random bytes, base64url, in
  the link `https://{Admin:Host}/invite/#token`. The fragment keeps it out of server logs and `Referer` headers.
- Codes and tokens are stored as SHA-256 hashes and compared after hashing. Unknown, expired, revoked and used ones
  answer the same `setup_code_invalid` or `invitation_invalid`.

### D6. Rate limits

Fixed windows partitioned by client IP: `admin-sign-in` 10 per minute on both sign-in calls, `admin-code` 5 per
10 minutes on both registration calls. Behind a proxy the client IP comes from the forwarded headers configured for
the deployment, the same as the player rate limits.

### D7. Admin audit

- `admin_audit { _id, at, adminId?, ip, kind, target?, details }`, TTL index `expireAt = at + 400 days`.
- Kinds: `admin_signed_in`, `admin_sign_in_failed`, `setup_code_failed`, `invitation_failed`, `admin_created`,
  `admin_removed`, `passkey_added`, `passkey_removed`, `invitation_created`, `invitation_revoked`, `quota_changed`
  (`{ accountId, oldBytes, newBytes, reason }`).
- `GET /audit` pages newest first with a cursor on `(at, _id)`.

### D8. Accounts for admins

- **Search:** `q` that is 32 hex characters looks up an account ID; a UUID looks up the Minecraft subject; otherwise
  a case-insensitive exact match on identity `name` across providers (an index on a lower-cased `nameLower` field,
  added and backfilled by this change), at most 50 accounts.
- **Overview:** the account document, its identities, `countDocuments` on `schematics`, and the 20 newest
  `account_audit` entries. No schematic content.
- **Quota:** `PUT .../quota` sets `quotaBytes` (0 to 100 GiB, reason 3 to 200 characters) with one update and writes
  `quota_changed`. Usage is left alone; `TryReserveAsync` already rejects uploads above the quota.

### D9. Admin site

- `src/web/admin`: Astro static with Svelte islands and `openapi-fetch`, styled like the player site but with its
  own layout and an "Admin" banner. Pages: `/` (sign-in or setup code), `/invite/`, `/accounts/` (search),
  `/account/?id=`, `/admins/`, `/audit/`, `/passkeys/` (add a passkey; the only page while one is missing).
- WebAuthn in the browser through `navigator.credentials.create` and `get` with the JSON options from the API
  (`PublicKeyCredential.parseCreationOptionsFromJSON` and `toJSON`, with a small fallback for browsers without them).
- The cloud serves the built site on the admin listener with the same static file rules as the player site
  (`WebsiteFiles`, pointed at the admin build).
- **AppHost:** `Admin__Url=http://localhost:5181`, `Admin__Host=localhost`, and the admin dev server on port 4322
  proxying `/admin/v1` to the admin endpoint. Passkeys for `localhost` work in browsers without TLS.

### D10. Tests

- A test-only software authenticator creates ES256 credentials with `none` attestation and signs assertions, so the
  integration tests run real registration and sign-in ceremonies through the API.
- Listener separation is tested on a `WebApplicationFactory` with both endpoints on Kestrel.

## Risks / Trade-offs

- [A route added later lands on the wrong listener] → The admin-port middleware (D1) rejects anything outside the
  admin group, and a test enumerates the endpoint data source to assert every `/admin/v1` endpoint requires the admin
  host and every other endpoint does not.
- [.NET 10 Identity passkey API does not fit a custom store] → Fido2NetLib fallback (D3); the contract and specs stay
  the same.
- [Setup code printed to a shared terminal or log] → 15 minutes, single use, and the audit log records its use; the
  command prints it to standard output only.
- [The admin host is public until WireGuard] → Passkeys only, rate limits, short sessions, host-bound cookie and a
  full audit log. Moving behind WireGuard keeps the host name, so passkeys stay valid.
- [Losing every admin's passkeys] → The server CLI recovery code (D5).

## Migration Plan

1. Deploy with `Admin:Url` and `Admin:Host` set and DNS for the admin host pointing at the proxy; certificates by
   HTTP-01 while public.
2. Run `admin setup-code` on the server, register the first admin and their second passkey.
3. Later, behind WireGuard: point DNS at the VPN address, restrict the proxy or firewall, and switch certificates to
   DNS-01. No code changes.

**Rollback:** unset `Admin:Url`; the admin listener closes and the collections stay unused.
