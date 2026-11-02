## Why

The placement wheel works but looks like programmer art. It is drawn as flat translucent rows with stair-stepped edges.
The highlight snaps from one segment to the next, and pages swap instantly. Every wedge is crowded with an item icon and
its label. The wheel is the remote's main control and opens dozens of times per build session, so how it looks and
feels shapes the whole mod.

## What Changes

- **Glass look:** dark translucent segments with smooth (anti-aliased) edges and gaps, a soft rim light, a darker centre
  disc, and a light dim of the world around the wheel. The world dim leaves the game running and does not pause it.
- **Motion:**
  - The wheel opens with a short scale and fade, and its segments come in one after another.
  - The hovered segment slides outward and its colour blends in, rather than snapping.
  - An accent arc on the outer rim and a notch on the centre disc glide to the hovered segment. They are tinted red for
    danger entries and green for confirm entries.
  - Sub-pages (Layers, Options, confirms, ...) turn in.
  - Running a segment pops the ring away.
- **Information:**
  - Each segment keeps its icon with a short label under it on the ring.
  - The centre names the hovered segment and gives a one-line hint of what it does. With nothing hovered, it names the
    placement and its state.
  - The current option on a sub-page is marked with a small dot rather than a green fill.
- **Sound:** a soft tick when the hovered segment changes and a click when a segment runs or opens a page.
- **Settings:** new client settings `wheel.animations` and `wheel.sounds`, both on by default. With animations off, the
  wheel draws its final state at once and keeps the same layout.
- **Icons:** a set of 35 custom 16×16 sprites replaces the vanilla items now drawn for the 34 wheel entries.
  Unknown blocks has one sprite for shown and one for hidden. The sprites follow one shared palette, with cyan for
  ghost or plan, red for mistakes and danger, orange for missing and green for confirm.
- The mockups this was agreed from:
  - wheel: https://claude.ai/artifact/9334LVZaiZjkoaLKAxQzbA (Proposed, Glass, labels on ring)
  - icons: https://claude.ai/artifact/SnykoJFNVEjiEYoz5K2x5L

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client/radial-menu`: adds requirements for the hovered segment's centre hint, the hover and pick sounds, and the
  `wheel.animations` and `wheel.sounds` settings. What the segments are and what they do stays unchanged.

## Impact

- `remote/RadialMenuScreen`: rendering is rewritten, while input handling, `WheelMovement` and the dev hooks stay as
  they are.
- `remote/WheelGeometry`: keeps hit-testing. The span builder gives way to a mesh of feathered quads drawn on the
  vanilla GUI colour pipeline.
- New small helpers, all free of the game and unit-tested:
  - a wheel animation state (open, hover, accent angle, page swap, close)
  - the colour and easing maths
- A short HUD fade so the ring can pop away after the screen has already closed.
- `HeavymaticaConfig`: `wheel.animations` and `wheel.sounds`.
- Language files: a hint key per wheel entry.
- Assets: 35 sprite PNGs under `assets/heavymatica/textures/gui/sprites/wheel/`.
- No server, cloud or network changes.
