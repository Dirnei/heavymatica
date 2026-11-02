## Context

- `targeting/GhostTargeting` already resolves the crosshair to a `GhostTarget`: placement, position, planned state
  after swaps, rotation and mirror, cell state, reach and planned index. `targeting/GhostPick` shows how to act on it
  with vanilla-only packets.
- `placement/WorldComparison` judges a world block against the plan through `CellKeys.found(state)` and
  `CellState.classify(expected, found)`, including the equivalents table, amounts, slab halves and finishes.
- `remote/RemoteInput` already cancels `InputEvent.InteractionKeyMappingTriggered` for the use key while the remote is
  held. That is the hook for any right-click we want to take over or refuse before vanilla sends a packet.
- On a vanilla server, `ServerGamePacketListenerImpl.handleUseItemOn` places a block from a `ServerboundUseItemOnPacket`
  when the hit location lies within the clicked block's cube (±1.0000001 from its centre) and the block is within the
  player's interaction range. It does not check line of sight, and it accepts a click on an air position: air is
  replaceable, so the block goes into the clicked position itself. Orientation comes from the hit (face, location) and
  from the player's rotation as the server last received it.

## Goals / Non-Goals

**Goals:**
- One generic way to get any block's planned placement state, vanilla and modded, without a per-block table.
- Vanilla client packets only; the same code in single player and on servers.
- Restriction judges a click exactly as the comparison will later judge the block.

**Non-Goals:**
- Properties a placement never sets (repeater delay, note pitch, stair shape, connections); the comparison already
  covers them as "needs finishing" or as matching.
- Two-item blocks' content step (planting into a placed pot). The click on the base is a block interaction, not a
  placement; this can follow once two-item blocks have landed.
- Carpet's accurate block placement protocol and a server-mod placement request; both can come later behind the same
  search.
- Anti-cheat compatibility.

## Decisions

### D1: Find the click by simulating vanilla placement on the client

For an Easy Place click, try a small set of candidate clicks and ask the held item's own placement logic which state each
would give: `BlockItem.getPlacementState(new BlockPlaceContext(...))` on the client level, with the client player's yaw and
pitch temporarily set to the candidate rotation. The first candidate whose state matches the planned state on every
property the comparison checks for orientation (and the slab type or amount for a "not enough yet" target) wins.

