# Tasks

Cloud tests run with `dotnet test --project src/cloud/Heavymatica.Cloud.Tests`; while a dev AppHost is running, add
`-p:OutDir=<repo>/src/cloud/Heavymatica.Cloud.Tests/bin/alt/`. Gradle runs as `rtk proxy ./gradlew ...` from
`src/mod`. Tasks marked **(in-game check)** run in `runClient` with a `HEAVYMATICA_DEV_SCREEN` script; tasks marked
**(manual check)** run in a browser against the dev AppHost.

## 1. Contract

- [ ] 1.1 Add `ThumbnailSource` (`uploaded`, `rendered`) and the optional `Version.thumbnailSource` to `src/api/heavymatica-cloud.v1.yaml`, and update the `attachThumbnail` description (replaces a rendered thumbnail; 409 only for an uploaded one); verify `npm run lint` in `src/api`, `dotnet build src/cloud`, the mod's `compileJava` and `npm run check` in `src/web/player` all pass

## 2. Rendering

- [ ] 2.1 Add the game-free PNG encoder (8-bit RGBA, Sub filter, `ZLibStream`, CRC-32) (D5); verify unit tests that its output passes `AttachThumbnail.Dimensions`, decodes to the same pixels with a minimal test decoder (inflate + unfilter), and that a fully transparent 256 x 256 image stays under 2 KiB
- [ ] 2.2 Add the block colour table loader for the embedded `block-colors.json` (lookup by block ID, neutral grey cube for unknown IDs, shapes `cube`, `slab`, `cross`, `none`) with a small hand-written placeholder table until 4.1 exports the real one (D6); verify unit tests for a known block, an unknown block and each shape
- [ ] 2.3 Add the isometric rasteriser over `SchematicModel`: projection and fit with an 8-pixel margin, top/east/south faces with 100/80/64 % shading, painter's order, culling against opaque full cubes, slab type and snow layers, `cross` and `none`, alpha blending and the `MaxDrawnBlocks` sampling (D4); verify unit tests: a single stone block gives three differently shaded faces centred in the image, a block hidden on all three visible sides draws nothing extra, a bottom slab is lower than a cube, a model of only air yields "nothing to draw", and a 1024 x 16 x 1024 model renders within the budget

## 3. Queue and worker

- [ ] 3.1 Extend `VersionDocument` and `Latest` with `thumbnailSource`, `thumbnailDueAt`, `renderAttempts` and `renderClaimedUntil`, map `thumbnailSource` to the contract, add the partial `render_queue` index, and make `ThumbnailKey` take the source with `CreateLinks`, `ShowThumbnail`, deletion and `BlobSweeper` using it (D1); verify the existing `LibraryTests`, `UploadTests` and sweeper tests still pass, plus a sweeper test that keeps a referenced `.rendered.png` and deletes an orphaned one
- [ ] 3.2 Set `thumbnailDueAt` in `UploadSchematic` (now for a website session, now + `GracePeriod` otherwise) and add the idempotent startup step that queues versions without a thumbnail at their `createdAt` (D2); verify integration tests for both upload kinds, that an identical re-upload changes nothing, and that the startup step queues an old version once and leaves versions with a thumbnail alone
- [ ] 3.3 Add `ThumbnailWorker`: claim the newest due version, read and parse the blob, rasterise and encode, store at the rendered key, conditional update with an unconditional quota increment, delete the object when the update loses, "nothing to draw" ends the attempts, failures count attempts up to `MaxAttempts` (D2, D3); verify integration tests with a `FakeTimeProvider`: a website upload is rendered on the next run and reports `thumbnailSource: rendered` and a larger `usedBytes`, a game upload is not rendered before the grace period, an attach during the grace period means nothing is rendered, a newer due version is taken before an older backfill version, an over-quota account still gets its thumbnail, and a corrupt blob stops after three attempts
- [ ] 3.4 Let `AttachThumbnail` replace a rendered thumbnail (reserve, store the uploaded key, conditional update on `rendered`, release the rendered bytes, delete the rendered object) and keep 409 `thumbnail_exists` for an uploaded one (D3); verify integration tests for both, including the usage before and after the replacement
- [ ] 3.5 Add `ThumbnailWorkerActor` on a timer with `Thumbnails:*` options (`Enabled`, `GracePeriod`, `PollInterval`, `ClaimTime`, `RenderTimeout`, `MaxAttempts`, `MaxDrawnBlocks`), register it in `SchematicServices`, and document the options in the cloud README section (D7); verify `AppHostTests` pass with `--Heavymatica:EphemeralStorage=true` and that, under the AppHost, a schematic uploaded through the API with a website session reports a thumbnail within a minute

## 4. Block colours from the game

- [ ] 4.1 Add the DevScreens step `exportBlockColors=<path>` (registry walk, averaged UP and NORTH sprite pixels with default tints, alpha, shape and height from the default state) and list it in the DevScreens Javadoc (D6); verify **(in-game check)** a scripted run writes the table, then replace the placeholder `block-colors.json` with it, and check that `minecraft:grass_block` is green on top, `minecraft:glass` has alpha below 1, `minecraft:oak_slab` is a `slab` of height 0.5 and `minecraft:poppy` a `cross`
- [ ] 4.2 Render a fixed set of conformance files (`src/conformance/files`) with the real table into PNGs under the test output and look at them; verify by viewing them that the structures are recognisable, and keep one as a snapshot test of the rasteriser's output hash

## 5. Website

- [ ] 5.1 After an upload of a new schematic or version, poll `getSchematic` every 3 seconds for up to 60 seconds until the version reports `hasThumbnail`, then update the card or details page; stop when the page is left (D8); verify a Vitest test of the poll helper (stops on thumbnail, on timeout and on cancel), `npm run check`, and **(manual check)** that a website upload shows its rendered thumbnail without a reload

## 6. Integration

- [ ] 6.1 **(manual check)** and **(in-game check)**: upload a schematic on the website and check its thumbnail on the website and in the game's library; upload from the game and check the game's own thumbnail is kept (`thumbnailSource: uploaded`); start the cloud with an older version without a thumbnail and check it gets one
- [ ] 6.2 Update `ROADMAP.md` step 5 to mark `cloud-thumbnails` done; verify the file reads consistently

## Workflow follow-up

- Commit the implementation once all tasks are done (for example `feat(cloud): render thumbnails for versions without one`).
- Archive the change with `/opsx:archive`.
