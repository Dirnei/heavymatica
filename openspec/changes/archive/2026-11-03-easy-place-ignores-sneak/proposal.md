# Proposal

## Why

With Easy Place on, a sneaking right-click on a ghost goes to vanilla. Sneaking changes what vanilla places: a chest
connects only to the chest whose side is clicked, so a planned double chest ends up as two single chests. Easy Place
should always place what the plan has.

## What Changes

- A right-click on a ghost with a candidate item is an Easy Place click whether or not the player sneaks.
- When the click Easy Place picks needs a different sneak state than the player's, Easy Place tells the server that
  sneak state for that click only, then the real one again.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client/easy-place`: sneaking no longer bypasses Easy Place.

## Impact

`BuildRules.easyPlaces` and `EasyPlaceClick`, `EasyPlace.send`, `EasyPlaceTest`.
