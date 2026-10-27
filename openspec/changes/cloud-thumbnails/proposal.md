# Proposal

## Why

Only the game makes thumbnails: it renders one with the real block models and attaches it after its upload. Schematics
uploaded on the website, and versions uploaded before thumbnails existed, show a placeholder on the website and in the
game. `cloud-thumbnails` is change 3 of 5 in step 5 of `ROADMAP.md`.

## What Changes

- **The cloud renders thumbnails.**
  - Every version that has no thumbnail gets one rendered by the cloud: a 256 x 256 isometric PNG of the visible
    blocks, coloured per block type.
  - Website uploads are rendered at once. Game uploads get a short grace period first, because the game attaches its
    own thumbnail right after uploading.
  - A thumbnail the uploader attaches replaces a rendered one, so the game's sharper image always wins. A thumbnail
    the uploader attached is still never replaced.
- **Backfill.** Versions that already exist without a thumbnail are rendered in the background, newest first, without
  slowing down new uploads.
- **Block colours from the game.** The colours come from a table exported from the game's textures by a dev step in
  the mod and shipped with the cloud. Blocks the table doesn't know (other mods' blocks) are drawn in a neutral
  colour.
- **Website:** after an upload, the card and details page show the thumbnail once it is rendered, without a reload.
- **Contract:** `Version` gains `thumbnailSource` (`uploaded` or `rendered`). Additive only; older mods ignore it.

Out of scope:
- rendering real block models, textures or entities in the cloud
- re-rendering thumbnails the uploader attached, or rendering again after a Minecraft update
- choosing the camera angle, or thumbnails of a single layer

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `cloud/schematic-storage`:
  - "Thumbnails": an attach may replace a rendered thumbnail; it is still rejected when an uploaded one exists.
  - "Version metadata": versions report where their thumbnail came from.
  - New: "Thumbnails rendered by the service", "Rendered thumbnails and the quota" and "Thumbnails for existing
    versions".
- `web/player-ui`:
  - New: "Thumbnail appears after upload".

## Impact

- **Contract (`src/api/heavymatica-cloud.v1.yaml`):** `Version.thumbnailSource`; the cloud controllers, the Java client
  and the website types are regenerated.
- **Cloud (`src/cloud/Heavymatica.Cloud`):**
  - new `Features/Schematics/Thumbnails` parts: the block colour table, an isometric rasteriser, a small PNG encoder
    and the render worker (an actor on a timer, like the blob sweeper)
  - `UploadSchematic` sets when a version becomes due for rendering; `AttachThumbnail` replaces rendered thumbnails
  - versions gain `thumbnailSource`, `thumbnailDueAt` and render attempt fields, with an index for the worker's query
- **Mod (`src/mod/heavymatica`):** a DevScreens step that exports the block colour table to the cloud's source tree.
  The game's upload and thumbnail flow are unchanged.
- **Website (`src/web/player`):** a short poll after an upload until the new version reports a thumbnail.
- **No new dependencies.** The PNG encoder uses `System.IO.Compression`.
- **Tests:** unit tests for the rasteriser, the PNG encoder and the colour table; integration tests for rendering,
  the grace period, replacement and backfill; a Vitest test for the poll.
