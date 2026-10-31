# Design

## Context

- `formats` only reads files. `Nbt` and `LitematicaFormat` have no write side. `SchematicModel` is independent of the
  game and already holds everything a file needs: size, palette (air at index 0), block indices in canonical order
  (x, then z, then y), block entities as `Nbt.Compound` by position, and the data version.
- Library entries come from files: `LocalLibrary.add(Path)` validates with `SchematicReader`, stores
  `local/<sha256>.<ext>` and records the entry. `Uploader` turns a local entry into a cloud schematic with a
  thumbnail.
- The remote's input lives in `RemoteInput`. It cancels right-click while a remote is held, reads `RemoteKeys` with
  key repeat and opens `RadialMenu`, a pure model of segments and pages that is unit-tested on its own.
- `Comparisons` already reads the client world in budgeted slices per tick through a chunk reader. Capture uses the
  same approach.
- The client only knows loaded chunks, and on a server it only has the block entity data the server sends for
  rendering (`getUpdateTag`).

## Goals / Non-Goals

**Goals:**
- Keep capture's logic (corners, box, limits, wheel pages) in classes free of the game, unit-tested like
  `PlacementController` and `RadialMenu`.
- Have one path from bytes into the library, so captures go through the same validation as imports.

**Non-Goals:**
- A general format converter or writers for `.schem` and `.nbt`.
- Reading block entity data from the server.

## Decisions

### Capture produces file bytes, then goes through the normal import path
The capture builds a `SchematicModel`, writes it with the new `LitematicaWriter`, and hands the bytes to a new
`LocalLibrary.add(byte[] content, String name, String extension)`. `add(Path)` is rewritten to use it. The bytes are
read back with `SchematicReader` before they are stored, as for every import.
- Alternative: store the model directly and write the file only on upload or export. Rejected because every library
  entry is a file today: hashing, duplicate detection, cache, thumbnails and upload all expect bytes.

### A game-independent NBT writer next to the reader
`Nbt.writeRoot(Compound, OutputStream)` writes gzip-compressed big-endian NBT and keeps entry order, since
`Compound` holds a `LinkedHashMap`. `LitematicaWriter` builds the root compound and packs the block states the way
`LitematicaFormat.unpack` reads them: `bits = max(2, ceil(log2(palette size)))`, entries spanning longs. The gzip
header's modification time is fixed at 0, so the output is deterministic.
- Alternative: Minecraft's `NbtIo` and `CompoundTag`. Rejected because `formats` has to stay free of the game so it
  can be tested against the conformance set without a running game, as the readers are.

### Litematica format version 7, one region
The writer writes `Version` 7 with `SubVersion` 1, `MinecraftDataVersion`, and `Metadata` (`Name`, `Author`,
`Description` empty, `TimeCreated`, `TimeModified`, `EnclosingSize`, `RegionCount` 1, `TotalBlocks`, `TotalVolume`).
It writes one region named after the schematic at `Position` 0, 0, 0 with a positive `Size`, and empty `Entities`,
`PendingBlockTicks` and `PendingFluidTicks`. Block entities are written with their ID and `x`, `y`, `z` relative to
the region. Version 7 is what current Litematica writes for 26.x, and the reader and conformance set already cover it.

### Capture box state is a pure model
`CaptureBox` holds corner 1, corner 2 and which one is active, plus the build-height bounds. It has the operations
from the spec: set by click, move the active corner relative to the camera (reusing the yaw snapping that placement
moves use), vertical move, swap, reset, size and the limits check. `CaptureSession` holds the box while capture mode
is on and clears it on logout and dimension change, the same hooks `PlacementSession` uses.

### Input routing: capture mode takes over the remote
`RemoteInput.tick` and `interaction` check capture mode first. While it is on:
- right-click goes to the box, using the block the ray hits, not the block in front of it as for placements
- the move keys and numpad + / - move the active corner
- the other placement keys are drained, as `layerKeys` already does
- `FOLLOWING` follow updates are skipped

Shift + scroll is handled in a `InputEvent.MouseScrollingEvent` listener, which is cancelled while capture mode is on,
a remote is held, no screen is open and Shift is down, so the hotbar doesn't move. Shift is read with
`hasShiftDown()`, the modifier placement moves use, not the sneak key, so sneak bindings don't matter. A new
`RemoteKeys.SWAP_CORNER` (unbound by default) joins `RemoteKeys.ALL`.

