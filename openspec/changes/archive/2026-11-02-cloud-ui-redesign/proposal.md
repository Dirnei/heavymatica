## Why

After `ui-glass-redesign`, the library and every alpha screen use the glass look, but the four cloud screens (account,
first sign-in choice, link code, upload target) are still vanilla buttons on a plain background. The cloud isn't
public yet, but it is used for testing, and the look should be the same throughout so the cloud can be switched on
without another round of UI work.

## What Changes

- **Account screen:** a full screen with Back to the library.
  - A profile panel shows the skin, the name, the signed-in state, the storage bar with usage against quota, and Sign
    out with a line saying what signing out removes.
  - A sign-ins panel lists every linked sign-in, marks the one in use, and offers Unlink… on each and "Link another
    sign-in".
- **First sign-in choice:** a dialog over the library with two large choices, create a new account or join one you
  have (only when joining is available), and a line saying the Minecraft login never goes to Heavymatica.
- **Link code:** a dialog with the QR code, the code in large character boxes as two groups of four, a ring showing
  the time left, and New code. When a code is redeemed, the confirmation (what wants to link, Link or Decline)
  appears in the same dialog.
- **Upload target:** a dialog over the library with two choices, as a new schematic or as a new version of an
  existing one. The second has a searchable list of cloud schematics showing the version it will become. A summary
  line says what the upload becomes.
- **Sign-in states in the library header:** a "Sign in" button while signed out, a signing-in state, and sign-in
  failures explained in a glass dialog rather than plain text.
- Mockups: https://claude.ai/artifact/FWfALSST927P584wiuFGs6 (Cloud boards).
- What these screens do stays as it is. Mockup extras that were never agreed on (folder and tags while uploading,
  cache and sync tiles on the account screen) are not part of this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. The behaviour in `client/cloud-account` and `client/schematic-import` stays the same. The screens follow
`client/screen-layout`, which `ui-glass-redesign` adds.

## Impact

- `ui/AccountScreen`, `AccountChoiceScreen`, `LinkCodeScreen`, `LinkCodeText`, `QrView`, `UploadTargetScreen`:
  rebuilt on the glass widget kit and `GlassDialogScreen` from `ui-glass-redesign`.
- `ui/LibraryScreen` header: the signed-out and signing-in states of the account chip.
- `DevScreens`: hooks for these screens updated.
- Language files: a few new keys.
- Depends on `ui-glass-redesign`, applied first. No changes to the cloud service, its API or the network.
