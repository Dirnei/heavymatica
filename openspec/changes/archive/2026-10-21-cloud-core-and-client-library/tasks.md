# Tasks

## 1. Repository scaffolding

- [x] 1.1 Run `git init`, add a root `.gitignore` covering Gradle, .NET, IDE, generated sources and run directories, and a short root `README.md` describing the `src/` layout (D1); verify `git status` shows only intended files
- [x] 1.2 Create `src/cloud/` with `Heavymatica.slnx`, `Directory.Build.props`, `Directory.Packages.props` (Central Package Management) and the three projects `Heavymatica.Cloud`, `Heavymatica.Cloud.Tests` and `Heavymatica.Cloud.AppHost`, with the `Features/`, `Formats/` and `Infrastructure/` folders (D1); verify `dotnet build` and `dotnet test` succeed from `src/cloud/`
- [x] 1.3 Wire the AppHost to start MongoDB, MinIO and the service in Development, with service defaults (health, OpenTelemetry) as an `Infrastructure` extension method (D13); verify `aspire run` shows all resources healthy and the service answers its health check
- [x] 1.4 Create `src/mod/` with ModDevGradle, NeoForge 26.1.2 and Java 25 toolchain, and the `heavymatica` subproject with mod id `heavymatica`, feature packages, and a `neoforge.mods.toml` that accepts servers without the mod (D1); verify `./gradlew build` succeeds and `runClient` reaches the title screen with the mod listed

## 2. API contract and code generation

- [x] 2.1 Write `src/api/heavymatica-cloud.v1.yaml` (OpenAPI 3.0.3) covering every operation in D8 with `operationId`s, tags, schemas, bearer security, octet-stream and PNG bodies, problem-details errors and the rejection reason-code enum (D2, D6); verify every D8 row has a matching operation
- [x] 2.2 Add a Spectral ruleset requiring `operationId`, a tag and problem-details errors on every operation, and run it from both builds; verify the contract lints clean and a deliberately broken copy fails
- [x] 2.3 Configure NSwag.MSBuild in `Heavymatica.Cloud` to generate DTOs and interface-style controllers into `obj/` per tag (D2); verify a test fails when any generated `I<Tag>Controller` (`IAuthController`, `IAccountController`, `ISchematicsController`) has no implementation, and the stubs return 501
- [x] 2.4 Make the upload operations stream: no body buffering on those routes, and the raw request stream handed to the slice; verify with a test over real Kestrel that the slice starts reading a 16 MiB upload while the client is still sending it (a buffered body would deadlock the test)
- [x] 2.5 Serve the source YAML at `/openapi/v1.yaml`; verify a test that the served bytes equal the source file
- [x] 2.6 Configure the OpenAPI Generator Gradle plugin (`java`, `library=native`) to generate the client into `build/generated`, and bundle Jackson with jarJar (D2); verify `./gradlew build` compiles the generated client and the built jar contains the bundled Jackson
- [x] 2.7 Add the `account/` client wrapper (bearer token, single refresh on 401, results on the render thread) around the generated client (D4); verify a unit test against a stub HTTP server, including the 401 refresh path

## 3. Conformance set

- [ ] 3.1 Collect real sample files into `src/conformance/files/`: `.litematic` from several Litematica versions (single and multi-region, negative region size), Sponge v2 and v3, vanilla structure `.nbt` with one and several palettes, files with modded blocks, entities, and no data version; verify each opens in its origin tool
- [x] 3.2 Craft invalid fixtures: unsupported format, truncated NBT, palette index out of range, size mismatch, oversized dimension, oversized volume, file over 16 MiB, decompression bomb; verify each file is present with a note on what it violates
- [x] 3.3 Write `src/conformance/expected/<file>.json` for every fixture per D7, plus `src/conformance/README.md` describing the format and the `paletteDigest` definition; verify every fixture has exactly one expected file

## 4. Cloud formats (`Heavymatica.Cloud.Formats`)

