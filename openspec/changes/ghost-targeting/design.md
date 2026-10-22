# Design

## Context

For motivation see proposal.md, Why. For behaviour see `specs/client/ghost-targeting` and the
`client/placement-remote` delta.

The current state this builds on (after `world-comparison`, `placement-layers`, `material-swaps`,
`ghost-opacity-wheel` and `block-equivalents`; the last is being implemented while this is written, so names from
it are taken from its design and may still move):
- **What is drawn** is decided in `placement/GhostRenderer.render`. A LOCKED placement whose cells are ready and
  that fits the block budget draws through `GhostCells` with:
  - the visible layers from `Layers.visible(placement, PlacementSession.feetLayer(placement))`
  - the in-hand alpha (`GhostRenderer.inHand`, built per frame from the held and hotbar items through
    `WorldComparison.highlights`): full, dimmed or 0
  - the comparison mask (`ComparisonState` mask bit = `CellState.showsGhost()`, so UNKNOWN and MISSING)
  - `MistakeBoxes` for `boxed()` states in the visible layers, whatever is held
  Placements over the budget, or whose cells are not ready yet, are drawn as a mesh or an outline without any of
  this.
- **`WorldComparison`** answers per world position: `stateAt`, `expectedAt` (the swapped, rotated state),
  `finishAt`. Per planned block it keeps `accepts(planned)`: the item indices that count toward it, own item first,
  then the placing item, then `same`, then `finishFrom` (`block-equivalents` D4).
- **`remote/PlacementOverlay.comparisonLines`** explains a boxed position under `minecraft.hitResult`.
- **`RemoteInput`** already cancels `InputEvent.InteractionKeyMappingTriggered` for use-item while the remote is
  held. NeoForge fires the same event for pick block (`isPickBlock()`).
- **Pick block since 1.21.4** sends `ServerboundPickItemFromBlockPacket` with a position. The server reads its own
  world there, so on a ghost it picks air or the block behind.
- **`CarriedItems`** sums items over the inventory, the offhand and item-capability contents (shulker boxes,
  bundles), but does not keep where an item was found.
- Minecraft is not on the unit-test classpath, so anything worth testing is kept game-free.

## Goals / Non-Goals

**Goals:**
- One answer to "what is drawn here", shared by the renderer and the ray, so a target is never a cell the player
  can't see.
- Traversal, the drawn rule, target choice, the HUD's state line and the pick decision are game-free and
  unit-tested.
- Picking uses only packets a vanilla client may send, so it works on vanilla servers.
- Jade is optional at compile time and at run time.

**Non-Goals:**
- Taking items out of containers, or picking from chests.
- Targeting placements that are not LOCKED, or extra blocks.
- Changing vanilla's `hitResult`, outline or block interaction. Only the pick key is taken, and only for a ghost.

## Decisions

### D1. A new `targeting` package

Everything new lives in `io.github.dirnei.mc.heavymatica.targeting`, with a `package-info.java` like the other
packages:
- game-free: `GhostRay` (D2), `TargetRules` (D3), `TargetLines` (D6), `PickDecision` (D8)
- game-facing: `GhostTargeting` (D4), `TargetHud` (D6), `GhostPick` (D9)
- the Jade plugin in its own sub-package `targeting.jade` (D7), so no other class refers to Jade types

`placement` gains only small accessors on `WorldComparison` (local-position lookups and the planned-block index of
a position) and the shared in-hand filter (D3).

### D2. Voxel traversal: `GhostRay`

`GhostRay.cast(ox, oy, oz, dx, dy, dz, maxDistance, Cells cells)` walks the cells the ray passes, in order, with
the Amanatides-Woo DDA (integer cell steps, `tMax` / `tDelta` per axis). For each cell it asks the callback
`Cells.at(x, y, z, entryDistance)`, which answers `TARGET`, `BLOCKED` or `PASS`. The walk stops at the first
`TARGET` (returning the cell and its entry distance) or `BLOCKED` (returning no target), or after `maxDistance`.

The game side builds the answer from two predicates in this order, so the planned block wins over a real block in
the same cell:
1. `drawn(x, y, z)`: some placement draws a targetable cell there (D3) → `TARGET`
2. `blocks(x, y, z)`: the world block's outline shape (as `ClipContext.Block.OUTLINE`, fluids ignored) is hit by
   the ray segment inside the cell → `BLOCKED`
3. otherwise `PASS`

Cells that intersect the player's bounding box are `PASS` without asking, so the cell the player stands or
builds in never captures the ray.

