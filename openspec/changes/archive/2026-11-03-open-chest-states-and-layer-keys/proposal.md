# Proposal

## Why

Player feedback from building with a locked placement: the comparison passes a single chest where the plan has a
double chest, and the other way round. It also passes a fence gate, door or trapdoor that is open where the plan has it
closed, or the reverse, even after the player toggles it. Both slip through as matching, so the finished build differs
from the plan without any orange box. Separately, the player has to take the remote out every time they move the layer
window while building, which breaks the flow.

## What Changes

- The comparison checks a chest's type (single, left half, right half) and the open state of doors, trapdoors and fence
  gates. A difference is wrong orientation, with an orange box. A barrel's open state stays ignored.
- A single chest where the plan has one half of a double chest is not enough yet, because placing the other half
  completes it. That way the first half of a double chest is not a mistake.
- The targeting HUD says "Open it" or "Close it" when only the open state differs. When a chest's type differs, it
  names the found chest type.
- Easy Place places a chest single or connected as planned. When the game would connect a planned single chest to a
  neighbour, Easy Place sends the click while sneaking, as a vanilla client can. Easy Place and placement restriction
  never judge the open state: a closed gate placed where an open one is planned is accepted, and the comparison then
  shows it orange until the player opens it.
- The layer keys work without the remote whenever the selected placement is locked and no screen is open. These are up
  and down arrows (move the window), numpad + and - (resize the window or change the follow offset) and the cycle layer
  mode key. Without the remote, a layer change shows the new layers in the action bar. Moving, rotating, mirroring and
  every other placement key still need the remote.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client/world-comparison`: chest type and the open state of doors, trapdoors and fence gates join the orientation
  properties. A single chest under a planned half is not enough yet.
- `client/ghost-targeting`: the HUD line for wrong orientation names the open state to set, or the found chest type.
- `client/easy-place`: chest connection placed as planned, using a sneaking click when needed. The open state is left to
  the player.
- `client/placement-restriction`: the open state does not refuse a click.
- `client/placement-layers`: the layer keys work without the remote on a locked placement, with action-bar feedback.
- `client/placement-remote`: placement keys need the remote, except the layer keys on a locked placement.
- `client/radial-menu`: the cycle layer mode key works without the remote on a locked placement.

## Impact

- `placement/CellKeys.java`: orientation and shown properties, chest-half handling. `placement/CellState.java`:
  classification. `placement/WorldComparison.java`: a judge that ignores the open state for clicks.
- `build/EasyPlace.java`, `build/VanillaPlacement.java`, `build/PlacementRestriction.java`: judging and the sneaking
  click.
- `targeting/TargetLines.java`, `targeting/TargetHud.java`, lang files: the new HUD lines.
- `remote/RemoteKeys.java`, `remote/RemoteInput.java`: the key conflict context and layer handling without the remote.
- Tests for classification, the placement search and the layer key rules.