- [x] 4.1 Implement the in-house NBT reader over a counting, size-limited stream with gzip support (D6); verify unit tests for every tag type, truncated input and the 128 MiB cap
- [x] 4.2 Implement the canonical model, canonical block-state strings, block counts (air and structure void excluded, grouped by block ID) and `paletteDigest`; verify unit tests including the stairs-facing scenario
- [x] 4.3 Implement format detection and the vanilla structure reader; verify the structure fixtures pass
- [x] 4.4 Implement the Sponge v2 and v3 reader; verify the Sponge fixtures pass
- [x] 4.5 Implement the Litematica reader with bit-array spanning, negative region sizes and later-region-wins merging; verify the Litematica fixtures pass
- [x] 4.6 Add a test that iterates over the whole conformance directory, including every invalid fixture with its reason code; verify it passes in `dotnet test`

## 5. Cloud identity (`Features/Identity`)

- [x] 5.1 Add Mongo mapping and indexes for accounts, challenges (TTL) and sessions in `Infrastructure/Mongo` (D9); verify an integration test creates the indexes against the Aspire Mongo
- [x] 5.2 Implement the `DevMojang` slice, mapped only in Development (D3); verify a test that it is mapped in Development and returns 404 in Production
- [x] 5.3 Implement the Challenge and Verify slices behind `IAuthController` with atomic single use, 60-second expiry and the Mojang `hasJoined` call through a configurable base URL (D3); verify integration tests for success, expired, replayed, not joined and Mojang unavailable
- [x] 5.4 Create or update the account on verify, keyed by Mojang's UUID and refreshing the name; verify tests for first sign-in and rename
- [x] 5.5 Issue ES256 access JWTs (15 minutes) and require them on all non-auth operations; verify tests for missing, expired and tampered tokens
- [x] 5.6 Implement the Refresh and SignOut slices with family revocation on reuse and 30-day idle expiry; verify tests for rotation, reuse revoking the family, and refresh after sign-out
- [x] 5.7 Add per-IP rate limits on challenge and verify; verify a test shows the limit returns 429

## 6. Cloud schematic storage (`Features/Schematics`, `Features/Account`)

- [x] 6.1 Add Mongo mapping and indexes for schematics and versions (D9), and the S3 client configured from Aspire with key layout `accounts/<uuid>/blobs/<sha256>` in `Infrastructure/S3`; verify an integration test round-trips an object through MinIO
- [x] 6.2 Implement the `Upload` slice: the Akka.Streams pipeline (limit, hash, spool, read, metadata, quota reservation, blob put, version insert, quota release on failure) for new schematics and new versions (D5); verify tests for new schematic, new version numbering, invalid file leaving nothing visible, and over quota
- [x] 6.3 Return the existing latest version for an identical re-upload; verify a test shows no version number is used up
- [x] 6.4 Implement the `Listing` and `Details` slices with cursor paging, case-insensitive name search, and folder and tag filters; verify tests for combined filters, ordering and paging
- [x] 6.5 Enforce owner-only access with an identical 404 for foreign and missing IDs on every schematic operation; verify a test that compares the two responses byte for byte
- [x] 6.6 Implement the `Organize` slice for name, folder and tags without creating versions; verify tests
- [x] 6.7 Implement the `Links` slice with pre-signed 5-minute links; verify tests that downloaded bytes hash to the reported SHA-256, for an in-time fetch and an expired link
- [x] 6.8 Implement the `Thumbnails` slice with PNG header and IHDR checks (256 x 256 px, 256 KiB, once per version), counted toward quota; verify tests for accepted, non-PNG, oversized and second attempt
- [x] 6.9 Implement the `Delete` slice for a schematic and a single version (refusing the last one), with quota released immediately; verify tests for both scenarios in the spec
- [x] 6.10 Implement the `Sweeper` slice as a scheduled Akka.NET job removing orphan and unreferenced blobs; verify a test with a fake clock removes an orphan older than one hour and keeps referenced blobs
- [x] 6.11 Implement the `Me` slice with name, usage and quota (default 100 MiB, configurable); verify a test

## 7. Mod formats (`formats` package)

