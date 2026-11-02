# Tasks

## 1. Server test

- [x] 1.1 Spike on `runServer` (D2, D3): a temporary dev step sends a `ServerboundMovePlayerPacket.Rot`, `gameMode.useItemOn` on an air position with a chosen face and hit location, then the real rotation, for oak stairs (4 facings × 2 halves), oak logs (3 axes), a top oak slab, a wall torch against a block, and an observer facing up and down. Record which states arrived and any server-log warnings (moved wrongly, interaction out of range) in the change's `design.md` under D3. If the server refuses or rubber-bands, stop and revise the design with the user before going on. Remove the temporary step afterwards.

## 2. Placement search

- [x] 2.1 Add `build.PlacementSearch` (D1): given the client level, player, held stack, target position, planned state and target kind, it tries the candidate clicks in the design's order with the player's rotation temporarily set, and returns the first hit result and rotation whose `BlockItem.getPlacementState` matches the planned state on the orientation and amount properties the comparison checks, or none. Verify with a `PlacementSearchTest` under `./gradlew :heavymatica:test` built on a fake "placement function" (the search core takes it as a parameter), covering: the player's own rotation preferred, a facing that needs a turned yaw, a half that needs a low hit, an axis from the face, a not-enough-yet slab, and no candidate.
- [x] 2.2 Add `build.EasyPlace` (D2, D4): the conditions of "When Easy Place acts" from the ghost target (`GhostTargeting.fresh()`), the candidate items as `GhostPick.decide` orders them, the search, and the send order rotation → `useItemOn` → real rotation with the client rotation restored before returning; the "can't place" action-bar message when the search finds nothing. Verify with a unit test for the act / don't-act decision (sneaking, spectator, remote held, out of reach, matching target, wrong item, matching item), and in `runClient` for "Stairs facing away", "Log on its side", "Top slab", "Completing a double slab", "Wall torch", "Torch without support", "Camera unchanged" and "Sweep along a row".

## 3. Placement restriction

- [x] 3.1 Add `build.PlacementRestriction` (D4, D5): the restricted-position lookup over locked placements in the dimension and their visible layers (selected placement first), prediction of vanilla's click with the real rotation through the same simulation, judging via `CellKeys.found` and `CellState.classify`, and the refused / allowed outcome with its message (the planned block's name, or air). Verify with a `PlacementRestrictionTest` on the judging core for every state (matching, equivalent, incomplete, needs finishing allowed; wrong block, wrong orientation, too many, extra refused; can't check allowed) and the outside / hidden-layer / anchored cases.
- [x] 3.2 Add the "acts on use" check (D6): a per-class cache of whether a block overrides `useWithoutItem` or `useItemOn`, skipped while sneaking; only `BlockItem`s are judged. Verify with a unit test on chest, oak door, lever, stone button (act) and stone, oak planks (don't), and in `runClient` for "Opening a chest", "Sneaking at a chest" and "Bucket".
- [x] 3.3 Wire the use-key hook (D4) after `RemoteInput`: remote → untouched, Easy Place click → Easy Place, else restriction, else vanilla; refused clicks cancel without swing. Verify in `runClient` for "Wrong block", "Wrong orientation", "Planned air", "Too many", "Matching block", "Half of a double slab", "Aim decides the slab half", "Wrong half refused", "Wrong item with both on", and on `runServer` for "Nothing reaches the server" (item count unchanged, nothing placed).

## 4. Settings, keys and wheel

- [x] 4.1 Add `build.easyPlace` and `build.placementRestriction` (default false, setters save) to `HeavymaticaConfig`, the two unbound keys with a "no screen open" conflict context, and the toggle with its action-bar message and language keys (D7). Verify in `runClient`: fresh config has both off; binding the keys toggles them while holding blocks; both survive a restart.
- [x] 4.2 Replace `RadialMenu.Entry.IN_HAND` on the LOCKED page with `OPTIONS` and add `Page.OPTIONS` with In-hand filter, Easy Place and Placement restriction (marked when on), `Command.TOGGLE_EASY_PLACE` and `TOGGLE_RESTRICTION`, the settings in `Selection`, icons, labels and language keys; picking closes the wheel; HOLD mode leaves the sub-wheel open (D7). Update `RadialMenuTest` for the LOCKED page and add cases for the Options page, marks, picks and Esc. Verify in `runClient` for "Turn Easy Place on", "Toggle the in-hand filter", "Marks show the current settings" and "Close without a change", and that the in-hand filter key still works.

## 5. Wrap-up

- [x] 5.1 Run the full `./gradlew :heavymatica:test` and fix failures; update `ROADMAP.md`'s Litematica gaps (Easy Place and restriction done) and the README's dev notes if the dev steps changed.

## Workflow follow-up

- Archive `unknown-and-legacy-blocks` before archiving this change, since both touch `client/radial-menu`.
