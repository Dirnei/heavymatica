# Tasks

Gradle runs as `rtk proxy ./gradlew ...` from `src/mod`; cloud tests run with
`dotnet test --project src/cloud/Heavymatica.Cloud.Tests`. Tasks marked **(in-game check)** run in `runClient`,
with a `HEAVYMATICA_DEV_SCREEN` script where one helps.

## 1. Writing files

- [x] 1.1 Add `Nbt.writeRoot` (gzip with modification time 0, every tag type, entry order kept); verify `NbtTest` round-trips every tag type through `writeRoot` and `readRoot`, and that writing twice gives the same bytes
- [x] 1.2 Add `LitematicaWriter` (version 7, one region, metadata, packed block states, tile entities with relative positions, empty entities and ticks); verify a `LitematicaWriterTest` that round-trips models with 1, 2, 3, 5 and 17 palette entries (entries spanning longs), air, a modded block and block entities through `SchematicReader`, with the same size, palette digest, block entity positions and IDs, and data version
- [x] 1.3 Add `src/conformance/files/written/capture.litematic` from a fixed model and a hand-written `expected/written/capture.litematic.json`; add a mod test that writes the same model and compares the bytes (and rewrites the file when `HEAVYMATICA_REGENERATE_WRITTEN=1` is set); document `files/written/` in `src/conformance/README.md`; verify the mod's `ConformanceTest` and the cloud's `ConformanceTests` both pass on the new file

## 2. Library from bytes and export

- [x] 2.1 Add `LocalLibrary.add(byte[], String name, String extension)` with the same validation and duplicate handling, and route `add(Path)` through it; verify the existing `LibraryTest` and `ImportingTest` pass, plus a test that rejected bytes leave the library unchanged
- [x] 2.2 Add the exporter: name cleaning, the `(n)` free-name rule, overwrite and keep-both handling, writing through a temporary file in `schematics/`; verify unit tests for unsafe characters, an empty-after-cleaning name, keep both with `(2)` and `(3)` taken, overwrite, and that cancelling changes no file
- [x] 2.3 Add Export to the details panel for local entries and cloud versions (cache, or download first; disabled offline with the reason) and the conflict screen with Overwrite, Keep both and Cancel; verify **(in-game check)**: exporting a local entry and an uncached cloud `.schem` version writes both into `schematics/` byte for byte, a second export asks, and offline an uncached version shows Export disabled

## 3. Capture box and wheel

- [x] 3.1 Add the game-free `CaptureBox` (set by click, active corner, camera-relative and vertical moves with Shift x10, build-height clamp, swap, reset, size, limit check); verify a `CaptureBoxTest` covering every scenario under "Setting corners", "Moving the active corner with keys", "Swapping the active corner" and "Box limits"
- [x] 3.2 Extend `RadialMenu` with the Capture segment and the capture page (greyed-out rules, commands `START_CAPTURE`, `CAPTURE`, `SWAP_CORNER`, `RESET_CORNERS`, `CANCEL_CAPTURE`, no Bring back while capturing); verify `RadialMenuTest` cases for every scenario under "Capture segment" and "Capture wheel", and that the existing cases still pass
- [x] 3.3 Add `CaptureSession` holding the box, cleared on logout and dimension change, and the unbound `RemoteKeys.SWAP_CORNER` key with its translations; verify a unit test that leaving the world or changing dimension ends capture mode and clears the corners

## 4. Input and drawing

- [x] 4.1 Route `RemoteInput` through capture mode: right-click on the hit block, arrows and numpad + / - on the active corner with repeat, other placement keys drained, no follow updates, wheel commands wired; add the Shift + scroll listener that cancels the hotbar scroll only in capture mode with the remote held; verify **(in-game check)**: with an anchored placement selected, capture mode moves only the box, Shift + scroll swaps the corner without changing the hotbar slot, and plain scroll still changes the slot
- [x] 4.2 Draw the corner markers (active one brighter) and the box outline, and add the overlay's capture section (size in the warning colour past the limits, active corner position, key hints, progress); verify **(in-game check)**: a 12 x 9 x 9 box shows `12 x 9 x 9`, its outline covers both corner blocks, and a 1030-wide box shows the size in the warning colour

## 5. Capturing

- [x] 5.1 Add the snapshot: section-by-section copy within a per-tick budget, palette built on the way, chunk-loaded check per section, block entities from the client level (via `NbtIo` into `Nbt.Compound`, `id` added) or, in single player, from the integrated server through server tasks the client never blocks on; verify a unit test of the game-free part (section order, budget split, canonical index order, failure when a section reports unloaded) over a fake world reader
- [x] 5.2 Wire Capture: limits and loaded-chunk checks before the prompt, the name prompt prefilled with `Capture yyyy-MM-dd HH:mm` and the server warning, the snapshot, then writing, validating and `LocalLibrary.add` on a worker; end capture mode with a saved message, or keep it with the reason on failure; verify **(in-game check)**: in single player a 20 x 10 x 20 build with a chest of items and a sign is saved as a local entry, placed again elsewhere, and world comparison shows it complete; reading the entry's file back shows the chest with its items and the sign with its text; a box beyond render distance is refused before the prompt
- [x] 5.3 Check a large capture **(in-game check)**: a 256 x 64 x 256 box captures without a visible freeze, the overlay shows progress, and the entry uploads with a thumbnail
- [ ] 5.4 Check compatibility **(in-game check)**: the exported `.litematic` of a capture loads and places in Litematica for the same Minecraft version, and on a dedicated server (`runServer`) the prompt shows the warning

## 6. Docs

- [x] 6.1 Add capture and export to `README.md` and as a done step in `ROADMAP.md`, and add the "ignore air on a placement" option as later work there; verify both files describe the controls as the specs do (V, right-click, arrows, numpad, Shift + scroll)
