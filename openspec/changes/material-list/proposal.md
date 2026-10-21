# Proposal

## Why

The world comparison shows what is still missing, but not what to fetch. Players building by hand need to know which
items they lack before they start a layer. This is the last piece of the hand-build helper (roadmap step 4).

## What Changes

- **A material list for the selected locked placement:** how many of each item the placement still needs, how many
  the player carries, and how many are short.
- **What it counts:**
  - Missing and wrong-block (red) positions count as needing an item.
  - Positions not compared yet count too, so the list never says too little.
  - Wrong orientation (orange) and wrong water (cyan) do not count: the item comes back when the block is placed
    again, and water is not an item.
  - Only the visible layers count (off, from-to window or follow feet). The in-hand filter has no effect.
- **Blocks become items:** most blocks are their own item. A small set of rules covers the rest:
  - two-cell blocks (doors, beds, tall plants) count once
  - a double slab counts as two slabs
  - candles, sea pickles and snow layers count by their amount
- **Can't carry:** positions whose block has no item (fluids, fire, portals, piston heads and the like) show in a
  separate "can't carry" part of the list, never as shortages.
- **What the player has:**
  - the inventory and the offhand
  - the contents of shulker boxes and bundles they carry
  - the contents of any other carried item that exposes NeoForge's item capability, such as backpacks that keep their
    items on the stack
- **Where it shows:**
  - the overlay gains up to 3 lines with the largest shortages
  - a new key (default K, rebindable, works while the remote is held) opens a screen with the full list: item, needed,
    have and short, sorted by most short first

Out of scope:
- chests, the ender chest and other containers in the world
- Sophisticated Backpacks and other backpacks that keep their contents on the server
- clicking a row to filter the ghost
- server-side inventories (machines addon)

## Capabilities

### New Capabilities
- `client/material-list`: which positions need items, how blocks map to items, what counts as carried, the shortage
  lines and the material list screen.

### Modified Capabilities
- `client/placement-remote`: the default key layout gains K for the material list, and the overlay gains the shortage
  lines for a locked placement.

## Impact

- **Mod code only (`src/mod/heavymatica`):**
  - the `placement` package: per-layer counts of needed items next to the comparison state
  - the `remote` package: the new key and the overlay lines
  - the `ui` package: the material list screen
- **Depends on `world-comparison`:**
  - It builds on that change's comparison state.
  - It modifies the overlay requirement that `world-comparison` changes.
  - `world-comparison` has to be archived before this change is archived.
- No cloud, API, server or saved data changes.
