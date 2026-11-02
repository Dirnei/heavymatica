## 1. Contract and cloud status endpoint

- [x] 1.1 Add the `Status` tag, `GET /v1/status` (`operationId: getStatus`, `security: []`, 200 with a `ServiceStatus` schema of `signIn` enum `open`/`closed` required and an optional `minClient` with pattern `^\d+(\.\d+)*$`, and a `default` Problem response) to `src/api/heavymatica-cloud.v1.yaml`. Verify that `npx @stoplight/spectral-cli lint src/api/heavymatica-cloud.v1.yaml` passes and that `dotnet build src/cloud/Heavymatica.slnx` generates `IStatusController`.
- [x] 1.2 Add `src/cloud/Heavymatica.Cloud/Features/Status/` (D6):
  - `StatusOptions`, bound to section `Status`, with `SignIn` defaulting to `closed` and an optional `MinClient`
  - `StatusApi : IStatusController`
  - `StatusServices`, with `AddStatus` and an endpoint hook that adds `AllowAnonymous`

  Wire them in `Program.cs` and `Infrastructure/Contract/ContractServices.cs`. Verify with tests in `src/cloud/Heavymatica.Cloud.Tests/Features/Status/`: an anonymous request gets 200 and `closed`; configured `open` and `MinClient` `0.3.0` show up; `minClient` is absent when it is not set; a request with a bearer token gets the same body; and while `closed`, a dev sign-in still succeeds.
- [x] 1.3 Set `Status__SignIn=open` on the cloud resource in `src/cloud/Heavymatica.Cloud.AppHost/AppHost.cs`. Verify after `aspire run` that `http://localhost:5180/v1/status` answers `{"signIn":"open"}`.

## 2. Client address and setting

- [x] 2.1 In `HeavymaticaConfig`: change the `cloud.cloudUrl` default to `https://api.heavymatica.com`, make `cloudUrl()` prefer the system property `heavymatica.cloud.url` when set (D2), and add `cloud.access` with the `CloudAccess { AUTO, ON, OFF }` enum and its comment (D7). Verify with a unit test for the property-over-config rule, kept free of the game by moving the choice into a small static helper, and in `runClient` that a fresh `heavymatica-client.toml` holds the production address and `access = "AUTO"`.
- [x] 2.2 In `src/mod/build.gradle` `runs.client`, set `systemProperty 'heavymatica.cloud.url'` from `HEAVYMATICA_CLOUD_URL`, falling back to `http://localhost:5180`. Verify in a `runClient` log line, or a dev screen, that the client uses `http://localhost:5180` with a fresh config, and the given address with `HEAVYMATICA_CLOUD_URL=http://localhost:6000`.
- [x] 2.3 Update README "Local development" for the dev address override and `HEAVYMATICA_CLOUD_URL`, and note `cloud.access = "ON"` for testing against a closed cloud. Verify that the steps, followed on a fresh clone, reach the local cloud.

## 3. Availability decision

- [x] 3.1 Add `account/CloudAvailability` (D3, D4): the answer states, keeping answers for the game session, retrying after `UNREACHABLE`, a 5 s timeout, the `minClient` comparison, and `showCloud` / `offerSignIn` / `mayContact` / `tooOld` with listeners. Verify with `CloudAvailabilityTest` on a stub fetcher. It covers every row of the "Who sees the cloud" and "Too old for the cloud" scenarios, plus:
  - `ON` never fetches, and `OFF` never fetches and hides the cloud even with a session
  - a second `libraryOpened()` after an answer doesn't fetch, while `refresh()` does
  - a failure is fetched again on the next `libraryOpened()`
  - `0.2.0` is older than `0.3.0`, `0.3.0-alpha` equals `0.3.0`, and a garbage `minClient` means no minimum
- [x] 3.2 Add the generated `StatusApi` to `CloudClient.Apis`. Expose a status fetcher on `CloudAccount` that uses the token-less `auth` client, and build `CloudAvailability` in `HeavymaticaClient.setup` with the mod version from the mod container. Verify with a `CloudClientTest` / `StubCloud` case that the status request has no `Authorization` header, and that nothing calls the fetcher during setup: the stub records no request after `restore()` and `loadStored()`.

## 4. Guarding every cloud path

- [x] 4.1 `Library.refresh()`: with `mayContact()` false or `tooOld()`, show the stored listing (none with `OFF`) and request nothing. `Library.file()` and `thumbnail()`: serve the cache, and fail fast as unavailable on a miss without a session, with `OFF`, or with `tooOld()`. `CloudAccount.refreshAccount()`: return early when `mayContact()` is false. Verify with library tests on `StubCloud`: no request reaches the stub for an uncached `file()` while signed out or with `OFF`; a cached file still loads with `OFF`; and `refresh()` with `OFF` makes no request.
- [x] 4.2 In the current library screen (`ui/LibraryScreen`, or its successor if `ui-glass-redesign` has landed):
  - call `availability.libraryOpened()` on open, and `availability.refresh()` on Refresh
  - show Sign in/out, Account and the account status line only per `showCloud`/`offerSignIn`
  - show the update hint when `tooOld()`
  - re-layout when the availability changes

  Add the hint text to `en_us.json`. Verify in `runClient`:
  - with no cloud running and a fresh session dir, the library shows local entries only and no Sign in or error
  - with `aspire run`, Sign in appears after the answer
  - with `cloud.access = "OFF"`, there are no cloud widgets
  - with a stored session and `Status__SignIn=closed`, the cloud listing still shows
- [x] 4.3 Check that no other screen or input triggers cloud traffic: the materials screen, the wheel, capture and paste. Verify with a `runClient` session against `aspire run`: play, open the wheel and materials, place and capture without opening the library. The cloud's request log shows no request from that client.

## 5. Docs

- [x] 5.1 Update ROADMAP.md "Open before a public release": the domain and default address are settled (`api.heavymatica.com`), and refusing sign-ups while closed is still open. Verify that the item reads correctly and nothing else in the file changed.

## Workflow follow-up

- Change `release-pipeline` uses this behaviour for the CurseForge/Modrinth network disclosure.
- Archive after the user has tried the library against a closed and an open local cloud.