- [x] 7.1 Implement the canonical model, block counts and `paletteDigest`, independent of world and registry state; verify JUnit tests mirror the .NET model tests
- [x] 7.2 Implement limited gzip and NBT reading (own reader with the cloud's limits, see D6), format detection, and the structure, Sponge and Litematica readers (D6); verify JUnit tests per format
- [x] 7.3 Add a JUnit test that iterates over `src/conformance/`; verify it passes in `./gradlew test` with the same results as 4.6

## 8. Mod cloud account (`account` package)

- [x] 8.1 Add the client config (`cloudUrl`, library key, cache size); verify a unit test that no request is made before first sign-in
- [x] 8.2 Implement sign-in through the game's session service `joinServer` and the generated Auth client, with per-UUID session storage (D4); verify against the AppHost that sign-in succeeds and that captured cloud requests never contain the Minecraft token
- [x] 8.3 Implement silent refresh, the session restored on restart, ignoring another UUID's session, and the quiet signed-out state on refresh failure; verify unit tests for each case
- [x] 8.4 Map sign-in failures (unverifiable account, Mojang unavailable, cloud unreachable, unexpected) to localized messages; verify unit tests per case and a manual check with an offline-mode dev client
- [x] 8.5 Implement sign-out that revokes when reachable and always deletes the local session and listing; verify a unit test with the cloud unreachable

## 9. Mod library and cache (`ui`, `library`, `cache`, `thumbnail` packages)

- [x] 9.1 Spike: render a fixed model into an offscreen 256 x 256 render target on 26.1 and save it as a PNG (D12); verify a PNG file is produced in a dev client, and record findings in design.md
- [x] 9.2 Build the small widget set (card grid, live search box, folder and tag sidebar, details panel, confirm dialog) on `AbstractWidget` (D11); verify in a dev client that each widget renders and responds to mouse and keyboard
- [x] 9.3 Implement the library screen bound to the configurable key (default `H`), showing cloud and local entries with cards, placeholders and the account status bar; verify in a dev client against the AppHost with mixed entries
- [x] 9.4 Implement combined search, folder and tag filtering and the details panel with versions and sorted block counts; verify unit tests for the filter logic and a manual check
- [x] 9.5 Implement rename, move, tag and delete (with confirmation) from the library, disabled when signed out; verify a manual check against the AppHost and a unit test for the signed-out state
- [x] 9.9 Allow renaming and deleting (with confirmation) local-only entries, also signed out, without touching the source file; verify a unit test and a manual check in a dev client
- [x] 9.6 Implement the read-through cache with hash verification on download and read, LRU eviction at the configured size, and local entries exempt (D10); verify unit tests for reuse, a corrupted file, eviction and exemption
- [x] 9.7 Implement the stored listing and offline mode with unavailable markers for uncached versions; verify a manual check with the AppHost stopped and a unit test for listing storage per UUID
- [x] 9.8 Confirm loading never blocks the game by adding an artificial 10-second delay in a dev setup; verify in a dev client that the game keeps rendering and the loading state shows

## 10. Mod import and upload (`importing` package)

- [x] 10.1 Implement file drop onto the library screen with per-file results; verify a manual drop of two valid and one invalid file
- [x] 10.2 Implement the import list for `schematics/` and `heavymatica/import/` including subfolders; verify a manual check with Litematica files present
- [x] 10.3 Store local-only entries as copies keyed by hash, usable signed out and after the source is deleted; verify unit tests
- [x] 10.4 Detect duplicates by hash against local and cloud entries and offer skip or import anyway; verify a unit test and a manual check
- [x] 10.5 Implement uploading a local entry or importing straight to the cloud, as a new schematic or a new version of a chosen schematic, keeping the local entry on failure; verify against the AppHost, including an over-quota failure
- [x] 10.6 Render and attach the thumbnail after upload using the 9.1 approach with the block budget and frame spreading, skipping it on failure; verify a manual upload shows a thumbnail and a forced render failure still uploads

## 11. Integration checks

- [x] 11.1 End to end against the AppHost: sign in, import a Litematica file, upload it, restart the client, find it by search, download it into an empty cache, and verify the hash; verify by running the flow in a dev client
- [x] 11.2 Join a vanilla dedicated server (without Heavymatica) with the mod installed; verify the connection succeeds and the library works
