# Tasks

## 1. Setting

- [x] 1.1 Add `overlay.pinned` (default false, with a comment) to `HeavymaticaConfig`, with a getter and a setter that saves. Add it to `SettingsTable` in the HUD section after `overlay.compact`, with name and hint lang keys, plus `heavymatica.overlay.pinned.on` / `.off` action-bar messages. Verify with the existing settings table and lang tests (every setting has name and hint keys) and `./gradlew :heavymatica:test`.

## 2. Wheel

- [x] 2.1 Add `Segment.tinted()` for the toggle entries, the `PIN_OVERLAY` entry and `TOGGLE_PIN_OVERLAY` command, and `pinned` on `Selection`. Show Options on FOLLOWING and ANCHORED after Opacity always, with Unknown blocks (if any) then Pin overlay. On LOCKED, put Pin overlay before Mistakes. Verify with `RadialMenuTest` cases for every Options scenario in the radial-menu delta, the tint, and the hint key for the new entry.
- [x] 2.2 Handle `TOGGLE_PIN_OVERLAY` in `RemoteInput.run` (toggle, save, action bar) and pass `pinned` into the selection. Verify by compiling and by the `RadialMenuTest` command mapping.
- [x] 2.3 Draw the green tint for `toggle && current` segments in `WheelPainter`, under the hover fill. Verify in a dev screenshot of the Options page with Easy Place on and restriction off, if a scripted client run is possible; otherwise list it as a manual check.
- [x] 2.4 Create `pin_overlay.png` (16×16) with a script in the scratchpad. Verify with `WheelIconsTest`.

## 3. Wheel without the remote

- [x] 3.1 Add the `SELECTED_KEYS` context with a pure rule next to `LayerKeyRules`, and move `WHEEL`, `LOCK`, `REMOVE`, `IN_HAND` and `MATERIALS` to it, keeping conflicts among all three Heavymatica contexts. Verify with unit tests of the rule (remote held; selection without capture; selection while capturing; nothing selected; screen open) and of the conflict pairs.
- [x] 3.2 Add `remoteHeld` to `Selection`. Without the remote, Unlock is disabled and Start capture is left out; add the `heavymatica.wheel.hint.unlock.disabled` and `heavymatica.unlock.needs_remote` lang keys. Verify with `RadialMenuTest`: Unlock disabled without the remote and enabled with it, no Start capture without the remote, picking a disabled Unlock stays open, Lock and Options work, and the disabled hint key exists.
- [x] 3.3 In `RemoteInput`, handle the wheel and action keys without the remote while a placement is selected and capture is off: lock but no unlock (action-bar message), remove, materials, in-hand and layers on a locked placement; drain the rest; claim the shared keys. Pass a `stillOpen` supplier to `RadialMenuScreen` in place of its `Remotes.held()` check. Verify by compiling and the tests above. List the in-game scenarios as manual checks (wheel opens without the remote with a selection, not without one, not in capture mode; stays open when the remote is put away; bound K locks but does not unlock).

## 4. Pinned overlay

- [x] 4.1 Let `PlacementOverlay` render while pinned with a selection, and filter hints without the remote as in the design. Put the visibility and hint rules in pure functions. Verify with a unit test of those rules: pinned and not held, not pinned, nothing selected, F1 and screen open, and only the layer hints on a locked placement without the remote.

## 5. Build option row

- [x] 5.1 Add `BuildOptionsHud` above the hotbar, register it in `HeavymaticaClient`, and put its show rule in a pure function. Verify with a unit test of the rule (each option on or off, no locked placement, screen open, F1, icon order), and in the dev screenshot if possible.

## 6. Integration

- [x] 6.1 Run `./gradlew :heavymatica:test` and confirm it passes. Update the README's overlay and wheel notes for pinning and the icon row.
