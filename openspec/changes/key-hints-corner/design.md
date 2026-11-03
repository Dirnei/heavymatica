# Design

## Context

`PlacementOverlay` builds one list of `Line`s per frame: status lines, a separator, then key hints, all in one glass panel
on the configured side. Which hints show depends on the remote, the selection's state, pending remove/unlock
confirmations, capture mode and which wheel-action keys are bound. Most of it uses `KeyMapping` and `Component`, which
the unit tests cannot load. `OverlayRules` already holds the pure "does the panel show" rule.

Vanilla's `SubtitleOverlay` (26.1.2) draws each subtitle row centred at `guiHeight - 35 - row * 10`, 9 high, so row 0
spans `guiHeight - 40` to `guiHeight - 30`, and further rows stack upwards. The number of rows is private and changes
every frame.

## Goals / Non-Goals

**Goals:** a status-only side panel; a corner hint list; testable rules for which hints show; keep a player's compact
choice.

**Non-Goals:** moving the targeting HUD or the build-options row; showing the menu hint without the remote, even though
the wheel now opens without it (the corner without the remote stays limited to layers).

## Decisions

### Pure hint rules, drawn by a separate GUI layer
`KeyHintRules` takes a `Situation` record (remote held, capture state and corners, whether capture is busy, selection
state, layer mode, pending remove/unlock, and the set of wheel-action keys that are bound) and returns the list of
`Hint` values in display order, plus whether the layer header leads. `KeyHints` is a new GUI layer that maps each `Hint`
to its key name and lang text, and draws them. `PlacementOverlay` keeps the status lines only, so `layerHints`,
`boundHint` and the hint branches leave it. Alternative: keep one class with a second render method. Rejected, because
the hint branches are the part worth unit-testing, and they need to be free of the game.

### Header text
The header reuses the layer range that `PlacementOverlay.layersText` computes (`Layers.visible`). It gets its own keys:
`Layers: off`, `From-to: layers %s-%s of %s` and `Follow feet +%s: layers %s-%s of %s`. The side panel keeps its
existing layers line.

### Placement in the corner
`KeyHintRules.bottom(guiWidth, guiHeight, listWidth, subtitles)` returns the y of the list's bottom edge:
- `guiHeight - 4` by default.
- With subtitles on, `guiHeight - 30 - 4 * 10 - 4`, which reserves four subtitle rows above vanilla's lowest row.
- Also lifted to that height when the list's left edge would reach within 4 px of the hotbar's right edge
  (`guiWidth / 2 + 91`, plus 29 for an offhand slot).

Four rows cover the usual case. More simultaneous subtitles may still overlap, which is acceptable for a soft UI hint.
Reflection into `SubtitleOverlay` for the live count was rejected as brittle.

### Look
The list is one glass panel like the overlay, with smaller padding. Each hint is a keycap in the accent colour on the
left, then the muted action text, and every row is right-aligned. The header row uses the locked accent colour. Pending
warnings ("press again") use the warning colour.

### Setting migration
The new `overlay.keyHints` setting defaults to true. `overlay.compact` stays in the spec as a legacy value, listed as
file-only in the settings table, with a comment saying it is replaced. On client setup, `HeavymaticaConfig.migrate()`
does the following when `compact` is true: it sets `keyHints` to false and `compact` to false, then saves. It runs once,
because `compact` is false afterwards. The pure decision is `KeyHintRules.keyHintsAfterMigration(compact, keyHints)`.
Alternative: rename only the label of `compact`. Rejected, because a toggle labelled "Show key hints" would read
inverted. Dropping the old key outright was rejected because it would lose the player's choice.

## Risks / Trade-offs

- [Two HUD elements instead of one] → The corner is small and quiet, and only shows with the remote or for a pinned
  locked placement.
- [Mods drawing in the bottom-right corner] → The list moves up with subtitles only. Other mods may overlap it, and
  turning the key hints setting off is the escape hatch.
