## Context

- `placement/ComparisonState` holds one state per position of a locked placement, in 16³ sections, and every change
  goes through `set`, which already keeps totals and dirty box sections. `CellState.boxed()` is exactly the set of
  boxed states the pointer looks for in its mistakes mode.
- `targeting/TargetHud` draws the targeting HUD below the crosshair; the pointer goes right below it.
- The Options sub-wheel and its HOLD-mode handling come from the `easy-place` change.

## Goals / Non-Goals

**Goals:**
- Finding the nearest candidate costs next to nothing, even on a 200,000-block placement, and never scans the whole
  placement per frame.

**Non-Goals:**
- A beam, a world marker or a waypoint; a later change if the HUD arrow isn't enough.
- Pointing into other placements, or lists of all mistakes.

## Decisions

### D1: Per-section counts kept by `set`

`ComparisonState` gets two `int` arrays over its sections: boxed positions and missing positions, updated in `set` from
the old and new state (a decrement and an increment, no scans). Sections not yet compared count nothing.

- *Alternative: scan the state array on demand.* Rejected: a full scan is ~200,000 reads per refresh on large builds.

### D2: Nearest search over sections, nearest first

On refresh, list the sections with a non-zero count for the mode that overlap the visible layers' y range, ordered by
the distance from the eye to the section's box. Scan them in that order, cell by cell, filtering by state and layer, and
stop once the best candidate found is nearer than the next section's box distance. In practice that is one or two
sections (at most 4,096 cells each).

### D3: Refresh on a timer, render from the cached result

A client tick refreshes the result every 10 ticks (half a second), and right away when the mode, the selection or the
layer setting changes. Rendering only reads the cached candidate (position and state), so the HUD costs nothing per
frame beyond drawing. The cache holds the state as found at refresh, so a fixed mistake disappears at the next refresh.

### D4: Arrow from the view's yaw

The horizontal direction is the angle between the player's look yaw and the direction to the candidate's centre,
rounded to eight steps and drawn as an arrow (up = ahead, down = behind). The height difference is the candidate's y
minus the player's eye block y, shown as "N up" or "N down" when not 0. The state text and colour reuse `TargetLines`.

### D5: Setting and wheel

`HeavymaticaConfig` gets `targeting.mistakePointer` (`OFF`, `MISTAKES`, `MISTAKES_AND_MISSING`, default `OFF`, the
setter saves). `RadialMenu` gets `Entry.MISTAKES` appended to the Options page, marked when not off, opening
`Page.MISTAKES` with `MISTAKES_OFF`, `MISTAKES_ONLY` and `MISTAKES_AND_MISSING` (current marked) and a
`Command.SET_MISTAKE_POINTER`. `Selection` carries the mode.

## Risks / Trade-offs

- [Up to half a second of lag after fixing a mistake] → Acceptable for a direction hint; D3 refreshes at once on mode
  and selection changes.
- [Unloaded sections can't be compared, so mistakes there are unknown] → They have no counts, as their boxes don't
  show either; the pointer covers what the comparison knows.
- [The arrow only gives eight directions] → Enough to walk toward it; the distance shrinks as the player gets close and
  the targeting HUD takes over once the crosshair is on it.
- [Depends on `easy-place`'s Options sub-wheel] → Apply `easy-place` first; the tasks start by checking it is in.

## Migration Plan

Client only; the pointer is off by default.