Candidates, in order of preference (the player's own rotation first, so most clicks send no rotation):
- clicked position: the target position itself, which is air or replaceable for a missing target, or holds the partial
  block (slab, candles) for a not-enough-yet target
- clicked face: all six
- hit location: the face's centre, and points near its upper and lower edge (for half and slab type)
- rotation: the player's own, then yaw 0/90/180/270 with pitch 0, then pitch ±90 with the player's yaw

That is at most a few hundred cheap calls per click, only on a click.

- *Alternative: a per-block table of which property comes from what* (as Litematica's older easy place did). Rejected:
  large, incomplete for modded blocks, and wrong whenever a block's placement logic changes.

### D2: Send the rotation, then the click, then the real rotation

When the winning candidate needs a rotation other than the player's own, send `ServerboundMovePlayerPacket.Rot` with it,
then `gameMode.useItemOn(player, hand, hit)` while the client player temporarily has that rotation, so the client's own
prediction places the same state. Then restore the player's rotation and send it again. The camera never moves because
the rotation is restored within the same call on the game thread before the next frame. `gameMode.useItemOn` keeps
vanilla's sequence numbers, swing and item use.

- *Alternative: only use the hit vector, never rotate.* Rejected: stairs, observers, pistons and most furniture take
  their facing from the player.

### D3: The test comes first

Task 1 is a spike on `runServer` that sends the D2 packets for stairs (all facings and halves), logs on every axis, top
slabs, wall torches and observers and checks the result and the server log for movement or interaction warnings. If the
server refuses or rubber-bands, the design is revisited before the rest is built.

**Spike result (`runServer`, NeoForge 26.1.2.116 dedicated server, offline mode, creative, flat world):** each case put
the item in the selected slot (`handleCreativeModeItemAdd`), then sent `ServerboundMovePlayerPacket.Rot` with the case's
rotation, called `gameMode.useItemOn` on an air position next to the player with the client player turned the same way,
restored the client rotation and sent it again. The planned state was taken from `BlockItem.getPlacementState` on the
client with the same click and rotation; the arrived state was read from the client level 2 seconds later, after the
server's block updates. All 15 cases arrived as planned:

| Case | Click | Rotation (yaw, pitch) | Arrived |
|---|---|---|---|
| oak stairs ×4, bottom | UP face, hit on top | 180 / -90 / 0 / 90, 0 | facing north / east / south / west, half bottom |
| oak stairs north, south, top | DOWN face | 180 / 0, 0 | facing north / south, half top |
| oak stairs east, west, top | NORTH / SOUTH face, hit y 0.8 | -90 / 90, 0 | facing east / west, half top |
| oak log ×3 | EAST / UP / SOUTH face | 0, 0 | axis x / y / z |
| oak slab | DOWN face | 0, 0 | type top |
| torch, stone to the west | EAST face, hit centre | 90, 0 | wall_torch facing east |
| observer | UP face | 0, 90 / 0, -90 | facing down / facing up |

No server-log warnings (no "moved wrongly", "moved too quickly" or out-of-range interaction), no position correction
(the player stayed at its position), and the client rotation was the player's own again after every case. Placing on an
air position with no line of sight to a supporting face is accepted. The design goes ahead unchanged.

### D4: One use-key hook decides Easy Place, restriction or vanilla

A new `build` package owns a listener on `InputEvent.InteractionKeyMappingTriggered` (use key, main hand), registered after
`RemoteInput` so the remote keeps priority. It runs:
1. holding the remote → not ours (RemoteInput handles it);
2. Easy Place click (spec conditions) → cancel the event, run D1/D2 or show the "can't place" message;
3. restriction on and the held item is a `BlockItem` → predict the click from vanilla's own hit result (the same
   `BlockPlaceContext` simulation with the real rotation), and if the predicted position is restricted and the
   predicted state judges as refused, cancel the event (no swing, no packet) and show the message;
4. otherwise leave the event alone.

Vanilla repeats the use key every 4 ticks while it is held, so Easy Place repeats with no extra code.

### D5: Judge with the comparison's own rules

Restriction builds a `Found` from the predicted state with `CellKeys.found(state)` and classifies it against the
placement's `Expected` at that position with `CellState.classify`. MATCHING, INCOMPLETE (not enough yet) and NEEDS_FINISHING pass;
WRONG_BLOCK, WRONG_ORIENTATION, TOO_MANY and EXTRA are refused. Wrong water is allowed, since water is not what the click
places. CANT_CHECK (unknown planned block) passes. That keeps restriction, the boxes and the finish sound in agreement,
including equivalents and the coming two-item blocks.

### D6: Blocks that act on use

Vanilla first lets the clicked block handle the use unless the player sneaks. To keep chests, doors and levers usable,
restriction skips a non-sneaking click whose clicked block's class overrides `BlockBehaviour.useWithoutItem` or
`useItemOn` (looked up once per block class by reflection and cached). It does not call those methods, since several
change client state when called.

Found while implementing: `CandleBlock` overrides `useItemOn` (to blow out lit candles with an empty hand), so the rule
alone would skip a candle clicked onto candles and break the "Too many" scenario. The skip therefore does not apply when
the predicted placement goes into the clicked block itself (vanilla's "replace clicked" case: a candle onto candles, a
slab onto a slab); such a click adds to the block rather than using it.

### D7: Settings, keys and the wheel

- `HeavymaticaConfig`: `build.easyPlace` and `build.placementRestriction`, both default false, setters save (as
  `ghost.showUnknownBlocks` does).
- Two keys without defaults in the Heavymatica category, with a conflict context that is active whenever no screen is
  open (not `HOLDING_REMOTE`), handled in the `build` package's tick.
- `RadialMenu`: `Entry.OPTIONS` replaces `IN_HAND` on the LOCKED page; a new `Page.OPTIONS` with `IN_HAND`, `EASY_PLACE`
  and `RESTRICTION`, each `current` when on, and commands `TOGGLE_EASY_PLACE` and `TOGGLE_RESTRICTION`. `Selection`
  carries the two settings. Opening and HOLD-mode behaviour follow the Layers sub-wheel.

## Risks / Trade-offs

- [Some servers check rotation changes or flag fast rotation packets] → Out of scope; Easy Place is opt-in and off by
  default. The rotation is restored at once.
- [The client's prediction and the server disagree, e.g. a block entity or a neighbour changes the state] → The server's
  answer wins, as for any vanilla placement; the comparison shows the result.
- [Simulation uses the client level, which may lag behind the server] → Same as vanilla's own prediction; rare and
  self-correcting.
- [Reflection on `useWithoutItem` misses blocks that act on use through other means] → Rare; sneaking still forces the
  restriction, and turning it off is one wheel pick.
- [Moving In-hand filter into a sub-wheel costs one more click] → It keeps its own key, and the wheel stays at five
  segments on a locked placement.
- [Unarchived `unknown-and-legacy-blocks` and `two-item-blocks` also touch the wheel and the comparison] → Archive
  `unknown-and-legacy-blocks` before this one; restriction uses the comparison's classify, so two-item blocks need no
  extra work here.

## Migration Plan

Client only. The new settings default to off, so nothing changes for existing players until they opt in. The In-hand
filter moves into Options; its key and per-placement flag are unchanged.
