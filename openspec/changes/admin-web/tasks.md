# Tasks

Cloud tests run with `dotnet test --project src/cloud/Heavymatica.Cloud.Tests`; while a dev AppHost is running, add
`-p:OutDir=<repo>/src/cloud/Heavymatica.Cloud.Tests/bin/alt/`. AppHost tests need
`--Heavymatica:EphemeralStorage=true`. Tasks marked **(manual check)** run in a browser against the dev AppHost.

## 1. Contract

- [ ] 1.1 Write `src/api/heavymatica-admin.v1.yaml` with the operations, schemas, the `adminCookieAuth` scheme and the problem codes of D2; lint it with the existing Spectral ruleset (extend the `lint` script to both files); verify `npm run lint` in `src/api` passes
- [ ] 1.2 Generate its controllers with NSwag into `Heavymatica.Cloud.Contract.Admin` (a second `GenerateContract` invocation, not embedded as a resource) and add stub controllers; verify `dotnet build src/cloud` passes and `ContractTests` still serves only the player contract

## 2. Admin listener

- [ ] 2.1 Add `AdminOptions` (`Url`, `Host`), the second Kestrel endpoint, the `/admin/v1` group with `RequireHost`, and the admin-port middleware that answers 404 outside the admin group and site (D1); verify tests: an admin path on the public listener is 404, `/v1/schematics` and the player site on the admin listener are 404, a wrong Host on the admin listener is 404, nothing admin is mapped without `Admin:Url`, and an endpoint-enumeration test that every `/admin/v1` endpoint requires the admin host and no other endpoint does

## 3. Admins, passkeys and sessions

- [ ] 3.1 Add `AdminUser`, the Mongo store (`IUserStore`, `IUserPasskeyStore`) over `admins` and `admin_passkeys` with their indexes, and `AddIdentityCore` with `IdentityPasskeyOptions.ServerDomain` from `Admin:Host` (D3); verify unit tests of the store and a test-only software authenticator (ES256, `none` attestation) that registers and signs in through `SignInManager`
- [ ] 3.2 Add the `AdminSession` cookie scheme, `admin_sessions`, the 30-minute sliding and 8-hour absolute limits, the admin-only policy on the group, and the request check for `AdminSession` (D4); verify tests with a `FakeTimeProvider` for idle and absolute expiry, a player cookie and a player bearer token answering 401 on `/admin/v1/session`, an admin cookie answering 401 on `/v1/me`, and an unsafe request without the header answering 403
- [ ] 3.3 Implement the sign-in and sign-out operations and `GET /session` (D2, D4); verify an integration test that signs in with the software authenticator, reads the session, signs out, and that a passkey of another RP ID fails with `passkey_failed`
- [ ] 3.4 Add the `second_passkey_required` filter and the additional-passkey operations (D4); verify an admin with one passkey gets 403 on `GET /accounts` and can add a second, after which the call succeeds

## 4. Setup codes, invitations, management

- [ ] 4.1 Add the `admin setup-code [--admin <id>]` CLI verb and `admin_setup_codes` (D5); verify tests that the verb prints a code, that a second run invalidates the first, and that the registration operations create the first admin with a valid code, reject an expired or used code with `setup_code_invalid`, and with `--admin` add a passkey to that admin
- [ ] 4.2 Add invitations (create, list, revoke, register from the token for `admin` and `recovery`) (D5); verify tests for use, reuse, expiry after 24 hours, revoke, and a recovery invitation adding a passkey to the named admin
- [ ] 4.3 Add admin management: list admins with passkeys, remove an admin (ending their sessions; `last_admin`, `cannot_remove_self`), remove a passkey (`passkey_required` below two); verify tests for each rule and that a removed admin's cookie answers 401 on its next request
- [ ] 4.4 Add the `admin-sign-in` and `admin-code` rate limits (D6); verify the 11th sign-in attempt in a minute and the 6th code attempt in 10 minutes answer 429 `rate_limited` without checking the code
- [ ] 4.5 Add `admin_audit` with the TTL index and entries for every kind of D7, and `GET /audit` with cursor paging; verify a test that a registration, a sign-in, an invitation, a failed setup code and a quota change each write an entry with time, admin, IP and details, read back newest first across two pages

## 5. Accounts

- [ ] 5.1 Add `nameLower` to identities (set on sign-in and rename, backfilled at startup) with its index, and `GET /accounts?q=` for account ID, UUID and case-insensitive names, at most 50 (D8); verify tests for each kind of query and an empty result
- [ ] 5.2 Add `GET /accounts/{id}` (identities, usage, quota, schematic count, 20 newest account audit entries, no schematic content) and `PUT /accounts/{id}/quota` with its validation and audit entry (D8); verify tests for the "Account overview" and "Changing the quota" scenarios, including an upload rejected as over quota after lowering it below usage

## 6. Admin site

- [ ] 6.1 Scaffold `src/web/admin` (Astro static, Svelte, `openapi-fetch`, Vitest, `npm run check`) with generated types from the admin contract and its own layout (D9); verify `npm run check` and `npm test` pass
- [ ] 6.2 Add sign-in, setup code registration, the invitation page and the add-passkey page with WebAuthn JSON helpers (D9); verify Vitest tests for the options conversion and the problem-code messages, and **(manual check)** registering the first admin from a CLI setup code, adding a second passkey, signing out and in again, and cancelling the browser prompt
- [ ] 6.3 Add the account search, account page with the quota form (MiB / GiB input), admins page (invite, recovery invite, revoke, remove admin, remove passkey) and audit page with paging; verify Vitest tests for the quota unit conversion and **(manual check)** each "in the browser" scenario
- [ ] 6.4 Serve the admin build from the cloud on the admin listener (D9) and add the AppHost settings and the admin dev server on port 4322 proxying `/admin/v1`; document `Admin:*` and the setup command in the cloud README section; verify `AppHostTests` pass with `--Heavymatica:EphemeralStorage=true`, and that under the AppHost `http://localhost:5181/` serves the admin site while `http://localhost:5180/admin/v1/session` is 404

## 7. Integration

- [ ] 7.1 **(manual check)** End to end on the dev AppHost: setup code, first admin with two passkeys, invite a second admin in another browser profile, look up a player account by Minecraft name, raise its quota, see the change in the audit log, and remove the second admin
- [ ] 7.2 Update `ROADMAP.md` step 5 to mark `admin-web` done and step 5 complete; verify the file reads consistently

## Workflow follow-up

- Commit the implementation once all tasks are done (for example `feat(admin): add the admin host with passkey admins`).
- Archive the change with `/opsx:archive`.
