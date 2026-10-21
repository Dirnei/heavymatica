# Design

## Context

For motivation see proposal.md, Why. For behaviour see `specs/client/placement-remote`, `specs/client/schematic-placement` and the `specs/client/schematic-library` delta.

The current state that shapes this design:
- The core mod is client-only (`@Mod(dist = Dist.CLIENT)` for the client side, `displayTest="IGNORE_ALL_VERSION"`). It must keep joining vanilla servers, so it registers no items, blocks or networked data.
- `HeavymaticaClient` already registers a key category and the `OPEN_LIBRARY` key, and consumes clicks in `ClientTickEvent.Post`.
- `Library.file(schematicId, version, sha256)` returns a cached or downloaded version file. `SchematicReader` turns it into the game-free `SchematicModel` (palette strings plus an index array).
- `thumbnail/SchematicView` (a `BlockAndTintGetter` over a model) and `ThumbnailRenderer` already tessellate block models with `ModelBlockRenderer` into our own buffers on NeoForge 26.1.2's GPU API. Ghost rendering builds on the same path.
- Client config lives in `HeavymaticaConfig` (`ModConfigSpec`, client type). Files go under `HeavymaticaClient.dataDirectory()`. Jackson is bundled.
- Tests are plain JUnit next to the game-free code (`formats`, `cache`, `account`, `importing`). Anything that needs a running game is checked through `DevScreens` and `runClient`.

## Goals / Non-Goals

**Goals:**
- Keep the interaction logic (states, selection, movement, rotation) game-free and unit-tested. Game code is a thin adapter around it.
- Rotating and mirroring never drift. Four clockwise rotations return a placement to exactly where it was.
- Ghosts never cost frame time while a mesh is being built. Building happens off the render thread.

**Non-Goals:**
- Sorting translucent ghost faces per frame. Ghost faces are drawn in mesh order. Small artefacts where ghost faces overlap are acceptable.
- Hiding ghost blocks that already match the world. That belongs to the hand-build helper (roadmap step 4).
- Any server-side or networked behaviour, including the `heavymatica_machines` Remote item itself.

## Decisions

### D1. Packages

```
io.github.dirnei.mc.heavymatica
  placement/
    Placement.java            record: id, source (cloud or local ref), pivot, rotation, mirror, dimension, state
    PlacementSource.java      sealed: Cloud(schematicId, version, sha256) | Local(sha256)
    PlacementTransform.java   game-free math: bounds, rotate/mirror around pivot, block position mapping
    PlacementController.java  game-free state machine: inputs in, changes out (D4)
    Placements.java           the world's placements, selection, change listeners
    PlacementStore.java       JSON load/save per world or server (D8)
    GhostMeshes.java          off-thread mesh building and GPU upload, keyed by (sha256, rotation, mirror) (D6)
    GhostRenderer.java        RenderLevelStageEvent hook, outlines, budget (D7)
  remote/
    Remotes.java              "is this a remote" (D2)
    RemoteKeys.java           key mappings and the HOLDING_REMOTE conflict context (D3)
    RemoteInput.java          event wiring: right-click interception, key repeat, raycasts -> controller
    PlacementOverlay.java     GUI layer (D9)
```

`placement` holds the model and rendering, and `remote` holds input and HUD, which matches the two capabilities. The game-free classes (`Placement*` apart from the rendering ones and `PlacementSession`) have no `net.minecraft` imports. Rotation and mirror are small enums of their own (`Placement.Turn`, `Placement.Flip`) in the order of vanilla's `Rotation` and `Mirror`, so unit tests never load game classes. `GhostView` maps them to the vanilla enums by ordinal.

### D2. Recognising a remote

`Remotes.isRemote(ItemStack)` returns true when the stack's item equals the configured item (`remote.item`, default `minecraft:stick`, validated as an existing item ID) or `stack.is(TagKey(ITEM, heavymatica:remotes))`. The check runs on the main hand only.

Item tags are sent by the server. On a vanilla server, or one without the machines addon, the tag is simply empty and only the configured item works. That's intended: the tag is how the addon's real Remote plugs in without the core depending on it. The core ships an empty `data/heavymatica/tags/item/remotes.json` so the tag exists in singleplayer.

Alternatives:
- A registered item. Rejected: it would break vanilla servers.
- Matching items by custom name or a data component. Rejected: it can't be set without commands or an anvil, and it's fragile.

### D3. Keys in their own conflict context

