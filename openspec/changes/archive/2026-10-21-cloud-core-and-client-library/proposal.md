# Proposal

## Why

Heavymatica's core promise is "your schematics, everywhere": a player's library lives in the Heavymatica Cloud and is available in any world, on any server, without copying files around. Nothing exists yet, so this first change builds the foundation every later feature (placement UI, hand-build helper, multiplayer sync, machines, dashboard) depends on: a cloud service that stores schematics and an in-game library that talks to it.

## What Changes

- New .NET cloud service (ASP.NET Core, Akka.NET, Akka.Streams, MongoDB, S3-compatible blob storage, orchestrated locally with Aspire).
- Player identity verified through the Mojang session handshake, so players sign in with their Minecraft account and never create a separate one. The service issues its own access tokens.
- Schematic upload through a streaming pipeline that hashes, parses, validates and indexes uploads, storing blobs in S3 under their content hash.
- Immutable schematic versions: uploading again creates a new version, existing versions never change.
- Library organization: names, folders, tags, delete, plus per-account storage limits.
- Downloads through short-lived pre-signed URLs, so blob bytes never pass through the API.
- Thumbnails rendered by the client at upload time and stored alongside the version.
- New NeoForge client mod `heavymatica` (core) with:
  - account sign-in,
  - an in-game library browser (search, folders, tags, thumbnails),
  - a read-through local cache that keeps downloaded schematics usable offline,
  - local import of `.litematic`, Sponge `.schem` and vanilla structure `.nbt` files, both as local-only entries and as uploads.
- Shared understanding of the supported schematic formats and a canonical internal model, implemented on both the Java and the .NET side.
- Repository scaffolding: all source under `src/`, one subfolder per part (API contract, cloud, mod, conformance fixtures), with code organized in vertical slices.
- A contract-first cloud API: a hand-written OpenAPI document is the single source of truth, and both the server controllers and the mod's client are generated from it.

Out of scope for this change: placement and ghost rendering, material swaps and variants, layer views, hand-build helpers, multiplayer sync, server-side read tokens, the `heavymatica_machines` addon, the web dashboard, web login, `.blueprint` import and the marketplace.

## Capabilities

### New Capabilities
- `cloud/identity`: verifying a player's Minecraft identity through the Mojang session handshake and issuing and refreshing service access tokens.
- `cloud/schematic-storage`: uploading, versioning, organizing, limiting, downloading and deleting schematics and their thumbnails.
- `schematic-formats`: which schematic file formats are accepted, how they map to the canonical model, and the validation rules both sides apply.
- `client/cloud-account`: signing in to and out of the Heavymatica Cloud from the game and keeping the session alive.
- `client/schematic-library`: the in-game library browser, the local cache and offline behaviour.
- `client/schematic-import`: importing local schematic files into the library, kept local-only or uploaded.

### Modified Capabilities
- None (greenfield project).

## Impact

- New code:
  - `src/api/` holds the OpenAPI contract.
  - `src/cloud/` is the .NET solution: one service project, its test project and an Aspire AppHost.
  - `src/mod/` is the Gradle build for NeoForge 26.1.2 on Java 25.
  - `src/conformance/` holds the shared sample files.
- New build tooling: NSwag (server generation), OpenAPI Generator (Java client generation) and Spectral (contract linting). The mod bundles Jackson for the generated client.
- New infrastructure: MongoDB, an S3-compatible store (MinIO locally), and outbound calls from the cloud to Mojang's session server.
- New dependency on Mojang's session server for sign-in. If it is down, new sign-ins fail but existing tokens keep working.
- The client mod makes outbound HTTPS calls to the Heavymatica Cloud. This must be disclosed on the mod's distribution pages.
- No dependency on `servus_foundation` in this change.
