# Tasks

## 1. Finish rules

- [x] 1.1 Add `Finishes` in `placement` with `check`, `finish`, `bringBack` and `canBringBack`, arming and re-arming per ID, one candidate, and a 10-second window (D1, D2). Verify with `FinishesTest` under `./gradlew :heavymatica:test`, covering every scenario in "Finishing a placement" and "Bringing a finished placement back" that needs no game: last block, unknown parts, extra blocks, empty schematic, bring back, too late, only the latest, and re-finish after a repair.
- [x] 1.2 Call `Finishes` from `PlacementSession.tick` after the comparison tick. A finish removes the placement through `Placements.remove` and notifies a finish listener, and bring back re-adds and selects the remembered record (D1, D2). Verify with `./gradlew :heavymatica:test` and a `HEAVYMATICA_DEV_SCREEN` step that logs the placement count and the bring-back state after a scripted finish.

## 2. Feedback

- [x] 2.1 Register a finish listener on the client that plays `SoundEvents.PLAYER_LEVELUP` through `SimpleSoundInstance.forUI`, and shows a `SystemToast` "%s finished" whose hint names the wheel key's current binding. Add the language keys (D3). Verify in `runClient`: finish a small locked placement, hear the sound, see the toast with the placement's name, and check the placement is gone from the overlay and the world list.

## 3. Wheel

- [x] 3.1 Add `BRING_BACK` to `RadialMenu`, first in every state while a finish can be brought back, with its icon, colour and label, running bring back and closing the wheel (D4). Verify with a `RadialMenu` unit test for "Just finished", and in `runClient` for "Bring back from the wheel": the placement returns LOCKED and selected, and it doesn't finish again until a block is broken and replaced.

## 4. Integration

- [x] 4.1 Check end to end in `runClient`:
  - "Locking a finished build".
  - "Too late": the segment is gone after 10 seconds.
  - The placement stays removed after leaving and rejoining the world.

  Verify by screenshots in `runs/client/screenshots/` and the client log.
