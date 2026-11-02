# Tasks

## 1. Plan and job

- [x] 1.1 Add `paste.PastePlan` (D1): built from a placement's `GhostView` and model, world positions grouped by section with target state (unknown blocks and structure voids left out) and block-entity NBT moved to the world position. Verify with a `PastePlanTest` under `./gradlew :heavymatica:test`: rotation and mirror, a swap, an unknown block left out, a structure void left out, and NBT position rewrite.
- [x] 1.2 Add the mode filter (D2) as a pure function of mode, planned state and world state: Replace all, Only into air (air and replaceable only), Skip air, and "already matches" skipping. Verify with a unit test per mode and the scenarios "Replace all clears terrain", "Only into air keeps blocks" and "Skip air keeps terrain".
- [x] 1.3 Add `paste.PasteJob` on the integrated server's tick (D1–D4): time-budgeted sections, chunk loading, undo recording before each change, `Clearable.tryClear`, `setBlock` without updates or drops, block-entity NBT load with a debug log on failure, progress counter, cancel on logout / server stop. Verify in `runClient` (creative single player): "Rotated with a swap", "Chest contents", "Unknown block", "Floating sand", and a 500,000-position paste with the overlay progress and no freeze ("Large paste").

## 2. Undo

- [x] 2.1 Add undo of the last paste through the same job in reverse with the recorded states and NBT, then drop the record; a new paste replaces it; logout drops it (D4). Verify in `runClient` for "Undo a paste" (including a replaced chest's contents), "Only the last one" and "After leaving the world".
- [x] 2.2 Undo paste also puts back the placement the paste finished, as it was and selected, unless it is back already; its bring back offer goes away (D4). Verify with `PasteFinishTest` and `FinishesTest`, and in `runClient` for "Undo restores a finished placement" and "Already brought back".

## 3. Availability and wheel

- [x] 3.1 Add the availability check (D5) and show "Pasting N %" / "Undoing N %" in the placement overlay. Verify in `runClient`: available in creative single player and when opened to LAN; not in survival; and on `runServer` not available ("Dedicated server").
- [x] 3.2 Add `Entry.PASTE` after Materials on ANCHORED and LOCKED, `Page.PASTE` with the three modes, `Entry.UNDO_PASTE` last on every page while undoable, greyed out while a job runs, the commands, `Selection` fields, icons, labels and language keys; HOLD mode leaves the paste sub-wheel open (D6). Extend `RadialMenuTest` and verify in `runClient` for "Locked placement in creative", "Survival", "Following placement", "Paste only into air", "Close without pasting", "After a paste", "Nothing to undo" and "Second paste while one runs".

## 4. Wrap-up

- [x] 4.1 Run the full `./gradlew :heavymatica:test` and fix failures; update `ROADMAP.md` (creative paste in single player done; step 7's server companion stays for multiplayer).
