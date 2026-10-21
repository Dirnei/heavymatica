# Design

## Context

The repository is empty apart from OpenSpec, so every structural choice here is new. For motivation see proposal.md, Why. For behaviour see the specs under `specs/`.

These constraints come from decisions made before this change:
- The core mod `heavymatica` targets NeoForge 26.1.2 on Java 25, matching the author's Servus mods. It must not depend on `servus_foundation`, because it has to stay installable on the client alone.
- The cloud is C# / .NET with Akka.NET and Akka.Streams, MongoDB for metadata, and S3-compatible storage for blobs. Aspire runs everything locally.
- The cloud is the source of truth. The client keeps a read-through cache and local-only entries, and never syncs in both directions.
- Placements will later pin an immutable version and game servers will fetch from the cloud too. Nothing in this change may make versions mutable or tie blobs to the client.

## Goals / Non-Goals

**Goals:**
- A repo layout that later changes (placement UI, machines addon, dashboard) slot into without restructuring.
- One API contract that the server and the client both compile against, so they cannot drift.
- Format readers that give the same result in Java and .NET, proven by shared fixtures.
- An upload path that never holds an unbounded file in memory and never leaves half-written state visible.

**Non-Goals:**
- Cluster sharding and build-project actors. They arrive with multiplayer sync and are not introduced here.
- Production hosting, CDN and a domain (see Open Questions).
- Rendering schematics in the world. The only rendering here is the offscreen thumbnail.

## Decisions

### D1. Repository layout and vertical slices

```
heavymatica/
  openspec/
  src/
    api/
      heavymatica-cloud.v1.yaml      OpenAPI contract (D2), hand-written
    cloud/
      Heavymatica.slnx
      Directory.Build.props          shared build settings
      Directory.Packages.props       Central Package Management
      Heavymatica.Cloud/             the service: one project
        Features/
          Identity/                  Challenge, Verify, Refresh, SignOut, DevMojang
          Account/                   Me
          Schematics/
            Upload/  Listing/  Details/  Organize/
            Links/  Thumbnails/  Delete/  Sweeper/
        Formats/                     NBT reader, canonical model, format readers
        Infrastructure/              Mongo, S3, auth, Akka hosting, service defaults
      Heavymatica.Cloud.Tests/       next to its project: unit + integration tests
      Heavymatica.Cloud.AppHost/     Aspire orchestration
    mod/
      settings.gradle  build.gradle  gradlew
      heavymatica/                   core mod (mod id `heavymatica`)
        src/main/java/io/github/dirnei/mc/heavymatica/
          account/  library/  cache/  importing/  thumbnail/  formats/  ui/
        src/test/java/...
    conformance/                     shared sample files + expected results (D7)
```

Rules:
- **Projects exist only where a deployment or tooling boundary requires one.** The cloud has three: the service, its tests (placed next to it), and the Aspire AppHost, which Aspire requires as a separate project.
  - Format readers, infrastructure and service defaults are namespaces inside `Heavymatica.Cloud`, not projects.
  - Later changes add `src/mod/heavymatica_machines/` next to the core mod. A second .NET project is only added if it is deployed separately.
- **Vertical slices.** Each feature folder owns its endpoint implementation, request handling, persistence access and validation. Code moves to `Infrastructure/` only when two or more slices share it. Namespaces follow folders (`Heavymatica.Cloud.Features.Schematics.Upload`).
- **The mod follows the same idea.** Its packages are features, not layers.
- **Gradle's `src/main/java` stays.** Fighting the Gradle convention is not worth a cleaner-looking path.

*Alternatives considered:*
- Layered projects (Api, Domain, Infrastructure, Contracts). Rejected because with one service they add project references and indirection without any boundary they actually enforce.
- Separate repos for mod and cloud. Rejected because the contract and the conformance fixtures change together with both sides.

### D2. Contract-first API

`src/api/heavymatica-cloud.v1.yaml` is the single source of truth for the HTTP API. Neither side hand-writes DTOs or route definitions.

- **Format.** OpenAPI 3.0.3, because 3.1 support in the generators is still incomplete. The contract is linted with Spectral, using the OpenAPI ruleset plus rules requiring an `operationId`, a tag, and problem-details error responses on every operation.
- **Tags.** The tags `Auth`, `Account` and `Schematics` map to generated controllers.
- **Server generation.** NSwag.MSBuild generates DTOs and controllers into `obj/` at build time; nothing generated is committed.
  - Controllers use NSwag's interface style: each generated controller forwards to an `I<Tag>Controller` interface.
  - Feature slices implement those interfaces. A slice implementation is a thin adapter that calls the slice's handler.
  - Breaking the contract breaks the build.
