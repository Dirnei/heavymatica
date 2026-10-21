# Tasks

## 1. Model (game-free)

- [x] 1.1 Add `PlacementController.lock()`, `unlock()`, `removeNow()` and `setLayerMode(mode, feetLayer)`, each clearing a pending second press, and make `cycleLayers` use `setLayerMode` (D4); verify unit tests for each in its valid and invalid states (lock on FOLLOWING, removeNow on LOCKED, setLayerMode on ANCHORED) and that the first switch to from-to opens a one-layer window at the feet
- [x] 1.2 Add `RadialMenu` with segments per state, the MAIN / LAYERS / confirm pages, `release` / `click` / `wheelKey` / `escape` outcomes for HOLD and TOGGLE, and the confirm label (D2); verify unit tests for every state's segment set, release on Remove and Unlock staying open on Yes / Cancel, Yes and Cancel outcomes, release and click in the centre, the layers sub-wheel commands with the current mode marked, and release ignored in TOGGLE
- [x] 1.3 Add `WheelGeometry.segmentAt` and `spans` (D3); verify unit tests for the dead zone, segment 0 at the top, clockwise order, sector edges for 2, 5 and 6 segments, directions beyond the ring, and that spans cover each segment and leave the gaps out

## 2. Keys, config and controller wiring

- [x] 2.1 Add `WHEEL` (V) and `CYCLE_LAYERS` (unbound), unbind `REMOVE`, `LOCK`, `IN_HAND` and `MATERIALS`, add `wheel.mode` (HOLD / TOGGLE) to the client config, consume left / right on a locked placement and handle `CYCLE_LAYERS` with Shift in `RemoteInput`, and add the lang entries (D5); verify `./gradlew :heavymatica:build` passes and in `runClient` that the controls screen lists the wheel key on V and the five action keys as not bound, and that left / right on a locked placement change nothing while up / down still move the window

## 3. Wheel screen

- [x] 3.1 Add `RadialMenuScreen`: non-pausing, no background, ring drawn from `WheelGeometry.spans` with hover, disabled and current colours, item icons and labels, centre label, HOLD release through `keyReleased` / `mouseReleased`, clicks, Esc, the wheel key closing, closing when the remote leaves the hand, and movement keys kept down (D1, D3); open it from `RemoteInput` and run its commands there (D5); verify in `runClient` that V opens it with the remote and not with a sword, that the game keeps ticking, that a held W keeps the player walking and the camera does not turn while the cursor moves, and take screenshots of the main wheel per state
- [x] 3.2 Add the dev steps `wheelPoint`, `wheelCentre`, `wheelRelease`, `wheelClick` and `logWheel` (D7); verify in `runClient` the scenarios of "Hold mode", "Toggle mode" (after switching `wheel.mode`), "Segments follow the state", "What the segments do", "Confirming remove and unlock" and "Layers sub-wheel", with screenshots of the confirm page and the layers sub-wheel

## 4. Overlay and integration

- [x] 4.1 Update `PlacementOverlay` hints: `V menu` in every state, no left / right layer-mode hint, wheel actions only when their key is bound, and the "+N more short" line naming the wheel's Material list when the material list key is unbound (D6); verify in `runClient` with `logOverlay` the "Wheel actions without keys", "Bound wheel action" and "More short without a key" scenarios, and that a bound remove key still shows "press again to remove"
- [x] 4.2 Run a scripted session with default keys: place from the library, anchor, rotate with R, mirror with M, move with the arrows, lock from the wheel, set from-to from the sub-wheel, move the window with up / down and numpad, toggle the in-hand filter, open the material list, unlock with Yes, remove with Cancel then Yes; verify the logged placements match each step
- [x] 4.3 Update `ROADMAP.md` step 3 to describe the wheel and the remaining direct keys; verify the file reads consistently

## Workflow follow-up

- Archive `world-comparison` and `material-list` before archiving this change, and this change before
  `material-swaps`.
- Commit the implementation once all tasks are done (Conventional Commits, for example `feat(remote): add radial menu`).
- Archive the change with `/opsx:archive` when asked.
