## Context

- `RadialMenuScreen` draws the ring as cached 1-pixel `graphics.fill` rows from `WheelGeometry.spans`. It draws vanilla
  item icons with `graphics.item` and text with `graphics.centeredText`. Hit-testing is angular
  (`WheelGeometry.segmentAt`), with the inner radius as the dead zone.
- What the wheel shows and does lives in `RadialMenu`, which is free of the game and covered by `RadialMenuTest`.
  This change does not touch it, apart from read-only additions for hints and disabled reasons.
- Dev scripts aim at segments through `RadialMenuScreen.segmentPoint` (`DevScreens` `wheelPoint`). They have to keep
  working.
- On "Run" the screen calls `onClose()` before the command runs. Library and Materials then open their own screens.
  Other commands return to the game with no screen open.
- No code in the mod uses the GUI pose stack or submits custom GUI geometry yet. Both are new ground on MC 26.1.2's
  `GuiGraphicsExtractor`.
- The look, layout and timings were agreed on the mockup linked in the proposal (Proposed, Glass, labels on ring).

## Goals / Non-Goals

**Goals:**
- Smooth edges at every GUI scale, with no per-frame rebuilding on the CPU beyond a few hundred vertices.
- Every animation is pure maths on a clock, so it can be unit-tested and switched off by setting each duration to zero.
- Hit areas, input timing and dev hooks stay exactly as they are.

**Non-Goals:**
- Custom sound assets, or a blurred world behind the wheel.
- Animated sprites, or more than one sprite for an entry other than Unknown blocks.
- Changing which segments show or what they do.
- Theming or colour settings beyond `wheel.animations` and `wheel.sounds`.

## Decisions

### D1: Draw the ring as a mesh of coloured quads on the vanilla GUI pipeline

Each segment is an annular sector cut into thin quads along its arc, with the vertex count scaled to the arc length.
Every vertex carries its own colour, so the radial gradient (darker inside, lighter outside) costs nothing extra. Edges
are smoothed with a feather: a strip one physical pixel wide (`1 / guiScale` GUI units) along every outer, inner and
gap edge, whose outer vertices have alpha 0. The centre disc, the rim light, the accent arc and its glow, and the world
dim are all strips of the same kind. The glow is a wider feather that fades out, and the world dim is a large ring
from dark to lighter.

A new `WheelMesh`, free of the game, builds the quads in GUI units from radii, angles, offsets and colours. The screen
hands them to the GUI renderer as one custom GUI element state on the vanilla position-colour GUI pipeline. No shader
or pipeline is added.

- *Alternative: per-pixel coverage through 1-pixel fills*, the span approach with partial alpha. Rejected: it is
  limited to GUI pixels, so edges stay blocky at GUI scale 3 and above, and it cannot slide, scale or turn segments
  cheaply.
- *Alternative: rasterise into a dynamic texture each frame.* This is the fallback if custom GUI geometry turns out to
  be blocked on 26.1.2 (task 1.1). It costs about 0.6 million pixels per frame at GUI scale 4, so it would redraw only
  while something animates.

### D2: One animation model, driven by a clock

A new `WheelAnimation`, free of the game, takes the current time in milliseconds and holds:

- **Open:** scale from 0.88 to 1 with a slight overshoot over 220 ms, and fade in over 170 ms.
- **Segment arrival:** each segment fades and scales in over 180 ms. The stagger between segments is
  `min(22 ms, 60 ms / count)`, so that a full wheel finishes within 250 ms.
- **Hover:** each segment eases towards 1 or 0 with a 50 ms time constant. It drives the slide-out (5 GUI units),
  the icon growing by 18%, the colour of the hover fill and the brightness of the rim.
- **Accent:** the angle eases along the shortest way round to the hovered segment's middle with a 45 ms time constant.
  Its visibility fades in or out with a 45 ms time constant, and its tint comes from the hovered entry's kind.
- **Page change:** the old page turns away by -0.22 rad, shrinks by 8% and fades out over 150 ms. The new page turns in
  from +0.22 rad over 220 ms, and its segments arrive as on open.

When `wheel.animations` is off, every duration and time constant is 0 and every value is at once at its target. The
screen asks the model for values each frame and never waits on it, so input is never held back.

- *Alternative: tween objects per effect.* Rejected: one model with one clock is easier to test and to switch off.

### D3: Pop-away after the screen closes, in a HUD layer

Running a segment still closes the screen at once, so commands and the next screen are as fast as today. On close, the
screen hands its last mesh and centre to a small HUD layer, `WheelAfterglow`. For 170 ms the layer draws it scaling to
107% and fading out. If another screen has opened meanwhile, such as the library or the materials screen, the layer
draws nothing.

- *Alternative: keep the screen open for 170 ms before running the command.* Rejected: it delays every action, which
  breaks the "Wheel animations setting" requirement.

### D4: Hit-testing and dev hooks do not move

`hovered()` keeps using `WheelGeometry.segmentAt` with the inner radius as the dead zone. The slide-out, the
scaling and the page turn are visual only. `segmentPoint` keeps returning the resting middle of a segment. The inner
radius changes from `outer * 2 / 5` to `outer * 9 / 20`, which makes room for the centre hint. The dead zone follows
that inner radius.

### D5: Labels on the ring, hints in the centre

