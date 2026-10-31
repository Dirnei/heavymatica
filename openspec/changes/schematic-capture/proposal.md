# Proposal

## Why

Heavymatica can only use schematics made by other tools. A player who built something in the game has to install
Litematica or WorldEdit to save it before it can reach the library or the cloud. Capturing an area with the remote
makes Heavymatica complete on its own. Exporting a library entry as a file lets the player use it in other tools
again.

## What Changes

- **Capture mode on the remote.** The wheel gets a Capture segment that starts capture mode:
  - Right-click a block for corner 1, right-click another for corner 2. The box shows as an outline with its size.
  - The last-set corner is active. The arrows move it relative to the camera and numpad + / - move it up and down,
    10 at a time with Shift. Raising the top therefore needs no nerdpole.
  - Shift + scroll, an unbound key or the wheel's Swap corner make the other corner active. With both corners set,
    a right-click moves the active corner to the clicked block.
  - While capturing, the wheel shows Capture, Swap corner, Reset and Cancel only. The remote's keys work on the box,
    not on placements.
  - The box is kept in memory only and is gone after leaving the world or changing dimension.
- **Capturing.** Capture asks for a name (prefilled with the date and time) and saves the area as a local library
  entry in `.litematic` format:
  - Blocks are saved exactly as they are; air stays `minecraft:air`.
  - Block entities are saved with whatever data the client has. On a server, the prompt warns that container
    contents may be missing.
  - Entities and placement ghosts are not captured.
  - A box that reaches into chunks the client hasn't loaded, or crosses the format limits, is refused with the
    reason.
  - A large capture never freezes the game; the overlay shows its progress.
  - The entry can then be uploaded like any local entry: as a new schematic or a new version, with a thumbnail.
- **A `.litematic` writer** in the shared formats code. Its output passes the existing readers on both sides and is
  added to the conformance set.
- **Export.** The library's entry details get Export, for local entries and for cloud versions (from the cache, or
  downloaded first). Export writes the stored file unchanged to `.minecraft/schematics/<name>.<ext>`, the folder
  Litematica uses and the import list already reads. An existing file is never overwritten without asking.

Out of scope:
- ignoring air on a placement (later: a placement option next to material swaps)
- converting between formats on export
- capturing entities, or a server side that fills in block entity data the client doesn't have
- several capture boxes, saved boxes, or moving the whole box

## Capabilities

### New Capabilities
- `client/schematic-capture`: capture mode on the remote, the capture box and its controls, and turning the box into
  a local library entry.

### Modified Capabilities
- `schematic-formats`:
  - New: "Litematica writer" and "Written files read back the same".
- `client/schematic-library`:
  - New: "Export an entry as a file".
- `client/radial-menu`:
  - New: "Capture segment" and "Capture wheel".

## Impact

- **Mod (`src/mod/heavymatica`):**
  - `formats`: a game-independent NBT writer and a `.litematic` writer for a `SchematicModel`
  - a new `capture` package: the capture box state, the snapshot of the world spread over ticks, and the box
    rendering
  - `remote`: capture mode in `RemoteInput`, the new wheel entries in `RadialMenu`, a swap-corner key in
    `RemoteKeys`, and Shift + scroll handled while capturing
  - `library`: `LocalLibrary` can add an entry from bytes; an exporter writes entries into `schematics/`
  - `ui`: Export in the details panel, the capture name prompt, an overwrite confirmation, and the capture lines in
    the placement overlay
- **Conformance (`src/conformance`):** a file written by the mod under `files/written/` with its expected result. The
  generator leaves that folder alone. The cloud's conformance tests read it like any other file.
- **Cloud and contract:** no changes.
- **Docs:** `ROADMAP.md` and `README.md` mention capture and export; the "ignore air" placement option is added to
  the roadmap as later work.
- **No new dependencies.**
