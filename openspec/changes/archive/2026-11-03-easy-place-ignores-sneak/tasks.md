# Tasks

## 1. Easy Place ignores sneaking

- [x] 1.1 Drop sneaking from `EasyPlaceClick` and `BuildRules.easyPlaces`. Verify with `EasyPlaceTest`.
- [x] 1.2 In `EasyPlace.send`, wrap the click in the click's sneak state whenever it differs from the player's, restoring the real input after. Verify in `runClient`: sneaking onto the second half of a planned double chest connects it.
