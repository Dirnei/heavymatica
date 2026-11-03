# Design

## Context

`WheelPainter.bodies` draws each segment's glass. The base body is `BODY_INNER` to `BODY_OUTER`, with the hover fill in
the entry kind's accent on top, then the rims and the current-option dot when `Segment.current()` is true. The dot logic
already skips Unknown blocks, whose "current" means "shown" and is carried by the icon (`unknown_blocks_shown` /
`_hidden`).

`RadialMenu.optionsSegments` builds the Options page. Options is on the LOCKED ring, and on FOLLOWING and ANCHORED rings
only when the placement has unknown blocks (`withOptionsForUnknownBlocks`).

`PlacementOverlay.render` returns early unless `Remotes.held()`. `lines()` builds the content and the hints together.
GUI layers are registered in `HeavymaticaClient` through `RegisterGuiLayersEvent`.

## Goals / Non-Goals

**Non-Goals:**
- Showing other settings (opacity, in-hand filter, mistake pointer) in the hotbar row. The in-hand filter is per
  placement and already in the overlay.
- A key for pinning.

## Decisions

### The segment knows whether it is tinted
`Segment.tinted()` is true when the segment is current and its entry is one of the toggles: In-hand filter, Easy Place,
Placement restriction, Unknown blocks, Pin overlay and Mistakes. Those entries only ever appear on the Options page,
so no flag has to be threaded through the page builders, and record equality in the tests stays unchanged.
`WheelPainter` draws a green layer (`GlassStyle.CONFIRM` at low alpha, inner to outer, like the hover fill) right after
the base body. The hover fill and rims draw on top, so hovering keeps the tint. Picker entries never count as toggles,
so they keep only the dot.

### Options on every selected state
`withOptionsForUnknownBlocks` becomes "Options after Opacity for FOLLOWING and ANCHORED". The NONE state stays without
Options. The sub-wheel gains `PIN_OVERLAY` (entry and command `TOGGLE_PIN_OVERLAY`), placed before Mistakes on LOCKED and
last on the other states. `Selection` carries `pinned` the same way it carries `easyPlace`. `RemoteInput.run` toggles
`HeavymaticaConfig.setOverlayPinned` and sends the action-bar message.

### Wheel and action keys with a selection
A third conflict context, `SELECTED_KEYS`, is active when no screen is open and the remote is held, or a placement is
selected while capture mode is off. `WHEEL`, `LOCK`, `REMOVE`, `IN_HAND` and `MATERIALS` move to it. The three contexts
(`HOLDING_REMOTE`, `LAYER_KEYS`, `SELECTED_KEYS`) each conflict with themselves and the other two, so the controls
screen still flags clashes among Heavymatica keys. The rule lives next to `LayerKeyRules` as a pure function, fed with
"a placement is selected" and "capturing" through suppliers set by `RemoteInput`, the same way `lockedSelected` is.

`RemoteInput.tick` handles the case without the remote in one branch. If a placement is selected and capture is off, it
reads the wheel key (opening the wheel with `remoteHeld = false`), lock (lock only; on a locked placement it sends the
"hold the remote to unlock" action-bar message), remove, materials, in-hand on a locked placement, and the layer keys on
a locked placement (with action-bar feedback, as before). Every other remote key is drained. `claimSharedKeys` claims
`SELECTED_KEYS`' keys, plus `LAYER`, in that state.

`RadialMenu.Selection` gets `remoteHeld`. Without the remote, the main ring leaves out Start capture, because capture
mode works only with the remote. The capture wheel and capture keys stay out of reach: the selection context excludes
capture mode. `mainSegments` builds Unlock with `enabled = remoteHeld`, and the
`unlock.disabled` hint ("Hold the remote to unlock.") comes through the existing `WheelText` `.disabled` mechanism.

`RadialMenuScreen.tick` stops checking `Remotes.held()` directly. It takes a `BooleanSupplier stillOpen` from
`RemoteInput`: remote held, or (a placement is selected and capture is off). That keeps the capture wheel closing when
the remote is put away, and keeps the placement wheel open without it.

### Pinned overlay reuses `lines()` with a hint filter
`render` shows when `Remotes.held()`, or when `HeavymaticaConfig.overlayPinned()` is on and a placement is selected.
`lines()` takes a `remoteHeld` flag. Without the remote, it adds only the layer hints (window, window size, follow
height, and the bound cycle key) on a locked placement, and skips the rest of the hint block, including the pending
remove or unlock lines. Those are only reachable with the remote anyway. The visibility rule goes into a small static
function so it can be unit-tested without the game.

### Build option row as its own GUI layer
`BuildOptionsHud` is registered above `VanillaGuiLayers.HOTBAR`. Its icons are 16×16 wheel sprites, each on a 20×20
`Glass.panel` with the overlay's colours, spaced 3 px apart and centred horizontally. The row sits at
`guiHeight - 64 - 20`, which clears the hotbar, the experience bar, hearts and food (centred row of 20 px cells can reach
into their columns), and the held item's name. Creative uses the same height so the row does not jump. "A locked
placement exists in the player's dimension" is read from the session's placements, using the same dimension check the
comparison uses. The decision is a small pure function for tests.

### Pin sprite
`pin_overlay.png` is drawn as a 16×16 pixel pushpin in the same white and 2-tone style as the existing sprites. It is
generated by a one-off script in the scratchpad, and only the PNG is committed. `WheelIconsTest` already checks that
every entry has a sprite and that no sprite goes unused.

## Risks / Trade-offs

- [The row collides with other mods' HUD elements above the hotbar] → It is small, and it only shows while an option is
  on near a locked build.
- [A pinned overlay covers part of the screen during normal play] → It only shows with a placement selected, and it is
  off by default and one wheel pick away.
