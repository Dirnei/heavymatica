# Design

## Context

For motivation see proposal.md, Why. For behaviour see `specs/client/ghost-opacity` and `specs/client/radial-menu`.

The current state this builds on (with `radial-menu` archived and `material-swaps` implemented, not yet archived):
- **`HeavymaticaConfig`:** a NeoForge `ModConfigSpec` client config. `ghost.opacity` is
  `defineInRange("opacity", 0.45, 0.05, 1.0)`, read through `ghostOpacity()`. Nothing writes config values yet.
- **`GhostRenderer`:** reads `ghostOpacity()` once per frame and passes it to each draw as the colour modulator's
  alpha, so meshes don't bake the opacity in. The in-hand filter's dimmed hotbar blocks use
  `opacity * layersHotbarOpacity()`. Nothing caches the opacity across frames.
- **`RadialMenu`** (game-free): `Entry`, `Page` (MAIN, LAYERS, CONFIRM_REMOVE, CONFIRM_UNLOCK), `Segment(entry,
  enabled, current)`, `Command` (an enum) and `Outcome` (Stay, Close, Run(command)). Layers turns to the LAYERS page;
  `release` only answers on MAIN; the layer segments are `current` when they match the selection's layer mode.
- **`RadialMenuScreen`:** draws segments with an item icon (`icon(entry)`) and a translated name
  (`heavymatica.wheel.entry.<entry>`), and hands a `Run`'s command to a `Consumer<Command>`, which is
  `RemoteInput.run`.
- **`DevScreens`:** `wheelPoint=<entry>` finds a segment by entry name, `logWheel` logs the segments.

Unit tests run without Minecraft or NeoForge classes.

## Goals / Non-Goals

**Goals:**
- The opacity page follows the layers page's rules exactly (turning, release, centre, Esc, marking), so both
  sub-wheels behave the same.
- The steps' sanitising and matching are game-free and unit-tested.

**Non-Goals:**
- A config screen or direct keys for the opacity.
- Watching `opacitySteps` while the wheel is open; it is read each time the wheel opens.

## Decisions

### D1. Game-free steps

`placement.OpacitySteps` (game-free, static):
- `DEFAULT = [0.2, 0.4, 0.6, 0.8, 1.0]`
- `sanitize(List<? extends Number>)`: drops nulls, non-finite values and values outside 0.05..1, removes duplicates,
  sorts; returns `DEFAULT` unless 2 to 8 steps remain
- `matches(step, current)`: `|step - current| < 0.001`
- `percent(step)`: the whole percentage, `Math.round(step * 100)`

Duplicates are removed by exact value. Two values closer than the epsilon but not equal both stay, which is harmless:
both get marked, and the labels may read the same.

### D2. Config

- `ghost.opacity` keeps its range and gets default 0.4.
- `ghost.opacitySteps` is a `defineList` in the ghost section with the default steps and an element validator that
  accepts any `Number`. Range, duplicate and count checks are left to `OpacitySteps.sanitize`, so a single bad value
  is dropped instead of NeoForge resetting the whole list. TOML may read `1` as an integer, so the value is held as
  `List<? extends Number>`.
- `ghostOpacitySteps()` returns the sanitised list.
- `setGhostOpacity(double)` calls `set` and then `save` on the opacity value. `set` updates the cached value, so the
  next frame draws with it; `save` writes the file.

Alternative: a mod-side field holding the opacity and only writing the config on exit. Rejected: two sources of truth,
and a crash would lose the choice.

### D3. Wheel model

`RadialMenu` gains:
- `record Opacity(List<Double> steps, double current)`, passed to the constructor next to the selection, a snapshot
  like the selection
- `Entry.OPACITY` (main page, every state, before Library) and `Entry.OPACITY_STEP`
- `Page.OPACITY`: one `OPACITY_STEP` segment per step, `current` when `OpacitySteps.matches`
- `Segment` gains a `double value` (the step; 0 for other entries); the old three-argument constructor stays
- `Command.SET_OPACITY`, and `Outcome.Run` gains a `double value`; `Run(command)` stays and means value 0
- label `heavymatica.wheel.opacity` ("Ghost opacity") on the opacity page

Picking OPACITY turns to `Page.OPACITY` and stays. `release` already answers only on MAIN, so a HOLD release on
Opacity leaves the sub-wheel open and a click picks, like Layers. The centre and Esc already close.

The screen's consumer becomes `Consumer<Outcome.Run>`, so the value reaches `RemoteInput.run`, which calls
`HeavymaticaConfig.setGhostOpacity(value)` for `SET_OPACITY`.

Alternative: one enum constant per step. Rejected: the steps are configurable.

### D4. Screen

- Icons: `TINTED_GLASS` for Opacity and `GLASS_PANE` for the steps. Neither is used by another segment (Layers off
  uses `GLASS`).
- `name(Segment)` replaces `name(Entry)`: a step reads `heavymatica.wheel.entry.opacity_step` with its percentage
  (`%s%%`), other entries as before.

### D5. Development steps

`wheelPoint` also takes `<entry>@<n>`, the n-th segment of that entry (from 0), to reach a single step, for example
`wheelPoint=OPACITY_STEP@3`. `logWheel` already logs each segment's record, which now includes the value. A new
`logOpacity` step logs `ghostOpacity()` and `ghostOpacitySteps()`.

## Risks / Trade-offs

- [Saving the config from the wheel fires NeoForge's config reloading event, and the file watcher may reload it] →
  accepted: the value written is the one already cached.
- [Existing configs keep 0.45, which is no step, so nothing is marked until a step is picked] → accepted, and
  specified.
- [Exact-value deduplication keeps near-equal steps] → harmless, see D1.

## Migration Plan

No saved data migration; NeoForge adds `ghost.opacitySteps` with its default to existing config files. Archive after
`material-swaps`, whose wheel segment lists the `client/radial-menu` delta builds on.
