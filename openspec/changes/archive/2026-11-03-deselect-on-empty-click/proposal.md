# Proposal

## Why

There is no way to deselect a placement. Now that the wheel and the overlay depend on whether a placement is selected,
players need to be able to clear the selection.

## What Changes

- A right-click with the remote (not sneaking) on a spot with no ghost deselects the selected placement. The library
  opens on such a click only when nothing is selected. A following placement is still anchored by the right-click.
- The corner key hints show "right-click on empty space: deselect" for anchored and locked placements.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client/schematic-placement`: right-click on no ghost deselects first, and opens the library only when nothing is selected.

## Impact

`PlacementController.use`, `Placements.deselect`, the overlay hints, `en_us.json`, `PlacementControllerTest`.
