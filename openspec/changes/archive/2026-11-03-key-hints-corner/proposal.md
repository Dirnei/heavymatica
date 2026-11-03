# Proposal

## Why

The placement overlay mixes status (placement, layers, progress, shortages) with a list of key hints. Pinned without the
remote it is mostly status, and the hints make it tall and cover the world. Players also want to see the current layer
setting next to the layer keys while building.

## What Changes

- The side panel shows status only: placement, version, state badge, position, rotation and mirror, swaps, unknown
  blocks, layers, in-hand filter, progress, mistakes, finishing, shortages, unavailable warning, paste progress and
  capture status. It no longer lists key hints.
- A new key-hint list sits in the bottom-right corner while the remote is held, with the same state-dependent hints.
  For a LOCKED placement it starts with a header naming the layer setting and its layers, then the layer keys.
- Pinned without the remote, the corner shows only the layer header and layer-key hints of a LOCKED placement.
- With vanilla subtitles on, the list sits above the subtitle area. On narrow screens it lifts above the hotbar.
- **BREAKING**: the compact overlay setting is replaced by "Show key hints" (`overlay.keyHints`, default on). A player
  who had compact overlay on gets key hints off. The side panel always shows its full status.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client/placement-remote`: the overlay shows status only, the key hints move to a corner list with a layer header, and
  the compact overlay is removed.
- `client/settings-screen`: the HUD section offers "Show key hints" instead of the compact overlay.

## Impact

`remote/PlacementOverlay.java` (split), new `remote/KeyHints.java` (corner layer) and `remote/KeyHintRules.java` (pure
rules), `HeavymaticaConfig.java` (new key, migration), `HeavymaticaClient.java` (layer registration and migration call),
`ui/SettingsTable.java`, `en_us.json`, README, tests.
