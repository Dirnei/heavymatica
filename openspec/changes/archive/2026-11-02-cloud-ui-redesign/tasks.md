## 1. Account screen

- [x] 1.1 Add a game-free `AccountLayout` (profile panel, sign-ins panel) and verify it in `ScreenLayoutTest` at 427 × 240, 640 × 360 and 960 × 540: no overlap, everything on screen.
- [x] 1.2 Rebuild `AccountScreen` on the glass kit: Back to the library; skin, name, signed-in state, quota bar with usage against quota, Sign out with its explanation line; the sign-in list with "in use" and Unlink… (confirmation as a `GlassDialogScreen`); and "Link another sign-in". Verify in `runClient` the `client/cloud-account` scenarios for showing and unlinking sign-ins, including that the last sign-in can't be unlinked.

## 2. Dialogs

- [x] 2.1 Rebuild `AccountChoiceScreen` as a `GlassDialogScreen` over the library: two large choices (join only when available), the Mojang line, and Not now, which creates nothing. Verify in `runClient` the "First sign-in choice" scenarios of `client/cloud-account`.
- [x] 2.2 Rebuild `LinkCodeScreen` as a dialog: QR in whole pixels on a light panel (D2), the code as two groups of four boxes, the time-left bar with `m:ss`, New code, and the confirmation (what wants to link, Link or Decline) inside the same dialog. Verify with a unit test that the code splits into two groups of four, and in `runClient` the "Link code from the library", "Joining an existing account from the game" and "Linking needs confirmation in the game" scenarios.
- [x] 2.3 Rebuild `UploadTargetScreen` as a dialog over the library: as new or as a new version, a searchable list with folder and `vN → vN+1` on the selected row, and a summary line built from the chosen target (D4). Verify with a unit test for the summary text in both modes, and in `runClient` the "Upload succeeds", "Upload rejected" and "Update an existing schematic" scenarios.

## 3. Sign-in states

- [x] 3.1 Add the account chip states of D3 to the library header, and a `GlassConfirmScreen` for `SignInFailure` with the reason, Retry and Done. Verify in `runClient` the "Sign-in failures explained", "Expired session handled quietly" and "Account status visible" scenarios (offline-mode game for a failure, a revoked session for expiry).

## 4. Integration

- [x] 4.1 Update `DevScreens` hooks for these screens and take dev screenshots of each at GUI scale 2 and 4 in an 854 × 480 window. Check them against the Cloud boards of the mockup canvas and against `client/screen-layout`. Run `./gradlew :heavymatica:test` and fix any failures.

## Not checked in the game

- Unlinking, uploading and confirming a link were only drawn, never carried out, so the shared dev cloud got no new
  links or schematics. Those flows are unchanged behind the new screens.
- The expired-session hover hint and the signing-in pip were not captured; a failed sign-in with Retry and Done was.
- DevScreens needed no change: its link code, redeem and button-click steps work on the new screens.
