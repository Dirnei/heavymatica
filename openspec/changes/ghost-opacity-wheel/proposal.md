# Proposal

## Why

How see-through the ghosts should be depends on the moment: faint while walking around a finished build, solid while
reading a detailed facade. Today the opacity is only a value in the client config file, so changing it means leaving
the game or living with one value. The remote's wheel already gathers quick actions, so it is the natural place to
switch the opacity with one flick.

## What Changes

- **Opacity segment on the wheel.** The wheel shows an Opacity segment in every state, also with no placement
  selected, since the opacity is not tied to a placement. It sits before Library.
- **Opacity sub-wheel.** Picking Opacity swaps the wheel to one segment per configured step, labelled as a
  percentage such as `40%`. The step matching the current opacity is marked, like the current layer setting.
  Picking a step sets the ghost opacity and closes the wheel. Esc or the centre close it and change nothing. In HOLD
  mode, releasing the wheel key on Opacity leaves the sub-wheel open and a click picks, like Layers.
- **One global, saved opacity.** A picked step is written to the client config (`ghost.opacity`) and saved, so it
  persists across restarts. Every ghost, and the in-hand filter's dimmed hotbar blocks (a share of it), change at
  once.
- **New default.** `ghost.opacity` defaults to 0.4 instead of 0.45, so the default matches a step. Existing configs
  keep their saved value.
- **Configurable steps.** A new client setting `ghost.opacitySteps`, default `[0.2, 0.4, 0.6, 0.8, 1.0]`. Values
  outside 0.05 to 1 are dropped, duplicates removed and the rest sorted. If fewer than 2 or more than 8 steps
  remain, the default steps are used.
- **No match, no mark.** When the current opacity is not one of the steps (for example 0.45 from an older config),
  no step is marked.

Out of scope:
- a direct key for the opacity or for stepping through it
- per-placement opacity
- changing the in-hand filter's hotbar share from the wheel

## Capabilities

### New Capabilities
- `client/ghost-opacity`: the global ghost opacity setting with its default and persistence, the `opacitySteps`
  setting with its sanitising and fallback, and the wheel's Opacity sub-wheel.

### Modified Capabilities
- `client/radial-menu`: the wheel's segment lists gain Opacity before Library in every state, also with no
  placement selected. The delta builds on the segment lists of `material-swaps`, which adds Materials.

## Impact

- **Mod code only (`src/mod/heavymatica`):**
  - the client config: the new default and `ghost.opacitySteps`, plus setting and saving the opacity
  - a game-free class for the steps' sanitising, matching and labels
  - the `remote` package: the Opacity entry and page in the wheel model, its icon and labels in the wheel screen, and
    the command run in `RemoteInput`
  - language keys, and the wheel's development steps reaching the step segments
- **Saved data:** the client config gains `ghost.opacitySteps`; NeoForge adds it with its default to existing files.
- **Depends on `material-swaps`**, whose wheel segment lists this builds on. Archive order: `material-swaps` →
  `ghost-opacity-wheel` → `block-equivalents`.
- No cloud, API or server changes.
