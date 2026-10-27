# Design

## Context

See proposal.md, Why. What exists:
- **Upload:** `UploadSchematic` streams the file to S3 (`accounts/{owner}/blobs/{sha256}`), parses it with
  `SchematicReader` into a `SchematicModel` (palette plus block index array, up to 1024 per axis and 16,777,216 blocks)
  and writes a `VersionDocument`. `SchematicDocument.Latest` holds a copy of the newest version.
- **Thumbnails:** `AttachThumbnail` checks the PNG header (at most 256 x 256, 256 KiB), reserves quota, stores
  `accounts/{owner}/thumbnails/{schematicId}/{n}.png` and sets `HasThumbnail` and `ThumbnailBytes` on the version and
  on `Latest`. A second attach answers 409 `thumbnail_exists`. `CreateLinks` and `ShowThumbnail` sign links to that key.
  `BlobSweeper` deletes unreferenced blobs and thumbnails older than `OrphanAge`.
- **Background work:** `BlobSweeperActor` runs the sweeper on an Akka timer, registered in `SchematicServices`.
- **Game:** `Uploader` uploads, then renders with the real block models (`ThumbnailRenderer`) and attaches when the
  version reports no thumbnail. The game's thumbnail has a transparent background.
- **Website:** `Library.svelte` and `Details.svelte` show `<img src="/v1/schematics/{id}/versions/{n}/thumbnail">`
  when `hasThumbnail`, a placeholder otherwise.
- **Who uploads:** a website upload carries a `WebSession` principal (`User.IsWebSession()`), a game upload a bearer
  token.

## Goals / Non-Goals

**Goals:**
- A recognisable picture for every version, at no cost to the upload request.
- The game's model-rendered thumbnail always wins over the cloud's.
- No new third-party dependency in the cloud.

**Non-Goals:**
- Textures, block models, biome tints per position, block entities or entities.
- Several cloud instances rendering together beyond the claim in D2 (it is safe, not tuned).
- Re-rendering when the colour table changes.

## Decisions

### D1. Data model

```
versions  + thumbnailSource?  "uploaded" | "rendered"   (absent while there is no thumbnail)
          + thumbnailDueAt?   when the worker may render it
          + renderAttempts    int, default 0
          + renderClaimedUntil?
          index render_queue { thumbnailDueAt: -1 }  partial: hasThumbnail false, renderAttempts < 3
```

- A rendered thumbnail is stored at its own key `accounts/{owner}/thumbnails/{schematicId}/{n}.rendered.png`, so an
  attach racing a render never overwrites the other's object. `ThumbnailKey` takes the source; `CreateLinks`,
  `ShowThumbnail`, deletion and the sweeper use it.
- `SchematicDocument.Latest` carries the same fields, updated together with the version as today.
- Contract: `Version.thumbnailSource` (enum `uploaded`, `rendered`), optional and absent without a thumbnail. Older
  mods ignore it; the Java generator already maps unknown enum values.

### D2. Due time and the queue

- `UploadSchematic` sets `thumbnailDueAt` on a new version: now for a website upload, now + `GracePeriod` (1 minute)
  for any other caller. An identical re-upload creates no version and changes nothing.
- The worker takes the next version with one `FindOneAndUpdate`: `hasThumbnail: false`, `thumbnailDueAt <= now`,
  `renderAttempts < MaxAttempts`, and `renderClaimedUntil` missing or past, sorted by `thumbnailDueAt` descending.
  The update increments `renderAttempts` and sets `renderClaimedUntil = now + ClaimTime` (5 minutes).
- Newest due first means a fresh upload overtakes the backfill. The backfill cannot starve uploads, and uploads only
  delay the backfill.
