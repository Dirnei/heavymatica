# Proposal

## Why

Players can't tell whether Easy Place or placement restriction is on. The wheel's only cue is a small dot on the Options
segment, and nothing in the HUD says it while you build. One tester wasn't sure whether they had turned Easy Place off at
all. The placement overlay also disappears as soon as the remote leaves the hand, but players often want to keep their
layers, progress and shortages in view while they hold blocks.

## What Changes

- Wheel: a toggle segment that is on gets a green tint over its glass, in addition to the existing dot. The toggle
  segments are In-hand filter, Easy Place, Placement restriction, Unknown blocks, Mistakes (while the pointer is not off)
  and the new Pin overlay. Picker pages (Layers, Opacity, Paste, the Mistakes choices) keep only the dot.
- HUD: while Easy Place or placement restriction is on and a locked placement exists in the player's dimension, a small
  row of their wheel icons on glass sits centred above the hotbar. Nothing shows while both are off.
- Pinned overlay: a new client setting `overlay.pinned` (default off) keeps the placement overlay on screen without the
  remote, while a placement is selected. It is toggled from the settings screen (HUD section) and from a new Pin overlay
  segment in the wheel's Options sub-wheel, with an action-bar message. Without the remote, the pinned overlay shows the
  same content but no key hints. The exception is the layer-key hints on a locked placement, since those keys work
  without the remote.
- Wheel without the remote: while a placement is selected (and capture mode is off), the wheel key opens the wheel
  without the remote in hand, and everything in it works except Unlock. Unlock is greyed out with the hint "Hold the
  remote to unlock", because an unlocked placement is moved with the remote. The bound lock, remove, in-hand filter and
  materials keys follow the wheel, but the lock / unlock key does not unlock without the remote. With nothing selected,
  or in capture mode, the wheel still needs the remote. Positioning (right-click with the remote, and the move, rotate
  and mirror keys) is unchanged.
- Options now shows whenever a placement is selected, so Pin overlay is always reachable. A following or anchored
  placement's Options holds Pin overlay, plus Unknown blocks when it has some.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client/radial-menu`: Options sub-wheel content and when Options shows; the on tint for toggles; the Pin overlay
  segment; the wheel and its action keys work without the remote while a placement is selected, except Unlock.
- `client/placement-remote`: the placement overlay can be pinned; which keys need the remote.
- `client/easy-place`: the HUD shows the active build options above the hotbar.
- `client/settings-screen`: the HUD section lists the pinned overlay setting.

## Impact

- `remote/RadialMenu.java`, `remote/WheelPainter.java`, `remote/WheelStyle.java`, `remote/WheelIcons.java`,
  `remote/RemoteInput.java`, `remote/RemoteKeys.java`, `remote/RadialMenuScreen.java`: segments, tint, the pin command,
  the key context for a selection, and the wheel staying open without the remote.
- `remote/PlacementOverlay.java`: pinned visibility and hint filtering.
- A new HUD layer for the build-option icons, registered in `HeavymaticaClient`.
- `HeavymaticaConfig.java`, `ui/SettingsTable.java`, `en_us.json`: the setting, its labels and the messages.
- A new sprite, `textures/gui/sprites/wheel/pin_overlay.png`.
- Tests: `RadialMenuTest`, `WheelIconsTest`, plus tests for the overlay and HUD visibility rules.