`RemoteKeys.HOLDING_REMOTE` implements `IKeyConflictContext`:
- `isActive()`: `minecraft.screen == null && Remotes.isRemote(player.getMainHandItem())`.
- `conflicts(other)`: `other == this`. The controls screen still marks any two bindings on the same key as duplicates (26.1's `KeyMapping.same` falls back to key equality), so the context only limits when the keys act.

The mappings, all in the Heavymatica category:

```
key.heavymatica.move_forward   UP arrow       key.heavymatica.rotate   R  (Shift: counter-clockwise)
key.heavymatica.move_back      DOWN arrow     key.heavymatica.mirror   M
key.heavymatica.move_left      LEFT arrow     key.heavymatica.remove   DELETE
key.heavymatica.move_right     RIGHT arrow
key.heavymatica.move_up        KP_ADD
key.heavymatica.move_down      KP_SUBTRACT
```

Shift is read when the key is pressed (`Minecraft.hasShiftDown()`) rather than registered as a `KeyModifier`. The shifted variants then don't need their own bindings, and a rebinding keeps working with Shift.

Numpad keys are the defaults because they're at the same place on every layout. The "+" key of the main block sits on different physical keys in German and US layouts. Players without a numpad rebind; the overlay shows whatever they chose (D9).

### D4. A game-free controller

`PlacementController` gets inputs and changes `Placements`:

```
inputs:  use(sneaking, Ray)  move(Dir4 relative, shift)  vertical(+1|-1, shift)
         rotate(shift)  mirror()  remove(nowMillis)  following(Target|null)  start(source)
ports:   Ray = eye position + direction, Target = hit block + face, both plain records
```

Right-click resolution (`use`):

```
selected FOLLOWING?           -> anchor it                       (any aim, sneaking ignored)
sneaking?
   selected ANCHORED?         -> release to FOLLOWING
   otherwise                  -> nothing
first ghost box hit by Ray?   -> select it (nothing if already selected)
no ghost hit                  -> request "open library"
```

The ghost hit test runs ray-vs-AABB (slab method) over the placements in the current dimension, up to 64 blocks. Its entry distance is clamped to 0 when the eye is inside a box, and ties go to the smaller volume. Pure math, unit-tested.

`RemoteInput` cancels `InputEvent.InteractionKeyMappingTriggered` when it is the use key and a remote is held, with `setSwingHand(false)`. Cancelling it there means vanilla never sends the use-item or use-on packet. Attack and pick-block events are left alone.

Key repeat: vanilla key mappings only count presses. `RemoteInput` keeps its own repeat in `ClientTickEvent.Post` for the move and up/down keys: the first move on press, then after 6 ticks every 2 ticks while `isDown()`. Rotate, mirror and remove don't repeat.

### D5. Pivot-based transform

A placement stores a **doubled pivot** `(px2, y, pz2)`: twice the centre of its footprint as integers, plus the bottom Y. It doesn't store a min corner. Bounds are derived:

```
size after rotation:  (sx', sz') = rotation in {90, 270} ? (sz, sx) : (sx, sz)
min x = floorDiv(px2 - (sx' - 1), 2)       min z = floorDiv(pz2 - (sz' - 1), 2)
```

Rotating and mirroring change only `rotation` and `mirror`. The pivot stays put, so the footprint turns around its centre. Four rotations land on identical bounds because nothing is re-rounded into the stored state. The spec's "same centre (rounded down)" comes out of the `floorDiv`.

Mapping a schematic-local block `(x, y, z)` to world space mirrors first, then rotates, inside the local box, then offsets by `min`. Block states go through vanilla `BlockState.mirror(Mirror)` then `rotate(Rotation)`, the same order Structure templates use. The mirror values are vanilla's `NONE`, `LEFT_RIGHT` and `FRONT_BACK`, applied in schematic-local space.

Movement:
- An arrow key takes the player's yaw snapped with `Direction.fromYRot`. Forward is that direction, back its opposite, left and right are its counter-clockwise and clockwise turns.
- The pivot moves by 2 x step on x/z (because it's doubled) and y by step.

Following: the target block is `hit.getBlockPos().relative(hit.getDirection())`. The pivot becomes `(2 * tx + 1, ty + followOffset, 2 * tz + 1)`, which centres the footprint on that block. `followOffset` is kept on the placement until it is anchored, then reset to 0. The ray is a client `Level.clip` from the camera, 64 blocks, `ClipContext.Block.OUTLINE`, fluids ignored. It's not the vanilla hit result, which only reaches about 5 blocks.

Alternative: store the min corner and recompute it on rotation. Rejected: odd/even sizes make it creep half a block per turn.

### D6. Ghost meshes

A mesh is keyed by `(sha256, rotation, mirror)`. Translation is a pose offset at draw time, so moving costs nothing. Rotating or mirroring needs a new mesh because block states change.

Building happens on `Util.backgroundExecutor()`:
1. Wrap the model in a transformed view, a `SchematicView` variant that maps positions and states through D5.
2. Skip air, unknown blocks and fully hidden blocks (all six neighbours are full, opaque cubes).
3. Tessellate with `ModelBlockRenderer` into a `ByteBufferBuilder` using the translucent layer's vertex format.
4. Hand the built vertices to the render thread for upload into a `GpuBuffer`.

While a new mesh is building, the old one keeps drawing, or the outline if there's no old one. Meshes are reference-counted by the placements using them. They're freed when no placement uses them, on disconnect, and on resource reload (models may change).

Alternative: render each ghost block every frame like a block entity. Rejected: it costs too much per frame for anything bigger than a hut.

### D7. Drawing, outlines and budget

`GhostRenderer` hooks `RenderLevelStageEvent` after the translucent stage:
- Each mesh in the current dimension is drawn with the translucent pipeline, a constant alpha (config `ghost.opacity`, default 0.45) and depth test on, depth write off, so real blocks hide ghosts behind them.
- Outlines: the selected placement is yellow while FOLLOWING and green while ANCHORED. Unselected placements are faint white. Unavailable ones are drawn as red boxes.

The budget (config `ghost.blockBudget`, default 300 000 visible blocks) is counted across meshes in the order placements were last selected or created. A mesh that would cross it isn't drawn, and its placement shows its outline only. The selected placement is counted first, so it always gets its mesh if it fits on its own.

### D8. Persistence

The file is `dataDirectory()/placements/<player uuid>/<world key>.json`.

The world key:
- Singleplayer: `sp-` plus the save folder name.
- Multiplayer: `mp-` plus `host_port`, lower-case, with characters outside `[a-z0-9._-]` replaced by `_`.
- Realms: `realms-` plus the Realm's name, sanitized the same way. The connection does not expose the Realm's id.

The content is `{ "version": 1, "placements": [ { id, source, pivot, rotation, mirror, dimension } ] }`.

Only ANCHORED placements are written. The file is written atomically (temp file then move), at most once every 2 seconds after a change and once on disconnect. On join, the placements load unselected, and each version loads through `Library.file`. A failure marks the placement unavailable (outline, overlay message). `Library.onChange` and the account's cloud state re-trigger loading of unavailable ones.

Placements are keyed by player as well as world because two Minecraft accounts on one machine are different players.

### D9. Overlay

`PlacementOverlay` is registered with `RegisterGuiLayersEvent` above the hotbar layer. It's drawn only when `HOLDING_REMOTE.isActive()`.
- Config `overlay.side` sets the side (`LEFT` or `RIGHT`, default `LEFT`). The panel is vertically centred, 4 px from the edge, with a translucent dark background.
- Config `overlay.compact` (default false) shows only the name and badge.
- Hints come from a table of `(state, mapping, translation key)`, filtered by the selected placement's state. Key names come from `KeyMapping.getTranslatedKeyMessage()`, so a rebinding shows right away.
- The pending "press again to remove" note and the "version unavailable" note replace the hints while they apply.

Layout:

```
+-----------------------------+
| Castle Gate  v3             |
| [ ANCHORED ]                |
| Pos   128, 64, -40          |
| Rot   90   Mirror  off      |
|-----------------------------|
| <key>  hint                 |
+-----------------------------+
```

### D10. Place from the library

`DetailsPanel` gets a Place button for the chosen version. It's disabled with a reason when `Library` says the version is uncached while offline.

Clicking it:
1. Runs `Library.file(...)` then `SchematicReader` off-thread.
2. Back on the render thread, closes the screen and calls `PlacementController.start(source)`.
3. If no remote is held, shows the "Hold a <item> to place" message on the action bar.

Local entries use the same path with `PlacementSource.Local`.

## Risks / Trade-offs

- [NeoForge 26.1.2's GPU API is new and not well documented] → `ThumbnailRenderer` already proves the tessellation and buffer path. The first task is a spike that draws a single translucent mesh in the world before the rest is built on it.
- [Litematica uses a stick as its default tool item too] → The remote item is configurable, and the overlay names it. A player with both mods picks a different item for one of them.
- [Taking right-click blocks doors, chests and levers while the remote is held] → Intended: the remote is a tool. Hotbar-switching away restores normal behaviour, and the overlay makes it obvious that the remote is active.
- [Translucent ghost faces aren't sorted] → Accepted for now (Non-Goals). If artefacts are bad in practice, sort per section when the camera moves a block.
- [Large schematics take seconds to mesh] → Building runs off-thread, and the outline shows until the mesh is ready. The budget keeps huge builds from tanking frame time.
- [Item tags come from the server] → Documented in D2. Only the configured item is guaranteed. That's fine, because the tag exists for the addon, which is server-side anyway.

## Migration Plan

None. This is new client data in a new directory. Removing the mod leaves `heavymatica/placements/` behind, and it has no effect.

## Open Questions

- Exact colours and the default opacity. They will be tuned in game; the config keys are fixed by D7.
