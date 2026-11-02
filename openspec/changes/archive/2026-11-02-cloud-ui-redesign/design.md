## Context

- `ui-glass-redesign` adds the glass widget kit (`ui/glass`: `GlassMesh` and the `Glass` drawing helper, buttons, edit
  boxes, `GlassDialogScreen` with its parent kept, `GlassConfirmScreen`), game-free layout records, and the library
  header with an account chip. Sign out already moved from the library header to the account screen there. This
  change uses all of them and adds one widget, `ChoiceCard`, for the large choices.
- `CloudAccount.Status` has `SIGNED_OUT`, `SIGNING_IN`, `SIGNED_IN`, `CHOOSING` and `EXPIRED`. `SignInFailure` names
  the failure reasons the spec distinguishes. The library header switches on these today with one vanilla button.
- `LinkCodeScreen` draws the QR code with `QrView` and `QrMatrix`, counts down the time left and shows the confirmation
  when a code is redeemed. `LinkCodeText` formats the hints.
- `UploadTargetScreen` offers "as new" or "as version of" through a vanilla list.

## Goals / Non-Goals

**Goals:**
- The four cloud screens and the header's sign-in states in the glass look, following `client/screen-layout`.
- No behaviour change. Every existing scenario in `client/cloud-account` and the upload scenarios in
  `client/schematic-import` keep passing as they are.

**Non-Goals:**
- New cloud features: folder or tags while uploading, cache or sync figures on the account screen, a sign-up gate.
- Changes to `cloud-availability`'s rules for when the cloud parts show.

## Decisions

### D1: Account is a screen, the rest are dialogs

The account screen has enough content (profile, quota, sign-ins) to be a full screen with Back, like the details page.
The first sign-in choice, link code and upload target are short decisions about the screen behind them, so they are
`GlassDialogScreen`s over the library or the account screen. When a link completes or a dialog is cancelled, the
player is back where they started, with the selection kept.

### D2: Link code as character boxes, QR in whole pixels

The code is shown as two groups of four boxes, using the spec's 8-character format, so it's easy to read out and type
on another device. The QR code keeps `QrMatrix` and is drawn as whole-pixel module runs on a light rounded panel, so
it stays sharp at every GUI scale. The time left is a thin bar plus `m:ss` text: the kit has no ring shape, and a bar
reads as well at this size. The confirmation (what wants to link, Decline, Confirm) appears in a box at the bottom of
the same dialog, with the code and QR faded above it, so the player never loses the code.

### D3: Header sign-in states

The account chip from `ui-glass-redesign` gets these states:
- `SIGNED_OUT` and `EXPIRED`: a "Sign in" button. Expired also gets a hover hint.
- `SIGNING_IN`: the chip with a small progress pip and no action.
- `SIGNED_IN`: name, status dot and quota bar, opening the account screen.
- `CHOOSING`: opens the first sign-in choice.

A `SignInFailure` from the library's sign-in opens a `GlassConfirmScreen` over the library with the reason's text,
Retry and Done. That replaces the plain status line, and gameplay is never blocked, as the spec requires.

### D4: Upload target list

"As a new version of…" lists the player's cloud schematics in a scroll list with a search box. Each row shows its
folder and its next version number (`v2 → v3` on the selected row). The summary line is built by the same code that
picks the target, so it can't disagree with what the upload does.

## Risks / Trade-offs

- [The QR code could blur at fractional GUI scales.] → Module size is snapped to whole GUI pixels, as
  `QrMatrix.moduleSize` already does, and the modules are plain fills without feathering.
- [Dialogs over the account screen render that screen each frame.] → Accepted: it's a light screen, and it keeps the
  context visible as the specs ask.

## Migration Plan

Apply after `ui-glass-redesign`. No data changes. Rollback is a revert.
