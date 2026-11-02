## Why

The comparison counts mistakes per colour, but on a large build a red count of 3 somewhere in 200,000 blocks, or the
last few missing blocks at the end, are hard to find: the player walks around looking for boxes. Litematica's verifier
answers "where is it?"; Heavymatica has the data for that already and only lacks a pointer.

## What Changes

- A mistake pointer on the HUD: while it is turned on, a small arrow below the crosshair points toward the nearest
  position of the selected locked placement that needs attention, with its distance, its height difference and its
  state in the comparison colours. When nothing is left, it says so.
- Two modes: **Mistakes** (every boxed state: wrong block, wrong orientation, too many, not enough yet, wrong water,
  extra, needs finishing) and **Mistakes and missing** (also positions still missing their block). Only the visible
  layers count.
- Chosen in two steps on the wheel: the Options sub-wheel gets a Mistakes segment, which opens a sub-wheel with Off,
  Mistakes, and Mistakes and missing, the current one marked. The mode is a client setting, off by default, saved in the
  config.
- A first version to try out: direction on the HUD only, no beam or world marker. If that isn't enough, a later change
  can add one.

## Capabilities

### New Capabilities

- `client/mistake-pointer`: finding the nearest position that needs attention in the selected locked placement and
  pointing to it on the HUD, with its modes and setting.

### Modified Capabilities

- `client/radial-menu`: a Mistakes segment in the Options sub-wheel and a Mistakes sub-wheel. The Options sub-wheel
  comes from the `easy-place` change, so this change is applied after `easy-place`.

## Impact

- Mod: per-section counts of boxed and missing positions in `placement/ComparisonState`, a nearest-position search, a
  HUD element next to `targeting/TargetHud`, the wheel (`remote/RadialMenu`, `RemoteInput`), `HeavymaticaConfig` and the
  lang file.
- No cloud, web, format or server changes.
- Depends on `easy-place` (Options sub-wheel).