- **Streamed uploads.** Upload operations declare `application/octet-stream` bodies. Their generated operations receive the raw request stream, which is passed unbuffered to the Akka.Streams pipeline (D5). Request body buffering is disabled for these routes.
- **Contract served as written.** The service serves the source YAML at `/openapi/v1.yaml` instead of generating a document from code, so what clients see is exactly the contract.
- **Client generation.** The mod uses the OpenAPI Generator Gradle plugin with generator `java`, `library=native`.
  - It is built on `java.net.http` with async methods returning `CompletableFuture` (D4).
  - Its JSON library, Jackson, is bundled into the mod jar with NeoForge jarJar, since Minecraft does not ship it.
  - Generated sources go to `build/generated` and are not committed.

*Alternatives considered:*
- Code-first with Swashbuckle or the built-in OpenAPI generator. Rejected because the contract would follow the code, and the Java client would only find out about changes after the fact.
- OpenAPI Generator's `aspnetcore` server generator. Rejected because it generates a whole project scaffold, which does not fit the slice layout.
- Kiota for the Java client. Rejected because it needs its own runtime libraries, which would be bundled as well.
- `okhttp-gson` client. It reuses Minecraft's Gson but bundles OkHttp instead; there is no real gain over the built-in HTTP client.

### D3. Sign-in handshake

```
 client                       cloud                         Mojang session server
   | POST /auth/challenge  -->  |  store {id, serverId,          |
   | <-- {challengeId, serverId}|   expires=+60s, used=false}    |
   | joinServer(uuid, mcToken, serverId) ----------------------> |
   | POST /auth/verify {challengeId, name} -->                   |
   |                            |  mark used (atomic)            |
   |                            |  hasJoined(name, serverId) --> |
   |                            |  <-- {uuid, name} or 204       |
   | <-- {access JWT, refresh}  |  upsert account by uuid        |
```

- `serverId` is 20 random bytes as hex, passed as-is to both `joinServer` and `hasJoined`. The vanilla shared-secret hashing is not needed because nothing is encrypted with it.
- Challenges live in Mongo with a TTL index. `verify` marks the challenge used with a single conditional `findOneAndUpdate` (`used=false and expires>now`), which gives single use even across several API instances.
- Access tokens are 15-minute JWTs signed with an asymmetric key (ES256) loaded from configuration, so the later game-server read tokens (a future change) can be checked with the public key alone.
- Refresh tokens are opaque random values, stored only as SHA-256 hashes. Each belongs to a session family; presenting an already-rotated token revokes the whole family.
- The Mojang base URL is configuration. In Development, the `DevMojang` slice maps a fake `joinServer` / `hasJoined` pair inside the service and the base URL points at it. The slice is never mapped in other environments.
- The challenge and verify endpoints get per-IP rate limits (ASP.NET rate limiter), because each `verify` costs a call to Mojang.

*Alternative considered:* Microsoft OAuth device-code login. Rejected for in-game sign-in, because the handshake needs no user interaction and never exposes credentials to us. Web login can be linked to the UUID later.

### D4. Client side of sign-in

The client calls `joinServer` through the game's own session service with the current user's access token, so the token goes only to Mojang (client/cloud-account). Sessions are stored as `<gamedir>/heavymatica/sessions/<uuid>.json`, and the client checks the running game's UUID before using one.

All cloud calls go through the generated client (D2) using its async methods. A small wrapper in `account/` adds the bearer token, refreshes it once on a 401, and hands results back to the render thread with `Minecraft.execute`, so nothing blocks a frame.

### D5. Upload pipeline (Akka.Streams)

```
 request body --> [limit 16 MiB] --+--> [SHA-256]
                                   +--> [spool to temp file]
                                             |
         [read + validate (D6)] <-----------+   (decompression capped at 128 MiB)
                    |
         [compute metadata + block counts]
                    |
         [reserve quota]  conditional $inc on account.usedBytes
                    |
         [put blob]  S3 key accounts/<uuid>/blobs/<sha256>  (skip if it exists)
                    |
         [insert version doc]  version number from $inc on schematic.nextVersion
                    |
         on any failure after the reservation: release the quota; the blob is left for the sweeper
```