### The wheel learns a capture page
`RadialMenu.Selection` gets a capture flag and the corner count. `Page.MAIN` returns the capture segments when the
flag is set, and adds `Entry.CAPTURE` for NONE, ANCHORED and LOCKED. New commands: `START_CAPTURE`, `CAPTURE`,
`SWAP_CORNER`, `RESET_CORNERS`, `CANCEL_CAPTURE`. Bring back is not shown while capturing.
- Alternative: a separate capture wheel screen. Rejected because `RadialMenu` already handles greyed-out segments,
  hold and toggle modes, and its tests.

### Snapshot over ticks, encoding off the render thread
Reading `ClientLevel` is only safe on the client thread. The snapshot copies the box section by section (16 x 16 x 16
cells), up to a block budget per tick (default 262,144 cells), into the model's index array and palette. Block
entities are copied in the same pass: `saveWithoutMetadata(registryAccess)`, converted to `Nbt.Compound` by writing it
with `NbtIo` and reading it back with `Nbt.readRoot`, and the `id` added from the block entity type. Before each
section it checks the chunk is still loaded and fails the capture if it isn't. When the copy is done, writing,
compressing, validating and storing run on a worker thread. The result comes back to the client thread, which ends
capture mode and shows the message. The overlay shows the copied share as a percentage, then "saving".
- The world can change while a large capture runs. Each section is read in one go, so a section is consistent.
  Changes across sections during a capture are accepted.
- Alternative: copy everything in one tick. Rejected because 16 M cells would freeze the game for seconds.

### Full block entity data in single player
The client level holds only the data the server sends for rendering, even in single player, so chests arrive
without their items. In single player, the snapshot therefore reads block entities from the integrated server's
level instead: per section, a task on the server thread (`server.submit`) saves the block entities in that section,
and the snapshot doesn't count that section as done until the task has finished. The client thread never blocks
on it and checks again the next tick. Block states still come from the client
level. On a remote server the client level's data is used, and the prompt warns.
- Alternative: always use the client level and warn everywhere. Rejected because single player is where most
  players build what they want to save, and the full data is right there.

### Unloaded chunk check before starting
On Capture, every chunk column in the box is checked with `getChunk(x, z, ChunkStatus.FULL, false)` on the client chunk
cache (`ClientLevel.hasChunk` is always true). If any is missing, the capture
is refused before the name prompt, with the box left unchanged.

### Drawing the box
The outline and the corner markers are drawn as line boxes in the same world pass as the target lines, slightly
inflated so they don't flicker against block faces. The active corner is drawn in a brighter colour. The overlay gets
a capture section in place of the placement section: size (warning colour past the limits), active corner position,
key hints (right-click, arrows, numpad, Shift + scroll, V).

### Export copies the stored file
`Exporter.export(Path source, String name, String extension, Conflict choice)` writes into
`<gameDir>/schematics/`, the folder `ImportSources` already lists. Names are cleaned of `< > : " / \ | ? *`, control
characters, and trailing dots and spaces, with a fallback name `schematic` if nothing is left. On a conflict the
details panel opens a confirmation screen with Overwrite, Keep both and Cancel. Keep both picks the first free
` (n)` name. The file is written to a temporary file in the same folder and moved into place, so a failed write
never leaves half a file. Cloud versions are resolved through `SchematicCache` the same way Place resolves them.

### Conformance: a written file in the shared set
A test in the mod writes a fixed model (stairs, air, a modded block, a chest with items, data version, fixed name,
author and time) and compares it byte for byte with `src/conformance/files/written/capture.litematic`. The test
rewrites the file when `HEAVYMATICA_REGENERATE_WRITTEN=1` is set. The expected JSON is written by hand from the model, so a writer
bug can't define its own expectation. `generate.py` only clears top-level files, so it already leaves
`files/written/` alone, as it does `files/real/`. The conformance tests on both sides already walk subfolders, so they
read the file without changes.

## Risks / Trade-offs

- [Block entity data on remote servers is incomplete: chests, barrels and spawners arrive without contents] → The
  prompt warns on servers. In single player the integrated server's data is used.
- [Server tasks slow a capture with many block entities down] → Only sections that contain block entities go to
  the server, and the client tick never waits on them.
- [Litematica may reject a detail of our files] → Verify by loading a written file in Litematica during the in-game
  check (task). The metadata follows what Litematica writes.
- [A capture right at the limit (16.7 M cells) makes a large `int[]` and a large palette map] → 64 MiB for the
  indices is acceptable for a client, and the reader already holds the same arrays when loading such a file.
- [The world changes during a long capture] → Accepted. The box is consistent per section, and the player can
  capture again.
- [Shift + scroll conflicts with other mods (inventory or zoom mods)] → Only while capture mode is on with the
  remote held. The unbound key and the wheel segment are alternatives.

## Open Questions

- Whether to cap the per-tick budget lower on slow machines. The default is a constant for now; it can become a
  client setting if players report stutter.
