## Why

Building a schematic by hand still means aiming at the right supporting face and turning to get each stair, log or
slab the right way round, and a misclick leaves a red or orange box to break out again. Litematica's two biggest
hand-building helpers, Easy Place and placement restriction, remove both chores. Ghost targeting already finds the
planned block under the crosshair and the comparison already knows what belongs at every position, so both helpers
build on what is there.

## What Changes

- **Easy Place:** with it on, right-clicking a ghost within reach while holding an item that places its planned block
  puts that block at the ghost's position with the planned orientation (facing, axis, half, slab type and the like). The
  player doesn't need to aim at a supporting face or turn around. It works on any server, vanilla included, using only
  the packets a vanilla client sends: a look direction and a block click chosen so that vanilla places exactly the
  planned state. When no click gives the planned state, nothing is placed and an action-bar message says so.
- **Placement restriction:** with it on, a right-click with a block item that would put a block inside a locked
  placement's visible layers is refused when the result would be a wrong block, a wrong orientation, too many, or a
  block where the plan says air. Outside every locked placement nothing changes, and blocks that act on use (chests,
  doors, buttons) can still be used.
- Both are client settings, off by default, saved in the config, and each has an unbound key that works without the
  remote in hand.
- **Options sub-wheel:** on a locked placement, the wheel's In-hand filter segment becomes Options, a sub-wheel with In-hand
  filter, Easy Place and Placement restriction, each marked when on. The in-hand filter key stays as it is.
- The change starts with a test on a dedicated server (`runServer`) that the look-direction and click packets place
  stairs, logs, top slabs, wall torches and observers as planned, before the full feature is built on them.

## Capabilities

### New Capabilities

- `client/easy-place`: placing the planned block of a targeted ghost with the planned orientation, and its setting and
  key.
- `client/placement-restriction`: refusing block placements that would contradict a locked placement, and its setting
  and key.

### Modified Capabilities

- `client/radial-menu`: the locked wheel shows Options instead of In-hand filter; new Options sub-wheel.
- `client/placement-layers`: the in-hand filter is toggled from the Options sub-wheel instead of its own segment.

## Impact

- Mod: a new `build` package (Easy Place, restriction, the placement search), changes to `remote/RadialMenu`,
  `remote/RemoteInput`, `HeavymaticaConfig` and the lang file, plus new keys in `RemoteKeys` or a sibling.
- Uses `targeting/GhostTargeting` for the target and `placement/WorldComparison` and `CellKeys` to judge a predicted
  block.
- No cloud, web or format changes. No server mod; the optional server mod (step 6) may later offer a cleaner placement
  request.
- Servers with strict anti-cheat may object to the look-direction packets; that is out of scope.
