# Tasks

## 1. Comparison: open state and chest type

- [x] 1.1 Add `CHEST_TYPE` to the orientation key. Add `OPEN` only for `DoorBlock`, `TrapDoorBlock` and `FenceGateBlock`, including subclasses. Add both to the shown properties. Verify with `CellStateTest` cases: gate opened after placing, trapdoor left closed and door opened are wrong orientation; an open barrel and fence connections are matching; two single chests merged and halves swapped are wrong orientation.
- [x] 1.2 Carry "half of a double chest planned" in `Expected` and "single chest found" in `Found`. When block and facing match, `classify` returns INCOMPLETE for a single chest under a planned half. Verify with `CellStateTest`: first half placed is not enough yet; second half connected is matching; a single chest facing east under a planned north half is wrong orientation. Verify with `MaterialCountsTest`: the single chest under a planned half needs no more items.
- [x] 1.3 Add `WorldComparison.judgePlaced`, which copies the planned `open` value onto a candidate state before judging. Verify with a unit test on the open-copy helper: a closed gate judged against a planned open gate gives MATCHING, a gate with the wrong facing stays WRONG_ORIENTATION, and blocks without `open` are unchanged.

## 2. Targeting HUD

- [x] 2.1 Add the HUD lines: "Open it" or "Close it" in orange when only the open state differs, and "Needs its other half" for a single chest under a planned half. Otherwise wrong orientation names the found properties, now including the chest type. Add the keys to `en_us.json` and every other lang file the project ships. Verify with `TargetLines` unit tests for the gate-to-open, door-to-close, chest-waiting and chest-stays-single scenarios, and with a test that the new keys exist in each lang file.

## 3. Easy Place and placement restriction

- [x] 3.1 Switch Easy Place and placement restriction from `judge` to `judgePlaced`. Verify with `PlacementRestrictionTest` and `EasyPlaceTest` cases: a closed gate is accepted where an open one is planned; a chest that would connect where single is planned is refused; a single chest under a planned half is allowed.
- [x] 3.2 Add the sneaking pass to `VanillaPlacement.find`: search without sneaking first, then with `player.input.keyPresses` swapped for a copy with shift set (restored in `finally`). Record on the found click that it needs sneaking. Verify with a `PlacementSearchTest` case: a fake placement function accepts only sneaking clicks, and the search returns a sneaking click after every non-sneaking one is rejected.
- [x] 3.3 In `EasyPlace.send`, wrap a sneaking click in a `ServerboundPlayerInputPacket` with shift set before and the real `keyPresses` after. Leave the real player's sneak rule unchanged. Treat a target whose found block is a single chest under a planned half as not ours. Verify with an `EasyPlaceTest` / `BuildRules` case for the excluded chest target, then in `runClient` against the easy-place scenarios: single chest beside a chest, completing a double chest, first half, and open gate and trapdoor placed closed.

## 4. Layer keys without the remote

- [x] 4.1 Add a `LAYER_KEYS` conflict context (no screen, and remote held or selected placement locked) that conflicts with itself and `HOLDING_REMOTE`. Move `MOVE_FORWARD`, `MOVE_BACK`, `MOVE_UP`, `MOVE_DOWN` and `CYCLE_LAYERS` to it. Verify with a unit test of the context's conflict rules, and in `runClient` that the controls screen still reports a clash when two Heavymatica keys share a binding.
- [x] 4.2 In `RemoteInput.tick`, when the remote is not held, a locked placement is selected and no screen is open, run the layer handling (move, resize or offset, cycle with Shift) and drain every other remote key. Show the new layer state in the action bar after each change. Verify in `runClient` against the "Layer keys without the remote" scenarios: window up and offset change while holding blocks, cycling, an anchored placement that doesn't move, and a chat screen that blocks the keys. Also check that R, M and the wheel key do nothing without the remote.
- [x] 4.3 Update the README's key and layer section to say the layer keys work without the remote on a locked placement. Verify by reading the section back against the spec.

## 5. Integration

- [x] 5.1 Run `./gradlew :heavymatica:test` and confirm it passes. In `runClient`, build a small locked schematic with a double chest, a single chest beside it, an open fence gate and a closed trapdoor. Check that the boxes, HUD lines and progress end at 100% only once the gate is opened and the chests match.