- **Disk spool, not memory.** The NBT formats are gzip-compressed and need random access after decompression. The pipeline spools the compressed bytes (16 MiB at most) to a temp file and keeps memory bounded.
- **Ordering.** The version document is written last, so a version is visible only once its blob exists. A failed upload therefore leaves at most an orphan blob, which never shows up in the library. The `Sweeper` slice periodically deletes orphan blobs older than one hour and blobs no longer referenced by any version. This satisfies "rejected upload leaves nothing behind" as far as any player can observe.
- **Per-account blob keys.** Blobs are keyed per account rather than globally, so deduplication never crosses accounts. Deleting can simply remove a hash once no version in that account references it, and no shared reference counting is needed.
- **Re-uploads.** The identical re-upload check compares the new hash with the latest version's hash before reserving quota.
- **Why Akka.Streams.** It gives backpressure from the request body to disk while enforcing the 16 MiB limit and hashing on the way. Once the body is spooled, every remaining step (read, quota, blob put, version insert) runs exactly once per upload, so those steps are plain async code in the `Upload` slice rather than one-element stream stages; the blob is streamed from the spooled file to S3. No long-lived actor is needed for uploads. Version numbering and quota rely on Mongo atomic operations instead of a per-account actor; that actor can come later if contention requires it. The `Sweeper` runs as an Akka.NET actor with a periodic timer.

### D6. Format readers

Both sides implement the same reader structure: detect the format by root NBT shape, read through a capped stream, check limits, and then map to the canonical model. The .NET readers live in the `Heavymatica.Cloud.Formats` namespace; the Java readers live in the mod's `formats` package.

- **Canonical model:** `size`, `palette` (block state strings in a canonical form: `id[prop=value,...]` with properties sorted), an `int[]` of palette indices in X-Z-Y order, a block entity map keyed by position, `dataVersion?`, and `skippedEntities`.
- **Format detection:**
  - root compound with `Regions` and `MinecraftDataVersion` or `Version` is Litematica;
  - `Schematic` compound (v3), or root with `Version` 2 and `BlockData`, is Sponge;
  - root with `size`, `palette` or `palettes`, and `blocks` is a vanilla structure.
- **Litematica specifics:** block arrays are bit-packed and entries can span two longs. Region sizes can be negative, which moves the region origin. Overlapping regions are written in file order, so later regions win.
- **Sponge specifics:** v2 and v3 differ in nesting (v3 nests blocks under `Schematic.Blocks`). Block data is varint-encoded.
- **.NET side:** a small NBT reader written in-house, not a third-party library. It reads from a stream wrapped in a counting limiter, so the 128 MiB decompression cap and malformed-length checks are enforced inside the reader instead of after the fact.
- **Java side:** a small NBT reader of our own, mirroring the .NET one (same 128 MiB cap, chunked large arrays, depth limit 512) over a limited `GZIPInputStream`, instead of vanilla `NbtIo`. That keeps the limits identical on both sides, avoids the getter API changes of recent Minecraft versions, and lets the readers run in plain JUnit without bootstrapping the game.
- **Rejections:** every rejection carries a stable reason code (`unsupported_format`, `file_too_large`, `decompressed_too_large`, `dimensions_too_large`, `volume_too_large`, `malformed_nbt`, `palette_index_out_of_range`, `size_mismatch`). The codes are an enum in the contract (D2), so both sides share them, and the client maps each one to a localized message.

### D7. Conformance set

`src/conformance/files/` holds sample files covering every format and rule, including malformed and bomb files. `src/conformance/expected/<file>.json` records for each file either `{reject: <code>}` or `{size, dataVersion, paletteDigest, blockCounts, skippedEntities}`. `paletteDigest` is the SHA-256 of the block-state string at every position in canonical order, so the expected files stay small.

Both test suites iterate over the directory, so adding a fixture tests both sides at once.

*Alternative considered:* only parsing on the server and having the client trust it. Rejected because local import and offline use need a client reader anyway.

### D8. HTTP API (v1)

The contract (D2) is authoritative. This table only summarizes it:

