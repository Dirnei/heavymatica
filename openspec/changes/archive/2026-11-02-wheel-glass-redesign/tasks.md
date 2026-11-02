## 1. Rendering spike

- [x] 1.1 Prove D1 on 26.1.2: from `RadialMenuScreen`, submit one custom GUI element state on the vanilla position-colour GUI pipeline that draws a feathered annular strip with per-vertex colours. Check that `graphics.pose()` translate, scale and rotate apply to it, and also to a GUI sprite blit with a colour argument (D8). Verify with a `runClient` dev screenshot at GUI scale 2 and 4 that the edges are smooth and a test sprite stays sharp. If the mesh cannot be done, switch the design to the dynamic-texture fallback before going on.

## 2. Mesh and animation model

- [x] 2.1 Add `WheelMesh`: annular sectors with constant-width gaps, radial colour gradients, feathered edges `1 / guiScale` wide, a full disc, an arc with glow, and the world-dim ring. The output is a quad list in GUI units. Verify with `WheelMeshTest`: vertices lie on the expected radii and angles, gaps keep their width at inner and outer radius, feather vertices have alpha 0, and a single segment has no gap.
- [x] 2.2 Add `WheelAnimation` with the timings in D2: open, staggered arrival, hover easing, accent angle and visibility, page change, and close. The stagger is capped so a full wheel finishes within 250 ms. Durations are 0 when animations are off. Verify with `WheelAnimationTest` on a fake clock: values at 0, mid-way and the end; everything is finished by 250 ms for 8 segments; the accent takes the shortest way across 12 o'clock; with animations off, every value is at its target at t = 0.
- [x] 2.3 Put the colours and entry kinds (normal, danger, confirm) from D7 in one place. Verify with a unit test that every `RadialMenu.Entry` has a kind, and that Remove, Cancel, Cancel capture, No and Undo paste are danger, and Yes and Bring back are confirm.
- [x] 2.4 Export the 35 sprites from the icon mockup's drawing script to 16×16 PNGs under `assets/heavymatica/textures/gui/sprites/wheel/`, named as in D8. Use a one-off script outside the repo; commit only the PNGs. Add `WheelIcons`, which maps an entry (plus the shown state for Unknown blocks) to its sprite id. Verify with `WheelIconsTest`: every `RadialMenu.Entry` maps to a sprite, every mapped sprite has a PNG in the resources that is exactly 16×16, and no PNG in the folder goes unused.

## 3. Settings and hints

- [x] 3.1 Add `wheel.animations` and `wheel.sounds` (both default true) to `HeavymaticaConfig`, with comments. Verify in `runClient` that a fresh config has both keys set to true, and that setting them to false survives a restart (spec: "Settings survive a restart").
- [x] 3.2 Add `heavymatica.wheel.hint.<entry>` for every entry, and `.disabled` reasons where a segment can be greyed out, to `en_us.json`. Add to `RadialMenu`, read-only, what the screen needs to pick the disabled reason. Verify with `RadialMenuTest` cases for the disabled reasons, and a test that every entry has a hint key in `en_us.json`.

## 4. Glass wheel screen

- [x] 4.1 Rewrite drawing in `RadialMenuScreen` on `WheelMesh` and `WheelAnimation`. This covers:
  - the world dim, glass segments, rim, centre disc, accent arc and centre notch
  - the hover slide-out, the open scale and fade, and the page turn
  - sprite icons from `WheelIcons` (D8), and the opacity-step pane filled to its step
  - ring labels that shrink or get cut to fit (D5)
  - the current-option dot, and greyed-out segments faded
  - the centre name and hint, or the placement and state
  - the inner radius set to `outer * 9 / 20`

  `segmentAt`, `segmentPoint` and all input handling stay as they are. Delete the span builder from `WheelGeometry` and its tests, and the vanilla item icons from `RadialMenuScreen`. Verify with `./gradlew :heavymatica:test`, and in `runClient` with dev screenshots of the main page, Options, a confirm and the capture wheel at GUI scale 2 and 4. Check them against the mockup.
- [x] 4.2 Add `WheelAfterglow` (D3): on close, the screen's last ring scales to 107% and fades out over 170 ms in a HUD layer, and is skipped while another screen is open. Verify in `runClient`: Lock shows the pop-away, and Library opens the library with no ring drawn over it.
- [x] 4.3 Add the sounds (D6): a tick on each hover change, limited to one per 30 ms, and a click on run or page open. No click for the centre, Esc or a greyed-out segment. Nothing plays when `wheel.sounds` is off. Verify in `runClient` against the three "Wheel sounds" scenarios, and tune pitch and volume there.
- [x] 4.4 Check the "Wheel animations setting" scenarios in `runClient` with the dev scripts:
  - press the wheel key, `wheelPoint=LIBRARY`, `wheelRelease` within 100 ms opens the library
  - with `wheel.animations` false, the wheel is fully drawn on the first frame

  Also check the "Segment labels and centre hint" and "Current option mark" scenarios in both HOLD and TOGGLE mode.

## 5. Wrap-up

- [x] 5.1 Run the full `./gradlew :heavymatica:test` and fix any failures. Add the two settings to the README's client config notes, if it lists settings, and mark the wheel redesign in `ROADMAP.md` if it is listed there.

## Workflow follow-up

- Archive `easy-place`, `mistake-pointer` and `creative-paste` before this change, since all of them touch `client/radial-menu`.
