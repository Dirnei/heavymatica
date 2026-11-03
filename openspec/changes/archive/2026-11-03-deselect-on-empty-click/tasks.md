# Tasks

## 1. Deselect

- [x] 1.1 Return DESELECTED from `PlacementController.use` on a click on no ghost while a placement is selected, via a new `Placements.deselect`. Verify with `PlacementControllerTest.rightClickOnNoGhostDeselects`.
- [x] 1.2 Add the corner key hint "on empty space: deselect" for anchored and locked placements while the remote is held. Verify with `KeyHintRulesTest` and in `runClient`.