| Method | Path | Tag | Purpose |
|---|---|---|---|
| POST | `/v1/auth/challenge` | Auth | issue a challenge |
| POST | `/v1/auth/verify` | Auth | complete sign-in |
| POST | `/v1/auth/refresh` | Auth | rotate tokens |
| POST | `/v1/auth/signout` | Auth | revoke the session |
| GET | `/v1/me` | Account | name, usage, quota |
| GET | `/v1/schematics?q=&folder=&tag=&cursor=` | Schematics | paged listing, newest change first |
| POST | `/v1/schematics` | Schematics | upload a new schematic (octet-stream body, name in a query parameter) |
| GET | `/v1/schematics/{id}` | Schematics | details with all versions |
| PATCH | `/v1/schematics/{id}` | Schematics | name, folder, tags |
| DELETE | `/v1/schematics/{id}` | Schematics | delete the schematic |
| POST | `/v1/schematics/{id}/versions` | Schematics | upload a new version (octet-stream body) |
| DELETE | `/v1/schematics/{id}/versions/{n}` | Schematics | delete a version |
| PUT | `/v1/schematics/{id}/versions/{n}/thumbnail` | Schematics | attach the thumbnail (`image/png` body) |
| POST | `/v1/schematics/{id}/versions/{n}/links` | Schematics | pre-signed URLs for file and thumbnail |

- Errors use RFC 7807 problem details with the reason code.
- A schematic owned by someone else returns `404`, the same as a missing one.
- The listing uses cursor paging on `(updatedAt, id)`.

### D9. MongoDB documents

- `accounts {_id: uuid, name, usedBytes, quotaBytes, createdAt}`
- `challenges {_id, serverId, expiresAt (TTL), used}`
- `sessions {_id: familyId, uuid, currentRefreshHash, revoked, lastUsedAt}` with a TTL of 30 days on `lastUsedAt`
- `schematics {_id, owner, name, nameLower, folder, tags[], nextVersion, latest: {...version summary}, updatedAt}`
- `versions {_id, schematicId, owner, number, sha256, format, sizeBytes, dims, dataVersion, blockTotal, blockCounts{}, hasThumbnail, thumbBytes, createdAt}`

Indexes cover `schematics (owner, updatedAt desc)`, `(owner, folder)` and `(owner, tags)`. Name search is a case-insensitive substring match on `nameLower`, which is enough at per-account library sizes. The latest version summary is denormalized onto the schematic, so a listing is a single query.

Each collection is accessed only by the slices that own it. Shared mapping and index creation sit in `Infrastructure/Mongo`.

### D10. Client storage layout

```
<gamedir>/heavymatica/
  sessions/<uuid>.json
  library/<uuid>/listing.json      last known cloud listing (deleted on sign-out)
  local/index.json + local/<sha256>.<ext>   local-only entries (never evicted)
  cache/<sha256>, cache/thumbs/<sha256>.png  LRU, default 1 GiB
  import/                          Heavymatica's own import folder
```

The cache index records the last access time for each file, and eviction runs before every write to the cache. Local entries are keyed by hash, which also powers duplicate detection on import.

### D11. Library UI

The UI is a small set of widgets of our own on top of vanilla `AbstractWidget`: a card grid, a search box with live filtering, a folder and tag sidebar, a details panel and a confirm dialog. Foundation's `PopupPanel` is not available to the core mod, and a third-party UI library would add a dependency that has to be installed on the client.

Layout:

```
 +-------------------------------------------------------------+
 | [search........]                    Dirnei  12.4/100 MiB  o |
 +-----------+-------------------------------------------------+
 | Folders   |  [card] [card] [card] [card]                    |
 |  houses   |  [card] [card] [card] [card]                    |
 |  towers   |                                                 |
 | Tags      |-------------------------------------------------|
 |  #spruce  |  Details: versions, dims, block counts, actions |
 +-----------+-------------------------------------------------+
   o = cloud status (signed in / offline / signed out)
```

### D12. Thumbnail rendering

The client renders the canonical model's blocks into an offscreen 256 x 256 render target with a fixed isometric camera, reads the pixels and writes them as a PNG.

- Large schematics can be slow, so rendering stops at a block budget (for example 200,000 visible blocks, preferring the outer surface) and runs across frames.
- Any failure skips the thumbnail, as the spec allows.

**Spike findings (task 9.1, 26.1.2 on an NVIDIA RTX 3090):** rendering works without the world or a GUI frame.
- **Output:** a colour texture (render attachment, copy source and copy destination) and a depth texture are created with `RenderSystem.getDevice()`. Then `RenderSystem.outputColorTextureOverride` / `outputDepthTextureOverride` point at them, the way the picture-in-picture GUI renderers do. Clearing a texture requires `USAGE_COPY_DST`.
- **Projection:** a 256 x 256 orthographic projection, applied between `RenderSystem.backupProjectionMatrix()` and `restoreProjectionMatrix()`.
- **Blocks:** each block is tessellated with `ModelBlockRenderer.tesselateBlock` against a fully lit `BlockAndTintGetter` over the canonical model, and its quads go to the moving-block render types (`RenderTypes.solidMovingBlock()` and the cutout and translucent variants) through an immediate `MultiBufferSource`.
- **Readback:** `CommandEncoder.copyTextureToBuffer` reads the texture into a `NativeImage` (rows flipped, alpha kept for a transparent background), which is written as PNG.
- **Result:** the house fixture rendered correctly in 82 ms.
- **Limitation:** blocks drawn by block-entity renderers (chests, signs, banners) are skipped, since they are not block models. This is acceptable for thumbnails.

