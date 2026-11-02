# Tasks

## 1. Counts and search

- [x] 1.1 Check that the `easy-place` change's Options sub-wheel is implemented (`RadialMenu` has the Options page); if not, stop and tell the user this change waits for it.
- [x] 1.2 Add per-section boxed and missing counts to `ComparisonState`, kept in `set` (D1). Verify with `ComparisonStateTest` cases: counts rise and fall as states change between missing, matching and each boxed state, and uncompared sections count nothing.
- [x] 1.3 Add the nearest-candidate search over sections (D2) as a pure function of the state, the eye position, the mode and the visible layer range. Verify with a new test: nearest of two mistakes, missing ignored in mistakes mode and found in the other, hidden layer skipped, a nearer candidate in a farther-sorted section edge case, and none.

## 2. Pointer

- [x] 2.1 Add `targeting.mistakePointer` to `HeavymaticaConfig` (default off, setter saves) and the pointer's refresh tick (every 10 ticks, at once on mode, selection or layer changes) with the cached candidate (D3, D5). Verify in `runClient` that a fresh config has it off and the setting survives a restart.
- [x] 2.2 Draw the pointer HUD below the targeting HUD: eight-step arrow, "N up / N down", distance, and the state in the comparison colours via `TargetLines`; "no mistakes" or "no mistakes and nothing missing" when empty; nothing when off, a screen is open or the selected placement isn't locked (D4). Add the language keys. Verify with a unit test of the arrow step and height text from yaw and positions, and in `runClient` for "Two mistakes", "Fixed mistake", "Mistake behind the player", "Hidden layer", "Clean build in mistakes mode", "Missing blocks counted" and "No locked placement selected".

## 3. Wheel

- [x] 3.1 Add `Entry.MISTAKES` at the end of the Options page (marked when not off), `Page.MISTAKES` with Off, Mistakes, and Mistakes and missing (current marked), `Command.SET_MISTAKE_POINTER`, the mode in `Selection`, icons, labels and language keys; picking closes the wheel; HOLD mode leaves the Mistakes sub-wheel open (D5). Extend `RadialMenuTest`, and verify in `runClient` for "Options shows Mistakes", "Marked while on", "Turn on with missing blocks", "Turn off" and "Close without a change".

## 4. Wrap-up

- [x] 4.1 Run the full `./gradlew :heavymatica:test` and fix failures; update `ROADMAP.md`'s Litematica gaps (go to mistake done as a HUD pointer).

## Workflow follow-up

- Archive after `easy-place`, since this change adds to the Options sub-wheel that `easy-place` introduces.