Alternatives:
- Stepping in fixed increments along the ray. Rejected: misses corners and costs more.
- Using vanilla's `hitResult` as the only blocker (target only if nearer than it). Rejected: `hitResult` stops at
  the mistake block itself, and the same-cell rule above is simpler with one walk. The per-cell outline clip gives
  the same blocking as vanilla's pick.

### D3. The drawn rule and target choice: `TargetRules`

`TargetRules.drawn(Kind planned, CellState state, boolean inLayers, boolean inHandShows)`:
- not `inLayers`, or the planned kind is not a block (air or ignored) → false
- `state.showsGhost()` (UNKNOWN, MISSING) → `inHandShows`
- `state.boxed()` and not EXTRA (wrong block, orientation, water, needs finishing) → true; boxes ignore the in-hand
  filter, as they are drawn
- otherwise (MATCHING, IGNORED, EXTRA) → false

`inHandShows` is true when the in-hand filter is off, or its alpha for the planned block is above 0 (dimmed counts).
`GhostRenderer.inHand` moves into a small `placement/InHandFilter` that both the renderer and the targeting use,
built once per frame, so both read the same alpha.

Which placements are asked: LOCKED, in the player's dimension, and drawn through cells in the last frame.
`GhostRenderer` records the ids it drew through `GhostCells` (it already records `lastCells` for development), so
a placement over the block budget or still waiting for cells is not targetable, matching what is on screen.

`TargetRules.choose(List<Candidate>, selectedId)`: when several placements draw the same cell, the selected one
wins, then the earliest in the session's placement order. Nearest-first needs no rule: the DDA visits cells in
order.

Alternatives:
- Target any cell of a locked placement regardless of filters. Rejected by the user: targets must match what the
  player sees.
- Targeting extra (magenta) cells as air. Rejected: there is no planned block to name or pick. The overlay keeps
  explaining them through vanilla's crosshair.

### D4. `GhostTargeting`: one target per frame

`GhostTargeting.current()` returns `@Nullable GhostTarget(Placement placement, BlockPos pos, BlockState planned,
CellState state, @Nullable BlockState found, @Nullable Finish finish, int plannedIndex, double distance,
boolean inReach)`.
- It is computed lazily once per frame (cleared on `RenderFrameEvent.Pre`), so the HUD, the overlay and Jade share
  it. `fresh()` recomputes for the pick key.
- The ray length is `max(hudRange, reach)`, so the pick works with the HUD off or a short range.
- `inReach` compares the cell's entry distance with `player.blockInteractionRange()`, the distance vanilla's pick
  uses.
- `found` is the world block when it is not air.

Cost: up to ~100 cells for 32 blocks, a few array lookups per placement whose box the ray enters (the existing slab
test from `Placements.entry` skips the others), and one outline clip per non-air cell. That is far below one
comparison tick.

### D5. Config: `targeting` section

- `targeting.hudRange`: int, default 32, 0 to 128. 0 hides the HUD.
- `targeting.pickBlock`: boolean, default true. Off leaves pick block entirely to vanilla.

No separate HUD toggle: range 0 already is one. No Jade toggle: Jade's own config can hide its tooltip.

### D6. The HUD: `TargetHud` and `TargetLines`

- A GUI layer registered with `RegisterGuiLayersEvent` above the crosshair layer, like `PlacementOverlay`.
- It is centred under the crosshair, on a translucent background like vanilla's tooltips, at most five lines:
  1. the item icon (first of `accepts(plannedIndex)`, none when empty) and the block name
  2. `WorldComparison.details(planned)` (orientation properties and waterlogging), muted, left out when empty
  3. the state line
  4. the placement's name, when it is not selected
  5. "out of reach", muted, when `!inReach`
- Hidden when a screen is open, the GUI is hidden (F1), the distance is above `hudRange`, or the Jade plugin is
  active (D7).
- `TargetLines.state(CellState, Finish)` is game-free: it maps the state to a translation key and a colour (red
  `MISTAKE_RED`, orange, cyan, yellow, muted for UNKNOWN, plain for MISSING), reusing the overlay's colour
  constants. The game side fills in the found block's name, its orientation details or the step. Wrong water says
  "needs water" or "must not be waterlogged" from the planned waterlogging.

### D7. Jade plugin, optional

- Build: `src/mod/heavymatica/build.gradle` adds an `exclusiveContent` Modrinth maven repository
  (`https://api.modrinth.com/maven`, group `maven.modrinth`) and `compileOnly "maven.modrinth:jade:${jade_version}"`;
  `jade_version` goes into the subproject's `gradle.properties`. A `-PwithJade` switch adds it as `localRuntime`
  for dev runs, so normal runs stay without Jade.
