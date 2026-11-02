## Context

- In single player the client and the world's server run in one process: `Minecraft.getSingleplayerServer()` returns the
  integrated server, whose `ServerLevel`s accept `setBlock` on the server thread. A LAN guest has no such server.
- `placement/GhostView` already resolves every position of a placement to the state the ghost shows (resolver, swaps,
  rotation, mirror; unknown blocks flagged), and `SchematicModel.blockEntities()` holds the schematic's block-entity NBT.
- Capture already paces world work by 16³ sections within a budget per step (`capture/CaptureSnapshot`), with progress in
  the overlay.

## Goals / Non-Goals

**Goals:**
- Exact paste of states and block entities, without physics running mid-paste.
- No frame drops on large pastes; a reliable undo of the last paste.

**Non-Goals:**
- Multiplayer paste (commands or a server mod); the machines addon covers servers later.
- Entities, biomes, and upgrading old block-entity data (old NBT is loaded as is; what the game can't read is dropped).
- Paste history beyond the last paste.

## Decisions

### D1: Build the plan on the client, apply it on the integrated server thread

On Paste the client builds an immutable plan from the placement: world positions grouped by 16³ section, each with the
target state and its block-entity NBT moved to the world position. The plan is handed to a small job runner that runs on
the integrated server's tick (`ServerTickEvent.Post`, which fires in-process for the integrated server even though the
mod is client-only). Only the job touches the `ServerLevel`; the client reads progress through an atomic counter for the
overlay.

- *Alternative: `/setblock` and `/fill` commands.* Rejected: slow, no block-entity data in a convenient form, chat noise,
  and pointless when the server is in our process.

### D2: Place without updates, clear before replacing

Each position: skip when the world already has the target state and the plan has no block entity there (and in Only into
air, skip when the world block isn't air or replaceable; in Skip air, skip planned air). Otherwise record the undo entry
(D4), clear a replaced container with `Clearable.tryClear` so it drops nothing, then `setBlock` with
`UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS` (no neighbour or shape updates, no drops). If the plan has
block-entity NBT, load it into the new block entity with the level's registry access and mark it changed; NBT the game
can't read is logged at debug and skipped, leaving the block in place.

### D3: Pace by time, section by section

The job runs whole sections until about 10 ms of the tick are used (at least one section per tick), loading the chunk of
the section when needed. Sections go in y-then-z-then-x order so chunk loads stay local. The overlay shows "Pasting
N %". Light updates follow normally through the level.

- *Alternative: a fixed cell budget.* Rejected: cost per cell varies a lot (block entities, chunk loads, lighting).

### D4: Undo as recorded previous states

Before changing a position the job records its previous state and, if it has a block entity, its full NBT
(`saveWithFullMetadata`). The record for the last paste is kept in memory (a few MiB for very large pastes). Undo runs
the same job in reverse order with the recorded states and NBT, then drops the record. A new paste replaces the record;
leaving the world (client logout or server stopping) drops it and cancels a running job after its current section.

### D5: Availability

Paste is available when `minecraft.hasSingleplayerServer()`, the local player is in creative mode, and no job runs. The
wheel and the job both check, and the job checks the server-side player's game mode again when it starts.

### D6: Wheel

`RadialMenu` gets `Entry.PASTE` after Materials on ANCHORED and LOCKED (enabled unless a job runs), `Page.PASTE` with
`PASTE_REPLACE_ALL`, `PASTE_ONLY_AIR` and `PASTE_SKIP_AIR`, and `Entry.UNDO_PASTE` as the last segment of every state
page while an undo is available. `Selection` carries paste availability, running and undoable. Commands `PASTE` (with
the mode) and `UNDO_PASTE`.

## Risks / Trade-offs

- [No neighbour updates leave some blocks needing an update later (e.g. a lamp next to a powered wire)] → That is the
  plan's state, and the first natural update corrects it; the same behaviour Litematica and structure blocks have.
- [Chunk loading during paste can stutter on slow disks] → Time budget per tick; one section at a time.
- [Old block-entity NBT may not load in a newer game] → Skipped per block with a debug log; the block itself stays.
- [Undo memory for huge Replace-all pastes] → One recorded state per changed position (~16 bytes) plus NBT only where
  block entities were; acceptable for single player.

## Migration Plan

Client only; nothing changes outside creative single player.