- **Ring labels:** each icon is the entry's sprite (D8), drawn 4 GUI units above the middle radius, and its label sits
  under it. A label wider than the segment's chord at that radius is scaled down to 75%. If it still
  does not fit, it is cut with "…".
- **Centre with a segment hovered:** the name is drawn at full size, tinted with the accent for danger and confirm
  entries. The hint is wrapped at 0.75 scale to the centre's width, at most 3 lines.
- **Centre with nothing hovered:** it shows `menu.label()` with a smaller line under it:
  - on the main page, the placement's state, "Pick one from the library" with nothing selected, or the number of
    corners set in capture mode
  - on a confirm page, that Esc or the centre backs out
  - on other sub-pages, nothing.

  `RadialMenu` gains read-only `selection()` and `capture()` for this.
- **Language keys:** hints come from `heavymatica.wheel.hint.<entry>`, and greyed-out reasons from
  `heavymatica.wheel.hint.<entry>.disabled`. Each entry is greyed out for only one reason (no placement selected,
  corners not set, or a paste running), so the entry alone picks the key.
- **No title above the ring:** the centre already carries `menu.label()`, so the old title above the ring is dropped.
- **Current option:** a 2-unit dot in the accent colour, just inside the outer edge, replaces the green fill.
- **Greyed-out segments:** drawn at half alpha, and their sprite at 45% alpha.

### D6: Sounds from vanilla UI events

Both sounds use `SimpleSoundInstance.forUI`, so only this player hears them and no assets are added:

- **Hover tick:** `SoundEvents.UI_BUTTON_CLICK` at pitch 2.0 and volume 0.12.
- **Pick click:** `SoundEvents.UI_BUTTON_CLICK` at pitch 1.4 and volume 0.3.

No click plays for a greyed-out segment, for the centre, or for Esc. The exact values get tuned in `runClient`. The
hover tick is limited to one every 30 ms, so that a fast sweep does not buzz.

### D7: Colours

These are constants in one place:

| Use | Colour |
| --- | --- |
| Segment body | `#0E1218` at 78% (inside) to `#1C232C` at 80% (outside) |
| Rim | white at 7%, rising to 37% with hover |
| Centre disc | `#080B0F` at 86% |
| World dim | `#04060A` at 38% (centre) to 12% (edge) |
| Accent | `#7FD8FF` |
| Danger | `#FF6B6B` |
| Confirm | `#7EE28F` |
| Hover fill | the accent at 10% (inside) to 42% (outside), times the hover value |

Danger entries are Remove, Cancel, Cancel capture, No and Undo paste. Confirm entries are Yes and Bring back.

### D8: Custom sprites from the GUI sprite atlas

**Files and atlas.** Each wheel icon is a 16×16 PNG under `assets/heavymatica/textures/gui/sprites/wheel/`, named
after its entry in lower case. Unknown blocks has `unknown_blocks_shown` and `unknown_blocks_hidden`. The game stitches
everything under `gui/sprites` into the GUI atlas by itself, so no atlas JSON is needed.

**Drawing.** The screen draws each sprite with the GUI sprite blit at 16×16 GUI units, one sprite pixel per GUI pixel,
so it stays sharp at every GUI scale. Scaling and fading go through `graphics.pose()` and the blit's colour argument.
A single `WheelIcons` map turns an entry, plus the shown state for Unknown blocks, into a sprite id. It replaces
`RadialMenuScreen.icon`.

**Opacity steps.** These use one sprite, `opacity_step`: an empty pane with a frame and a checker pattern. The screen
fills it from the bottom to `round(12 * step)` pixels with the ghost cyan, plus a one-pixel highlight row. The steps
come from the config (2 to 8 values), so a sprite per step would not cover them all.

**Source.** The pixel art is defined in the icon mockup (proposal), as a small drawing script plus an automatic
outline and bevel. The PNGs are exported from it once and committed. After that, the PNGs are the source. Later
edits happen on the PNGs, and the mockup is not kept in sync.

- *Alternative: keep vanilla items.* Rejected: their meanings fit poorly (a barrier for Remove, dyes for pointer
  modes), and the user chose the custom set on the mockup.
- *Alternative: one sprite sheet with UV offsets.* Rejected: the GUI atlas already packs single sprites, and single
  files are easier to edit.

## Risks / Trade-offs

- [Custom GUI geometry may not be reachable from a NeoForge mod on 26.1.2] → Task 1.1 is a spike that draws one
  feathered quad strip before any other work. If it fails, fall back to the dynamic-texture renderer in D1.
- [A resource pack or a later edit leaves an entry without a sprite] → The atlas test (task 2.4) fails the build when a
  sprite file is missing. At runtime the game's missing-texture sprite shows, which is visible but harmless.
- [The paste-mode sprites, Only into air and Skip air, are hard to tell apart] → The labels on the ring carry the
  difference. Redraw them if testers mix them up.
- [Long labels in other languages] → Scale down, then cut with "…" (D5). Only `en_us` exists today.
- [Frame cost] → The mesh is about 8 segments × 3 strips × some 24 steps, a few thousand vertices at most. It is rebuilt
  only while something animates and cached otherwise.
- [Other unarchived changes also touch `client/radial-menu`] → This delta only adds requirements. Archive
  `easy-place`, `mistake-pointer` and `creative-paste` before this one.

## Migration Plan

The two new config keys default to on. Existing configs pick them up on first load. Nothing else migrates.
