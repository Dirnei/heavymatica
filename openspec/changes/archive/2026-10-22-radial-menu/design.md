# Design

## Context

For motivation see proposal.md, Why. For behaviour see `specs/client/radial-menu` and the deltas for
`client/placement-remote`, `client/placement-layers` and `client/schematic-placement`.

The current state this builds on:
- **`RemoteKeys`:** every placement key is a `KeyMapping` in the `HOLDING_REMOTE` conflict context (remote in the
  main hand, no screen open). `ALL` lists them, and `RemoteInput.claimSharedKeys` drops other bindings on the same
  keys while the remote is held.
- **`RemoteInput.tick`:** reads the keys each client tick. On a locked placement `layerKeys` maps up / down to the
  window, numpad + / - to size or follow offset, left / right to `cycleLayers`, and I to `toggleInHand`. K opens the
  material list or says "lock first".
- **`PlacementController`:** game-free. `remove` and `lockOrUnlock` hold the second-press state (`Pending`, 3 s
  window), and the overlay reads `removePending` / `unlockPending`.
- **`PlacementOverlay`:** a GUI layer, hidden while any screen is open. Hints name bound keys through `name(key)`.
- **Vanilla input with a screen open** (`Minecraft.setScreen`, `KeyboardHandler.keyPress`):
  - Opening a screen releases the mouse (cursor to the window centre) and calls `KeyMapping.releaseAll()`.
  - While a screen is open, key presses go to `Screen.keyPressed` and never set a `KeyMapping` down. Releases the
    screen doesn't consume still reach `KeyMapping.set(key, false)`.
  - `MouseHandler` only turns the player while the mouse is grabbed.
  - `LocalPlayer` reads its movement from the `KeyMapping`s every tick, screen or not.

Unit tests run without Minecraft classes, so the wheel's logic lives in game-free classes.

## Goals / Non-Goals

**Goals:**
- One game-free model decides the segments per state, the page (main, layers, confirm) and what a release, click,
  key or Esc does. The screen only draws it and feeds it input.
- Direct keys and wheel segments call the same controller methods, so they can't drift apart.

**Non-Goals:**
- Animations, sounds or icons drawn from custom textures. Segments use item icons and text.
- Remembering the last picked segment.

## Decisions

### D1. The wheel is a non-pausing Screen

`RadialMenuScreen` extends `Screen` with `isPauseScreen() == false` and an empty background (no blur, no dimming).

- Opening it releases the mouse, so the camera stops turning without any mixin, and the cursor starts at the wheel's
  centre, which is the "nothing picked" position.
- Esc, clicks, key releases and mouse releases arrive through the normal screen callbacks.
- `HOLDING_REMOTE` is inactive while it is open, so R, M and the arrows can't act behind it.
- The overlay hides like for any screen. The wheel's centre shows the selected placement's name instead.

**Keeping WASD:** the movement key mappings are in NeoForge's `IN_GAME` conflict context, so `isDown()` reports
false while any screen is open, and opening a screen releases them anyway. The screen therefore:
- swaps `LocalPlayer.input` for its own `ClientInput` while open, which reads forward, back, left, right, jump,
  sneak and sprint from the physical keys (`InputConstants.isKeyDown`, `glfwGetMouseButton`); sneak and sprint in
  toggle mode keep the state of the tick before the wheel opened
- puts the original input back in `removed()` and sets the movement mappings to the physical key state, so a key
  still held after the wheel closes keeps working without being pressed again

The screen closes itself in `tick` when the remote leaves the main hand.

Alternative: a GUI layer plus input interception. Rejected: it would need its own mouse grab and release, a camera
lock (cancelling `MouseHandler` turning has no NeoForge event), and catching Esc before the pause menu.

### D2. Game-free wheel model

`remote.RadialMenu` holds the open wheel's state:
- the mode (HOLD or TOGGLE)
- a snapshot of the selection taken when it opens: state (none, following, anchored, locked), name, layer mode and
  in-hand flag
- the page: MAIN, LAYERS, CONFIRM_REMOVE or CONFIRM_UNLOCK

It exposes:
- `segments()`: the segments of the current page, each an `Entry` (LIBRARY, CANCEL, LOCK, REMOVE, UNLOCK, IN_HAND,
  LAYERS, MATERIAL_LIST, LAYERS_OFF, LAYERS_FROM_TO, LAYERS_FOLLOW, YES, NO) with `enabled` and `current` flags. The
  Materials segment that `material-swaps` adds fits as one more entry, `enabled` false with nothing selected.
- `label()`: the centre text, for example the confirm question with the placement's name
- `release(index)`, `click(index)`, `wheelKey()`, `escape()`: each returns an `Outcome`. An outcome is `Stay`,
  `Close` or `Run(Command)`, where a command is LOCK, UNLOCK, REMOVE, CANCEL, TOGGLE_IN_HAND, LAYERS(mode),
  OPEN_MATERIAL_LIST or OPEN_LIBRARY. Index -1 is the centre.

Rules:
- On the MAIN page, picking Remove (anchored) or Unlock moves to a confirm page and Layers to LAYERS. The pick
  returns `Stay`.
- `release` is ignored (`Stay`) on the LAYERS and confirm pages, and in TOGGLE mode. So in HOLD mode the release
  that opened a confirm page or the sub-wheel can't also answer it.
