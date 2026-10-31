# Proposal

## Why

When a locked placement reaches 100%, nothing happens. The overlay shows 100%, and the finished build stays covered by
an empty ghost box and its selection outline. Players get no moment of "done" and have to unlock and remove the
placement themselves, with two confirmations.

## What Changes

- A locked placement whose progress reaches 100% is **finished**:
  - the client plays a short success sound;
  - a toast names the placement and says how to bring it back;
  - the placement is removed from the world's list.
- For 10 seconds after a finish, the wheel shows a **Bring back** segment. It restores the placement exactly as it was
  (locked, same position, rotation, mirror, layers, filter and swaps) and selects it.
- A brought-back placement does not finish again until its progress has dropped below 100% and come back up, for
  example after a block in it was broken and placed again.
- Finishing removes a LOCKED placement on its own; removing one by hand still needs it to be unlocked first.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client/world-comparison`: a locked placement at 100% progress finishes, with sound, toast and removal, and can be
  brought back for a short time.
- `client/schematic-placement`: finishing is the one way a LOCKED placement is removed without unlocking it first.
- `client/radial-menu`: the Bring back segment while a finished placement can be brought back.

## Impact

- Mod only (`placement`, `remote`, language file); no cloud, API or saved-format changes.
- New language keys for the toast and the wheel segment.
- Uses a vanilla sound event; no new assets.