- `neoforge.mods.toml` declares `jade` as `type="optional"`, `side="CLIENT"`.
- `targeting.jade.HeavymaticaJadePlugin` is annotated `@WailaPlugin` and implements `IWailaPlugin`. Jade finds it
  by annotation scan, and nothing else references it, so without Jade the class is never loaded.
- `registerClient(IWailaClientRegistration)`:
  - `addRayTraceCallback`: when `GhostTargeting.current()` is within `hudRange` and nearer than Jade's own hit (or
    at the same position, the mistake case), return `registration.blockAccessor()` built for the planned state at
    the target position, with a hit result on that cell. Otherwise return Jade's accessor unchanged.
  - `registerBlockComponent` of a provider on `Block.class` that adds the state line from `TargetLines`, only when
    the accessor's position and state are the current target's.
  - Sets a static `active` flag. `TargetHud` hides only when that flag is set, so a plugin that failed to load
    leaves the own HUD on.
- The exact API of the Jade build for 26.1 (callback signature, accessor builder methods, whether a block accessor
  without a block entity asks the server for data) is verified against the artifact first (task 6.1). If it does
  not fit, the plugin, the build lines and the optional dependency are dropped and the HUD always shows; the spec
  requirement "Jade shows the ghost target" is then removed from this change before archiving.
- Verified against `maven.modrinth:jade:26.1.11+neoforge` (Jade 26.1.11 for NeoForge 26.1 to 26.1.2, sources jar):
  - `@WailaPlugin(String value() default "")`, `IWailaPlugin.registerClient(IWailaClientRegistration)`.
  - `addRayTraceCallback(JadeRayTraceCallback)` with `Accessor<?> onRayTrace(HitResult hitResult, Accessor<?>
    accessor, Accessor<?> originalAccessor)`. Callbacks run for every hit, MISS included. Contrary to the javadoc, a
    `null` return makes Jade use an empty accessor, so the callback returns `accessor` unchanged when there is no
    ghost.
  - `blockAccessor()` returns a `BlockAccessor.Builder` preset with level, player, server connection, server data and
    show details; it has `blockState`, `blockEntity(BlockEntity)`, `hit(BlockHitResult)`, `serverConnected`,
    `serverData`, `requireVerification` and `build()`.
  - `registerBlockComponent(IComponentProvider<BlockAccessor>, Class<? extends Block>)`; `IBlockComponentProvider`
    needs `appendTooltip(ITooltip, BlockAccessor, IPluginConfig)` and `Identifier getUid()`.
  - Server data: with no block entity Jade only asks the server for providers registered on the block itself, and only
    when the accessor says the server is connected. The ghost accessor is built with `serverConnected(false)` and no
    server data, so a ghost never asks the server.

Alternatives:
- Reflection instead of `compileOnly`. Rejected: brittle and unreadable; `compileOnly` plus annotation discovery
  already keeps Jade optional.
- Show both Jade and the HUD. Rejected by the user: never two tooltips.

### D8. The pick decision: `PickDecision`

Game-free over item keys. Input:
- `candidates`: per candidate item, in order, its hotbar slots, its main-inventory slots (9 to 35), and the name of
  the first carried container holding it (or null)