- `release(-1)`, a release on a disabled segment, `click(-1)`, `escape()` and Cancel close. `click` on a disabled
  segment stays.
- `wheelKey()` (the wheel key pressed again while open) closes in both modes. In HOLD mode this only happens after a
  missed release, for example V tapped within one tick, so the wheel never gets stuck open.

The selection is snapshotted because the wheel blocks every input that could change it. Commands are run through
the controller, whose methods are no-ops in the wrong state, so a stale snapshot can't do harm.

Alternative: building the segments live every frame. Rejected: a segment could change under the cursor between
pointing and releasing.

### D3. Geometry and hit-testing

`remote.WheelGeometry` (game-free):
- `segmentAt(dx, dy, count, deadZone)`: -1 inside the dead-zone radius, otherwise the segment whose angular sector
  contains the direction. Segment 0 is centred at 12 o'clock, then clockwise. Distance beyond the ring still picks
  by direction, so a flick needs no precision.
- `spans(inner, outer, count, gap)`: per pixel row, horizontal runs of the ring, each tagged with its segment. They
  are computed once per layout. Pixels within the gap of a sector edge are left out, which draws the dividers.

The screen draws each run with one `fill`, coloured by segment: normal, hovered, disabled or current. This gives
real wedges with plain fills, without custom vertex code. Each segment gets an item icon and its label at the
sector's middle radius. The centre shows the label, or the placement's name.

Alternative: boxes on a circle. Rejected: they don't show which direction picks what.

### D4. Controller calls

`PlacementController` gains `lock()`, `unlock()`, `removeNow()` and `setLayerMode(mode, feetLayer)`:
- `lock()`: ANCHORED → LOCKED.
- `unlock()`: LOCKED → ANCHORED.
- `removeNow()`: removes a FOLLOWING or ANCHORED placement, never a LOCKED one.
- `setLayerMode`: sets the mode, opening the one-layer window at the feet when from-to is first chosen, like
  `cycleLayers`. `cycleLayers` uses it.

Each clears any pending second press. The direct keys keep `remove(now)` and `lockOrUnlock(now)` with their
second-press rule.

### D5. Keys and input

`RemoteKeys`:
- `WHEEL` (`key.heavymatica.wheel`, V) and `CYCLE_LAYERS` (`key.heavymatica.cycle_layers`) are new.
- `REMOVE`, `LOCK`, `IN_HAND`, `MATERIALS` and `CYCLE_LAYERS` are unbound (`GLFW_KEY_UNKNOWN`). Names stay.
  Minecraft writes every binding to `options.txt`, defaults included, so existing installs keep Delete, L, I and K
  as direct keys; only new installs, or a reset in the controls screen, leave them unbound. No migration rewrites
  saved bindings.

`RemoteInput.tick`:
- `WHEEL.consumeClick()` opens the wheel through a callback.
- On a locked placement, left / right are consumed and do nothing.
- `CYCLE_LAYERS` steps the layer mode (Shift: back) on a locked placement.
- The other keys keep their current handling.

`RemoteInput` opens the screen with a snapshot of the selection, and runs the commands it returns:
- controller calls for lock, unlock, remove, cancel, in-hand and layers
- its existing `openMaterials` / `openLibrary` callbacks for the screens, which open once the wheel has closed

Config: `HeavymaticaConfig.wheelMode()` from `[wheel] mode = "HOLD" | "TOGGLE"`.

### D6. Overlay hints

- Every state adds `<wheel key> menu` before the other hints.
- LOCKED drops the left / right "layer mode" hint.
- LOCKED shows in-hand, material list, cycle layers and unlock hints only for bound keys. ANCHORED shows lock and
  remove only for bound keys, FOLLOWING cancel only for a bound key.
- The "+N more short" line names the material list key when bound, otherwise `<wheel key>: Material list`.
- The "press again" lines for direct keys stay.

### D7. Development steps

`DevScreens` gains:
- `wheelPoint=<entry>`: moves the real cursor (GLFW) to the middle of that segment
- `wheelCentre`: moves the cursor to the centre
- `wheelRelease`: sends the wheel key's release to the open screen
- `wheelClick`: left-clicks at the cursor through the screen
- `logWheel`: logs the open wheel's page, segments and label

`press=key.heavymatica.wheel` opens the wheel through the normal key handling. HOLD and TOGGLE are switched by
editing the run's client config between runs.

## Risks / Trade-offs

- [Existing installs keep Delete, L, I and K from their saved `options.txt`, so they don't see the new defaults] →
  accepted: those keys keep working with the second-press rule and show as hints, and "Reset" in the controls screen
  unbinds them. New installs get the wheel-only layout.
- [A screen hides the overlay while the wheel is open] → the wheel's centre names the placement, and the wheel is
  only open for a moment.
- [Reading movement keys physically ignores key modifiers and scancode bindings] → movement mappings rarely use
  either. The worst case is that the player stops while the wheel is open.
- [Other mods that react to `ScreenEvent.Opening` see a screen opening for each wheel use] → accepted. It is a normal
  non-pausing screen.

## Migration Plan

No saved data changes. Archive `world-comparison` and `material-list` before this change, since all three modify
`client/placement-remote` and this delta builds on their text. Then archive this change before `material-swaps`,
whose `client/placement-remote` delta builds on this one.
