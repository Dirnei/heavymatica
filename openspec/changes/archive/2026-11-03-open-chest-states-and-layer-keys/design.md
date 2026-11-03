# Design

## Context

The comparison reduces a block state to an orientation key built from `CellKeys.ORIENTATION`. Any property outside
that list is ignored, which is why `chest_type` and `open` slip through. Classification is per cell: `CellState.classify`
sees only the expected and found terms of one position, never its neighbours. Easy Place, placement restriction and
the ghost target all judge a candidate state through `WorldComparison.judge`, which uses the same classification.

The remote keys use one `IKeyConflictContext`, `HOLDING_REMOTE`, which needs the remote in the main hand. On a locked
placement, `RemoteInput.tick` already sends the move keys to the layer controls (`layerKeys`).

Checked in the 26.1 sources:
- `ChestBlock.getStateForPlacement` connects to a same-facing neighbour unless the click is a secondary use (sneaking).
  When sneaking, it connects only to the chest on the clicked side.
- `LocalPlayer.isShiftKeyDown()` returns `input.keyPresses.shift()`, so the client's placement prediction follows a
  temporarily swapped `keyPresses`.
- The server sets the player's sneaking from `ServerboundPlayerInputPacket` (`handlePlayerInput` → `setShiftKeyDown`).
  A vanilla client sends that packet whenever its input changes.

## Goals / Non-Goals

**Goals:**
- Chest type and the open state are part of the comparison, and neither breaks Easy Place or placement restriction.

**Non-Goals:**
- Easy Place opening or closing doors, trapdoors and gates after placing them. That would be a second interaction and
  could toggle a redstone-linked door; it stays the player's job.
- Neighbour-aware classification, such as checking whether a chest's partner position is planned or built.
- Comparing other "use" states: a lectern's book, a jukebox, note block pitch, repeater delay, comparator mode.

## Decisions

### Orientation key gains `chest_type` and a filtered `open`
`BlockStateProperties.CHEST_TYPE` joins `ORIENTATION`. `BlockStateProperties.OPEN` counts only for `DoorBlock`,
`TrapDoorBlock` and `FenceGateBlock` (with their subclasses, which covers copper doors and trapdoors). A barrel's `open`
flips while a player looks inside, so it must not count. A block-class check is the rule, not a list of blocks, so modded
doors work too. Both also join `SHOWN`, so the wrong orientation line names them.

Alternative: a separate `WRONG_STATE` cell state. Rejected because the user wants it orange like orientation, and a new
state touches the boxes, overlay counts, restriction and HUD for no visible gain.

### A single chest under a planned half is INCOMPLETE
Classification is per cell, so it cannot see whether a partner exists. The first half of a double chest is always placed
single, which would make it wrong orientation and get it refused by Easy Place and restriction. Instead, `Expected`
carries a flag "half of a double chest", and `Found` carries "single chest". When the block and facing match and the
found chest is single where a half is planned, `classify` returns INCOMPLETE. That fits the meaning "not enough yet:
another block completes it". Easy Place, restriction and progress treat it correctly without new rules. A found single
chest is amount 1 against a planned 1, so the material list asks for nothing more at that position, and the partner
position asks for its own chest.

Two singles placed while sneaking where a double is planned stay INCOMPLETE (orange box, "other half needed") rather
than wrong orientation. That is acceptable: the box is the same colour, and breaking and replacing one chest fixes it.

### Judging clicks without the open state
`WorldComparison` gets a `judgePlaced(x, y, z, state)` for Easy Place and placement restriction. It copies the planned
`open` value onto the candidate state (only when both have the property) before classifying, then delegates to `judge`.
The ghost target, the comparison itself and the boxes keep using the real state. That is how a placed-closed gate is
accepted and then shown orange until it is opened.

### Easy Place tries a sneaking pass for any block
`VanillaPlacement.find` runs the existing candidate search without sneaking first. If nothing is accepted, it runs the
same search again with sneaking. For prediction, it swaps `player.input.keyPresses` for a copy with `shift` set and
restores it in `finally`. The found click remembers that it needs sneaking. `EasyPlace.send` then sends a
`ServerboundPlayerInputPacket` with shift set before the rotation and click, and the real `keyPresses` after. The
client's `lastSentInput` already equals the real input, so the client does not send a duplicate. This is generic, not
special-cased to chests, because sneaking never changes what a click on an empty or replaceable target does except
through the placed block's own placement logic.

Easy Place's rule "the player is not sneaking" refers to the real player and is unchanged. A ghost whose found block is
a single chest under a planned half is excluded from Easy Place (not ours). Clicking it would place the chest beside it,
somewhere other than the target, so the search could never succeed anyway, and the "can't place" message would be
misleading.

### Layer keys get their own conflict context
A new `LAYER_KEYS` context is active when no screen is open and either the remote is held or the selected placement is
locked. `MOVE_FORWARD`, `MOVE_BACK`, `MOVE_UP`, `MOVE_DOWN` and `CYCLE_LAYERS` move to it. Its `conflicts` matches
`HOLDING_REMOTE` and itself, so the controls screen still flags clashes among Heavymatica keys. The remaining keys stay
on `HOLDING_REMOTE`. `RemoteInput.tick` gets an early branch: when the remote is not held, a locked placement is
selected and no screen is open, it runs the layer handling (window move, resize or offset, cycle) and drains every other
remote key. Without the remote, layer changes report to the action bar through `sendOverlayMessage`, built from the same
text the overlay uses for its layer line.

Alternative: separate layer-up and layer-down keys with no default. Rejected in favour of the keys players already use.

## Risks / Trade-offs

- [A schematic captured with a door opened by redstone plans it open] → It shows orange until opened, which is correct
  for the plan. The player can toggle it.
- [The arrow keys now act while the player holds blocks] → This only happens on a locked placement and no screen. Vanilla
  binds nothing to arrows or the numpad by default, and the controls screen still shows conflicts with other mods'
  keys.
- [Sneaking for one click] → The server sees two input packets in one tick. A vanilla client sends the same packets when
  the player taps sneak, and the player's own sneak state is restored before the next tick.
- [Existing placements change state on reload] → Positions with an opened gate or a merged chest turn orange where they
  were green. That is the intended fix, and no stored data changes.

## Implementation Notes

- Minecraft is not on the unit-test classpath, so which properties count (chest type, the filtered open state) and the
  open-copy used by `judgePlaced` are checked in a scripted `runClient` run. Unit tests cover classification on integer
  keys, the sneaking order of the placement search, the Easy Place and restriction rules, the HUD lines and the
  layer-key rules (`LayerKeyRules`, a game-free helper the conflict context uses).
- The single-chest check for the HUD and Easy Place compares block states (`WorldComparison.waitsForOtherHalf`) instead
  of reading the comparison's cell state, so it works from a ghost target alone.
