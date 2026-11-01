# Design

## Context

- `Comparisons.tick` (game thread) keeps one `WorldComparison` per locked placement in the player's dimension.
  `ComparisonState.matching(0, sizeY - 1)` and `counted(0, sizeY - 1)` give the overall progress. Unknown positions
  count as not done, so `matching == counted` already implies that every counted position was compared.
- `Placements` is the world's list with `add`, `remove` and `select`. `PlacementStore` saves it 2 seconds after a
  change through the `onChange` listener.
- `PlacementController` owns the player's actions, including the "LOCKED cannot be removed" rule. `RadialMenu` builds
  the segments from the selection, and `RadialMenuScreen` turns a picked segment into an `Outcome`.
- Toasts and UI sounds are vanilla client features: the toast manager and `SimpleSoundInstance.forUI`.

## Goals / Non-Goals

**Goals:**
- Finish detection with no extra scanning: it is read off the counts the comparison already keeps.
- The placement rules stay in `placement`, and the sound, toast and wheel stay in `remote`/UI code, so the finish logic
  can be unit-tested without a game.

**Non-Goals:**
- Persisting the bring-back window or the "brought back" mark across leaving the world. After a rejoin, a
  brought-back placement that is still complete finishes again, with its own bring-back window.
- A configurable sound, or turning the feature off.
- Finishing placements that are not locked, or that are in another dimension; neither has a comparison.

## Decisions

### D1: A `Finishes` tracker in `placement`, fed by `PlacementSession.tick`

`Finishes` is a game-thread class with the following parts:
- `check(placement, state, nowMillis)` returns whether the placement just finished. That is the case when
  `counted > 0`, `matching == counted`, and the placement is armed.
- **Arming:** a placement is armed unless it was brought back. A brought-back placement is re-armed once its state has
  no unknown counted positions and `matching < counted`.
- `finish(placement, nowMillis)` remembers `(placement, finishedAt)` as the one bring-back candidate, replacing any
  earlier one.
- `bringBack(nowMillis)` returns the candidate within 10 seconds and clears it, and marks its ID as not armed.
- `canBringBack(nowMillis)` is used by the wheel.

`PlacementSession.tick` runs after `comparisons.tick`. For every locked placement with a comparison, a finish removes
it through `Placements.remove` and fires a `Consumer<Placement>` finish listener. The listener goes to the UI side and
plays the sound and shows the toast. Keeping the timing in `nowMillis` arguments, as `PlacementController` already does
for its confirmations, makes the rules unit-testable.

Alternative: detect the finish in `WorldComparison` when a cell changes. Rejected: it would spread "progress" logic
into the per-cell path, and the comparison is torn down as soon as the placement is removed.

### D2: Bringing back re-adds the same record

`Placements.add` takes the remembered `Placement` unchanged: same ID, state LOCKED, and the same layers, filter and
swaps. It goes to the front of the list and is selected. Because the ID is the same, the ghost meshes and the model
cache are simply reused or rebuilt, and the store saves it as before. The ID is held in the not-armed set until the
re-arm condition from D1 holds.

The finish removal goes through `Placements.remove`, not `PlacementController.removeNow`. The controller's rule that a
LOCKED placement can't be removed is about the player's actions, and stays untouched.

### D3: Feedback: UI sound and system toast

- **Sound:** `SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f)`. It is short, positional-free, recognisable
  as "done", and needs no asset.
- **Toast:** a `SystemToast` with the title "%s finished". Its text names the wheel key: "Open the wheel (V) within 10 s
  to bring it back". The key's current binding is read through its `getTranslatedKeyMessage`.

A toast survives screens and doesn't fight the action bar, which vanilla uses for other messages.

Alternatives:
- `UI_TOAST_CHALLENGE_COMPLETE`. Rejected: it is long and grand for a small build.
- The action-bar message. Rejected: it is overwritten by vanilla messages and too short for the hint.

### D4: The wheel's Bring back segment

`RadialMenu` gets the entry `BRING_BACK`, put first in every state's list while `Finishes.canBringBack(now)`. Its icon
is a clock and it is drawn in the confirm colour. Picking it runs `bringBack`; if the window ran out in the meantime,
the pick is a no-op that closes the wheel. The wheel already reads the selection when it opens, so no live refresh is
needed.

## Risks / Trade-offs

- [Another player finishes the last block while the player is far away] → the comparison notices it within the
  background budget, and the finish fires then. That is intended, since the build is done.
- [A finish right after locking surprises a player who locked over an existing build to check it] → the toast and the
  Bring back segment make it a one-click undo. After the undo it stays until a change.
- [Swaps or equivalents make a slightly different build count as 100%] → this follows the progress rules that already
  exist, so there is no new risk.