### D13. Local development and tests

- **Aspire:** `Heavymatica.Cloud.AppHost` starts MongoDB and MinIO (Aspire and CommunityToolkit hosting integrations) plus the service in Development, where the `DevMojang` slice stands in for Mojang.
- **Pointing the mod at it:** the mod's client config has `cloudUrl`, so a dev client can target the AppHost.
- **.NET tests:** `Heavymatica.Cloud.Tests` uses xUnit. Folders mirror the slices. Unit tests cover handlers and formats, and integration tests use Aspire testing (`DistributedApplicationTestingBuilder`) against real Mongo and MinIO.
- **Java tests:** JUnit for readers, cache and session logic.
- **Contract checks:** Spectral lint runs before both builds. A test fetches `/openapi/v1.yaml` from the running service and checks it is byte-identical to the source file.
- **In-game checks:** the dev client runs accept a script of UI steps (`HEAVYMATICA_DEV_SCREEN`). The steps dispatch real input events (typing, clicks, file drops, the library key) and take screenshots, so the library can be checked on a real client and GPU without manual clicking. `HEAVYMATICA_THUMBNAIL_SPIKE` and `HEAVYMATICA_QUICKPLAY` cover thumbnail rendering and joining a server. See the root README.

## Risks / Trade-offs

- **Rendering API changes.** 26.1 rendering API changes may make offscreen thumbnail rendering hard. → Do a spike early in the tasks. Thumbnails are optional by spec, so the rest can ship without them.
- **Mojang session server.** It can be slow, rate-limited or change. → Sign-in is rate-limited and errors are distinct. Existing sessions do not depend on Mojang, and the base URL is configurable.
- **Tokens on disk.** Refresh tokens are stored in plain JSON on the player's disk, readable by other local software. → Refresh tokens rotate and detect reuse, and sign-out revokes them. OS keychain storage can come later.
- **Drift between readers.** Two reader implementations can drift apart. → The conformance set (D7) runs in both CI suites, and a format bug fix starts with a new fixture.
- **Format edge cases.** Litematica edge cases (bit packing across longs, negative region sizes) are easy to get wrong. → Collect real-world fixture files from several Litematica versions early.
- **Upload buffering.** NSwag's generated upload operations may buffer the body or bind it in a way that breaks streaming. → Verified early in the contract tasks: a binder hands the generated `IFormFile` parameter a stream over the raw request body, and a test proves the slice reads a 16 MiB upload while the client is still sending it. Allocation counts were not usable as the check, because Kestrel and HttpClient churn about three times the body size even on a bare endpoint. If generation cannot stream, the two upload operations are mapped by hand while still using generated DTOs.
- **Generator output.** The Java client from OpenAPI Generator can be verbose or awkward around octet-stream bodies. → The `account/` wrapper hides it from feature code. If the upload call needs it, a hand-written call next to the generated client is acceptable.
- **Jar size.** Bundling Jackson through jarJar adds about 2 MB to the mod. → Accepted. The client choice only affects the build and the wrapper.
- **Orphan blobs.** These use storage until the sweeper runs. → They are bounded by the 16 MiB upload limit and swept hourly; quota is never charged for them.
- **Platform disclosure.** Mod platforms may require disclosing network access. → State it on the distribution pages and keep the explicit first sign-in (client/cloud-account).
- **Substring name search.** This does not scale to huge libraries. → It is acceptable at expected sizes; switch to a text index if needed.

## Migration Plan

This is a greenfield deployment with nothing to migrate. Before the first public release:
1. Deploy the service with production MongoDB and an S3 bucket.
2. Generate the ES256 signing key and store it as a secret.
3. Set the mod's default `cloudUrl`.

Rolling back the cloud is a redeploy. The contract is versioned (`v1` in the path and the file name), so later changes can add a v2 contract without breaking released mods.

## Open Questions

- Production hosting provider and domain for `cloudUrl`. This only affects deployment configuration.
- The default free quota. This design assumes 100 MiB per account, configurable per account.
- The exact rate-limit numbers for the auth endpoints.