- **Backfill:** a startup step (next to `AccountMigration`'s startup work) sets `thumbnailDueAt = createdAt` on every
  version without a thumbnail and without `thumbnailDueAt`. It is idempotent and needs no flag. Old versions then
  sort behind every new upload.
- After `MaxAttempts` (3) failures the version stays without a thumbnail and drops out of the partial index.

### D3. Storing a rendered thumbnail

1. Read the blob, parse it with `SchematicReader`, rasterise (D4) and encode (D5).
2. A version with nothing to draw (only air-like blocks) gets `renderAttempts = MaxAttempts` and no thumbnail.
3. Put the PNG at the rendered key, then one conditional update `hasThumbnail: false` → `hasThumbnail: true`,
   `thumbnailSource: rendered`, `thumbnailBytes`, and the same on `Latest` when it is that version.
4. If the update matched nothing (an attach won meanwhile, or the version was deleted), delete the rendered object.
5. Quota: the bytes are added with an unconditional increment, never through `TryReserveAsync`. A rendered thumbnail
   is a few KiB and must not fail because the account is full.

**Attach over a rendered thumbnail:** `AttachThumbnail` accepts a version whose `thumbnailSource` is `rendered`: it
reserves the new bytes, stores the uploaded key, updates conditionally on `thumbnailSource: rendered`, releases the
rendered bytes and deletes the rendered object. `thumbnail_exists` stays for `uploaded`.

### D4. Rasteriser

- **Projection:** fixed isometric, the same view direction as the game's thumbnails (from south-east, above). A block
  at (x, y, z) projects to `u = (x - z) * c`, `v = (x + z) * c / 2 - y * c` with `c = cos 30°`. The projected bounds
  of the whole model are fitted into 256 x 256 with an 8-pixel margin, so a small build gets large cubes.
- **Faces:** each drawn block contributes its top face and the two side faces toward the viewer (south and east),
  as polygons filled by a scanline rasteriser into an RGBA buffer with alpha blending. Shading: top 100 %, east 80 %,
  south 64 % of the block's colours.
- **Order and culling:** painter's order (increasing y, then increasing x + z). A face is skipped when the neighbour
  on that side is an opaque full cube; a block with no face left is skipped.
- **Shapes** from the colour table (D6): `cube`; `slab` and other low blocks drawn with their height (`type=top`
  raised, `type=double` a full cube, `layers=n` for snow at n/8); `cross` (plants, torches) as a thin centred column
  in the side colour; `none` not drawn. Fluids are drawn as cubes with their table alpha.
- **Budget:** when the model has more than `MaxDrawnBlocks` (2,000,000) non-air blocks, every k-th block per axis is
  taken with the smallest k that fits, which only matters for builds far too large to read at 256 pixels anyway.
- **Unknown blocks:** a neutral grey, cube.
- **Alternative:** render in the browser with three.js. That leaves game listings without thumbnails and needs every
  client to render. **Alternative:** ImageSharp for drawing and encoding. Its split licence and size buy nothing over
  ~200 lines of polygon filling and a PNG writer.

### D5. PNG encoder

8-bit RGBA, one IDAT through `ZLibStream` with `CompressionLevel.SmallestSize`, filter type Sub per row, CRC-32 from
`System.IO.Hashing` (part of the shared framework). Transparent background, like the game's thumbnails. The output
must pass `AttachThumbnail.Dimensions` and the 256 KiB limit; typical outputs are 5 to 40 KiB.

### D6. Block colour table

- `Features/Schematics/Thumbnails/block-colors.json`, an embedded resource:
  `{ "minecraft:stone": { "top": "#7d7d7d", "side": "#7d7d7d", "alpha": 1.0, "shape": "cube", "height": 1.0 }, ... }`
  keyed by block ID (properties are read by the rasteriser only for slab type and snow layers).
- **Made by the game:** a DevScreens step `exportBlockColors=<path>` walks the block registry and, for each block's
  default state:
  - averages the visible pixels of the sprites of the model's UP quads (top) and NORTH quads (side), weighted by
    alpha, with the default tint from `BlockColors` (so grass and leaves are green, not grey);
  - takes `alpha` from the sprites' mean alpha, lowered to 0.6 for the translucent layer;
  - takes `shape` and `height` from the state's shape: a full block is `cube`, a box lower than a block is `slab`
    with its height, a render shape without a model or an empty shape is `none`, and a non-colliding model (plants,
    torches, rails) is `cross`;
  - writes the JSON sorted by key.
- The table is regenerated when the mod moves to a new Minecraft version, and checked in. It holds averaged colours,
  not textures.
- **Alternative:** vanilla `MapColor`. It is available without textures, but its palette is coarse (64 colours) and
  many unrelated blocks share one, so thumbnails read much worse.

### D7. Worker

- `ThumbnailWorker` (render one version) and `ThumbnailWorkerActor` (timer), registered like `BlobSweeperActor`.
- The actor asks for the next version every `PollInterval` (5 seconds). After a render it asks again at once, so the
  backfill runs continuously while there is work, one version at a time per instance.
- Each render has a timeout (`RenderTimeout`, 30 seconds). A timeout or exception is logged and counts as a failed
  attempt; the claim expires and another attempt can follow.
- Options `Thumbnails:Enabled` (default true), `GracePeriod`, `PollInterval`, `ClaimTime`, `RenderTimeout`,
  `MaxAttempts`, `MaxDrawnBlocks`. Tests use a `FakeTimeProvider` and call `ThumbnailWorker` directly.

### D8. Website

After a successful upload (new schematic or new version), the page polls `getSchematic` every 3 seconds for up to
60 seconds until the uploaded version reports `hasThumbnail`, then updates the card or the details page. Leaving the
page stops the poll. Nothing changes for listings loaded later: they already read `hasThumbnail`.

## Risks / Trade-offs

- [Rendered thumbnails look plain next to the game's] → The game's always replaces them (D3), and game uploads get
  the grace period so the cloud doesn't render what the game is about to attach.
- [The averaged colours derive from Mojang's textures] → The table holds one or two colours per block, not textures,
  much like map colours. If that is ever a concern, `MapColor` (D6 alternative) is the fallback and needs no
  textures.
- [Large schematics take long to parse and draw] → One at a time, a timeout and the block budget; the file limits
  already bound the work.
- [Backfill load on first start] → Sequential, one version at a time, newest uploads first; it only uses idle time.
- [Rendered thumbnails push an account slightly past its quota] → A few KiB each; the next upload is refused as
  usual, and deleting frees them.
- [A game upload that never attaches waits a minute for its thumbnail] → Acceptable; it is the case today with no
  thumbnail at all.

## Migration Plan

1. Deploy the cloud. The startup step queues every version without a thumbnail; the worker fills them in.
2. Older mods keep attaching their own thumbnails within the grace period and ignore `thumbnailSource`.

**Rollback:** set `Thumbnails:Enabled=false`. Rendered thumbnails stay and keep working; the added fields are ignored
by older code.