- `creative`, `selectedSlot`, `suitableSlot` (vanilla's `Inventory.getSuitableHotbarSlot()`)

Output, a sealed `Action`:
1. no candidates → `Vanilla`
2. the first candidate with a hotbar slot or main slot, in candidate order:
   - in the hotbar → `Select(slot)`, the selected slot if it holds it, else the lowest
   - else → `Swap(inventorySlot, suitableSlot)` (lowest main slot), then select
3. creative → `CreativeAdd(candidate 0, suitableSlot)`
4. the first candidate with a container → `InContainer(candidate, containerName)`
5. → `NotCarried(candidate 0)`

The preconditions (setting on, not sneaking, a target, in reach) are checked before, in `GhostPick`, and also
give `Vanilla`. Candidate order comes from `WorldComparison.accepts(plannedIndex)` mapped to items, so the order is
the one `block-equivalents` defines and is not repeated here. A planned block no item places has no candidates.

The order is item-first: an own item in the main inventory beats a placing item in the hotbar, as confirmed.

### D9. Executing the pick: `GhostPick`

Not on `InputEvent.InteractionKeyMappingTriggered`: in 26.1 vanilla's `pickBlockOrEntity` fires that event only
when `hitResult` is not a MISS, so a ghost with nothing real behind it within reach would never reach the handler. Instead `GhostPick` runs on `ClientTickEvent.Pre`, which fires before
`handleKeybinds`: when the preconditions hold and `PickDecision` gives anything but `Vanilla`, it takes the pick
key's clicks with `keyPickItem.consumeClick()`, so vanilla never sees them and sends no pick-from-block packet. Clicks
for a `Vanilla` decision are left alone. Then:
- `Select`: `inventory.setSelectedSlot(slot)`; the client tells the server on its next tick, as with scrolling.
- `Swap`: `gameMode.handleContainerInput(player.inventoryMenu.containerId, inventorySlot, hotbarSlot,
  ContainerInput.SWAP, player)` (26.1's name for `handleInventoryMouseClick` / `ClickType`; main-inventory slots 9 to
  35 keep their index in the player's inventory menu), then select the hotbar slot. This is the click a player makes
  with a number key in the inventory screen, so the server checks it as usual.
- `CreativeAdd`: slot from `inventory.getSuitableHotbarSlot()`; a stack already there moves to
  `inventory.getFreeSlot()` (also sent), then a stack of the item goes into the slot and is sent with
  `gameMode.handleCreativeModeItemAdd(stack, 36 + slot)`, then selected, as vanilla's creative pick did before 1.21.4.
  Creative is `player.hasInfiniteMaterials()`, which `handleCreativeModeItemAdd` checks too.
- `InContainer` / `NotCarried`: `player.sendOverlayMessage(message)` (26.1's action-bar call).
- Containers are only looked into once a click is taken and the decision without them is `NotCarried`.

The inventory is scanned at click time (hotbar, slots 9 to 35, and item-capability contents through a helper
extracted from `CarriedItems`), not read from the cached counts, because the slots are needed and the cache can
lag.

Alternatives:
- A server mod packet. Rejected: the core works client-only on any server.
- Letting vanilla pick and fixing up afterwards. Rejected: the server answers with the block behind the ghost.

### D10. Overlay

`PlacementOverlay.comparisonLines` takes its position from `GhostTargeting.current()` when the target's placement is
the selected one, and from `minecraft.hitResult` otherwise (extra blocks, or a target in another placement). The
expected / found / step lines stay as they are.

### D11. Development hooks

`DevScreens` gains:
- `logTarget`: logs the current ghost target (placement, position, planned state, state, found, distance, in
  reach) or "none"
- `logHud`: logs the HUD's lines as text, like `logOverlay`
- `logInventory`: logs the selected slot and the hotbar and main-inventory items

The pick itself is driven with the existing `press=key.pickItem`, so it runs through vanilla's key handling and
the real event.

## Risks / Trade-offs

- [Jade's 26.1 API differs from the 1.21 API this design assumes] → verified first (task 6.1); the Jade part is
  isolated and can be dropped without touching anything else, and the HUD then always shows.
- [Anti-cheat plugins may flag a SWAP click without an open inventory screen] → it is the packet vanilla sends for
  number-key swaps in the inventory; sneak + middle click and `targeting.pickBlock=false` are the escape hatches.
- [The selected-slot change and the swap race the server's own state] → they are the same packets vanilla sends;
  the server stays authoritative and corrects the client.
- [Targets lag the renderer by a frame, since "drawn last frame" is read] → one frame, not visible.
- [Code from `block-equivalents` is still in flux (`accepts`, `InHandFilter` source, `finishAt`)] → implement after
  it is applied; tasks name the accessors to check.
- [The overlay can disagree with vanilla's block outline: the outline is on the wall, the overlay talks about the
  ghost in front] → intended: the HUD makes the ghost target visible.
- [Long rays through large placements cost per frame] → bounded by 128 blocks and by box tests; measured in
  `runClient` with the frame time already logged.

## Migration Plan

Nothing is saved. The client config gains the `targeting` section; NeoForge adds it with defaults. Archive after
`block-equivalents` (`material-swaps` → `ghost-opacity-wheel` → `block-equivalents` → `ghost-targeting`): the
`client/placement-remote` delta modifies "Finishing in the overlay", which only exists once `block-equivalents` is
archived. If the Jade part is dropped (D7), remove its requirement from the delta before archiving.

## Open Questions

- The HUD's exact placement and background (directly under the crosshair or a bit lower, tooltip frame or plain
  shadow). Tuned in `runClient`.
- The exact wording of the action-bar messages.
