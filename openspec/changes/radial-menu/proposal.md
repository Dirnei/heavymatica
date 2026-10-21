# Proposal

## Why

The remote has grown a key for almost every action: Delete, L, I, K, the arrows for layer modes, and material swaps
would add N. Players have to learn and remember them, and each new feature takes another key from vanilla or other
mods. A radial menu ("wheel") on one key gathers the one-shot actions in one place, shows only what the current state
allows, and leaves the keys for continuous controls (moving, rotating, mirroring, layer window) where they are.

## What Changes

- **A wheel on V.** While a remote is held, V (rebindable) opens a wheel of segments around the screen centre. It
  never opens without a remote or while another screen is open.
- **Two modes (client config `wheel.mode`):**
  - HOLD (default): hold V, point with the mouse, release V to run the segment. Releasing in the centre or Esc
    cancels.
  - TOGGLE: tap V to open, click a segment to run it. Tap V again, Esc or a click in the centre closes it.
- **While the wheel is open** the camera does not turn (the mouse only points) and WASD, jump, sneak and sprint keep
  working. The game does not pause.
- **Segments follow the state:**
  - no placement selected: Library only
  - FOLLOWING: Cancel and Library
  - ANCHORED: Lock, Remove and Library
  - LOCKED: Unlock, In-hand filter, Layers, Material list and Library
- **Confirmation on the wheel:** Remove (of an anchored placement) and Unlock swap the wheel to Yes / Cancel with a
  label such as `Remove "Castle Gate"?`. Yes runs, Cancel, Esc or the centre aborts. In HOLD mode, releasing V on
  Remove or Unlock keeps the wheel open on Yes / Cancel and a click answers, so one flick never deletes.
- **Layers sub-wheel:** Off / From-to / Follow feet set the layer mode directly.
- **Keys that keep their defaults:** arrows (move; on a LOCKED from-to placement up / down move the window), numpad + /
  - (up / down, window size, follow offset), R (rotate, Shift: counter-clockwise), M (mirror), and H for the library
  without the remote.
- **BREAKING (default keys):** Delete (remove), L (lock / unlock), I (in-hand filter) and K (material list) lose their
  defaults. Each stays a rebindable key in the Heavymatica category, unbound by default, and a new unbound "cycle layer
  mode" key joins them. A directly bound remove or unlock key keeps today's press-twice-within-3-seconds rule.
- **BREAKING (arrows on a locked placement):** left / right no longer cycle the layer mode; they do nothing there.
- **Overlay hints:** show "V menu" plus the keys that stay. Hints for actions that moved to the wheel only show when
  the player has bound a key for them.

Out of scope:
- a gamepad or controller wheel
- customising which segments the wheel shows or their order
- the Materials segment for material swaps, which `material-swaps` adds

## Capabilities

### New Capabilities
- `client/radial-menu`: the wheel key and its modes, camera and movement while open, which segments show in which
  state, confirmation, the layers sub-wheel, and the unbound keys behind every wheel action.

### Modified Capabilities
- `client/placement-remote`: the default key layout loses Delete, L, I and K and gains V; the overlay hints show the
  wheel key and only the directly bound wheel actions.
- `client/placement-layers`: the layer setting is set from the wheel's sub-wheel instead of cycled with left / right,
  and the in-hand filter is toggled from the wheel.
- `client/schematic-placement`: removing, locking and unlocking happen from the wheel (with a confirmation for remove
  and unlock); a directly bound key keeps the second-press rule.

## Impact

- **Mod code only (`src/mod/heavymatica`):**
  - the `remote` package: the wheel key, the game-free wheel model, the wheel screen, the input changes and the
    overlay hints
  - the `placement` package: direct lock, unlock, remove and set-layer-mode calls on the controller
  - the client config: `wheel.mode`
- **Players' key bindings:** Minecraft saves every binding in `options.txt`, so existing installs keep Delete, L, I and
  K (and their hints) until the player resets them in the controls screen. New installs start with them unbound.
- **Depends on `world-comparison` and `material-list`.** Both modify the `client/placement-remote` requirements this
  change modifies, and its delta builds on their text. The `client/material-list` capability only exists in the
  unarchived `material-list` change, so this change doesn't modify it; the wheel's Material list segment and the
  overlay's "more short" pointer are specified in `client/radial-menu` instead.
- **Archive order:** `world-comparison` → `material-list` → `radial-menu` → `material-swaps`.
- No cloud, API, server or saved data changes.
