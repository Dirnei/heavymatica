# Proposal

## Why

While building by hand the player looks at a ghost and wants to know which block it is and to middle-click to pick
it, as with real blocks. Vanilla's crosshair only hits real blocks, so today the ghost under the crosshair can't be
named or picked: the overlay only explains mistakes vanilla happens to hit, and middle click picks whatever real
block is behind the ghost, or nothing. Since 1.21.4 pick block is also decided by the server, which only sees the
real world.

## What Changes

- **Ghost targeting.** A client-side ray-cast finds the ghost under the crosshair.
  - It walks the cells from the eye along the view, and stops at the first real block or the first ghost cell that
    is actually drawn.
  - A cell is drawn only when it belongs to a **LOCKED** placement (selected or not), lies in the visible layers,
    passes the in-hand filter (dimmed counts) and isn't hidden by the comparison. Matching cells are not targets,
    and FOLLOWING and ANCHORED placements are never targeted.
  - A real block that holds a mistake (wrong block, orientation or water, or needs finishing) at a placement
    position is that cell's target, with the found block attached. The planned block wins over the real one.
  - The target names the placement, the position, the planned (swapped) block state and the cell state.
- **Targeting HUD.** A small HUD below the crosshair shows the target:
  - the planned block's item icon and name, and its relevant properties (facing, half, axis and the like)
  - its state in the comparison colours: missing; wrong block (red) naming the block that is there; wrong
    orientation (orange); wrong water (cyan); needs finishing (yellow) naming the tool; or not compared yet while
    scanning
  - the placement's name when it is not the selected one
  - a muted "out of reach" hint beyond block-interaction reach, where middle click does not pick
  - The HUD range is configurable (default 32 blocks); range 0 turns the HUD off.
- **Jade support (optional).** When Jade is installed, a Jade plugin shows the ghost target in Jade's tooltip, as
  the planned block plus a Heavymatica line with the state, and the own HUD hides. Without Jade nothing changes. If
  Jade's API does not fit, this part is dropped and the own HUD always shows.
- **Middle click picks the ghost.** Within block-interaction reach, pick block on a ghost target is handled on the
  client and works on vanilla servers:
  - The item is chosen in the order the material list uses for the planned block: its own item, the item that
    places it, then the items `block-equivalents` accepts for it.
  - In the hotbar: that slot is selected. In the main inventory: it is swapped into a suitable hotbar slot, which
    is selected. In creative, when not carried, it is added to the hotbar the vanilla creative way.
  - Only inside a carried shulker box or bundle: an action-bar message says so; nothing is taken out.
  - Not carried: an action-bar message, such as "You have no oak stairs".
  - Sneak + middle click, no ghost target, a target out of reach, or a planned block no item places: vanilla pick,
    unchanged.
  - A client setting turns the ghost pick off.
- **Overlay follows the ghost target.** The overlay's expected and found lines use the ghost target when there is
  one, so they agree with the HUD; extra blocks are still explained through vanilla's crosshair.

Out of scope (later):
- picking from containers (taking an item out of a shulker box, bundle or chest)
- targeting ANCHORED or FOLLOWING placements
- targeting extra blocks (magenta boxes) from the HUD

## Capabilities

### New Capabilities
- `client/ghost-targeting`: which ghost cells can be targeted and how the ray finds them, the targeting HUD and its
  range, the optional Jade tooltip, and picking a ghost with middle click (item order, select, swap, creative,
  messages, the vanilla fallbacks) with its settings.

### Modified Capabilities
- `client/placement-remote`:
  - "Comparison in the overlay": the crosshair target is the ghost target when there is one.
  - "Finishing in the overlay" (added by `block-equivalents`): the same for a position that needs finishing.

## Impact

- **Mod code only (`src/mod/heavymatica`):**
  - `placement`: game-free ray traversal, drawn-cell rule and target choice; the game-facing targeting service over
    `PlacementSession`, `WorldComparison` and `Layers`; the in-hand filter shared with `GhostRenderer`
  - a game-free pick decision and the client pick handler (the pick key's clicks taken on `ClientTickEvent.Pre`); a
    container-aware view of `CarriedItems`
  - the targeting HUD layer; `remote/PlacementOverlay` reading the ghost target
  - an optional Jade plugin package, only loaded when Jade is installed
  - client config (`targeting` section), language keys, DevScreens steps
- **Build:** a `compileOnly` Jade dependency from the Modrinth maven and an optional `jade` dependency in
  `neoforge.mods.toml`. Jade is never bundled or required.
- **Network:** only vanilla packets the client already may send (selected slot, inventory swap click, creative slot).
  No server mod needed.
- **Depends on `block-equivalents`** (item order, needs finishing in the HUD) and on what it builds on. Archive
  order: `material-swaps` → `ghost-opacity-wheel` → `block-equivalents` → `ghost-targeting`.
- No cloud, API or saved data changes.
