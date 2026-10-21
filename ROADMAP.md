# Heavymatica Roadmap

Heavymatica is a schematic mod in the spirit of Litematica, with a much simpler UI, MineColonies-style placement and a
cloud library (Heavymatica Cloud) that follows the player to every world and server. Each step below becomes one
OpenSpec change (`/opsx:propose`); finished changes live in `openspec/changes/archive/`, their requirements in
`openspec/specs/`.

## Ground rules (decided)

- **Hybrid:** the core mod `heavymatica` works client-only on any server, vanilla included. Server-side features are
  optional and only active when the server has them.
- **Cloud is the source of truth.** Client and game server both fetch schematics from the cloud; placements pin an
  immutable version (id + version + variant + position/rotation/mirror).
- **Simple UI**, three verbs: place, swap materials, show layers.
- **Building by hand is first-class**; machines are a bonus.
- **Two mods, one repo:** `heavymatica` (no Foundation dependency) and `heavymatica_machines` (depends on
  Servus: Foundation in `../servus_mc_mods`).
- **Stack:** NeoForge 26.1.2 / Java 25; cloud in C# / .NET with Akka.NET, Akka.Streams, MongoDB and S3-compatible
  storage, run locally with Aspire; contract-first OpenAPI (`src/api`) with generated server and client.

## Steps

### 1 + 2. Cloud core and client library (done)

Archived as `2026-10-21-cloud-core-and-client-library`: sign-in through the Mojang handshake, uploads with immutable
versions, library screen, cache, offline mode, import and upload.

Left over: real Litematica / WorldEdit / structure-block exports for `src/conformance/files/real/` (task 3.1 of that
change). `.blueprint` (Structurize) import was out of scope.

### 3. Placement UI

- **Placing (change `placement-interaction`):**
  - A held remote controls placements: a configurable vanilla item (default stick) or anything in the
    `heavymatica:remotes` item tag; the machines addon adds a real Remote item to the tag later.
  - Library **Place** starts a ghost that follows the crosshair. Right-click anchors it, sneak + right-click releases
    it again.
  - Keyboard instead of an on-screen pad: arrows move relative to the camera (Shift: 10), numpad + / - up and down,
    R rotate, M mirror, remove (press twice; on the wheel since `radial-menu`).
  - Many placements per world; right-click a ghost to select it. A side overlay shows the state and key hints.
    Anchored placements are saved per world or server.
- **Locking and layers (change `placement-layers`):**
  - Locking an anchored ghost, unlocking with a confirmation (both on the wheel since `radial-menu`). A locked ghost
    can't move, rotate or be removed.
  - While locked, the arrows and numpad + / - control layers on the Y axis: off, a from-to window, or follow feet
    (layers up to the player's feet + 1).
  - The in-hand filter (on the wheel): the held block shows normally and other hotbar blocks show dimmed. It combines
    with the layer setting and works whatever the player holds.
- **Wheel (change `radial-menu`):**
  - V (rebindable, only with the remote) opens a radial menu of the one-shot actions the selected placement's state
    allows: Lock / Unlock, Remove (Cancel while following), In-hand filter, Layers (a sub-wheel with off, from-to and
    follow feet), Material list and Library.
  - HOLD (default: hold V, point, let go) or TOGGLE (tap V, click) in the client config. The camera stays still and
    WASD keeps working while it is open.
  - Remove and Unlock ask Yes / Cancel on the wheel, so one flick never removes anything.
  - Direct keys stay for continuous controls: arrows, numpad + / -, R and M, plus H for the library. The wheel's
    actions are still keys in the controls screen, unbound by default; a bound remove or unlock key keeps the
    press-twice rule.
- Still open in this step: material swaps (below), which add a Materials segment to the wheel. Dragging ghosts with
  the mouse is dropped for now.
- **Material swaps:** swap by block family, so oak to spruce also turns stairs, slabs, fences, doors and trapdoors and
  keeps their block states. Non-destructive: a swap is a small "variant" stored on top of the original version in the
  cloud.

### 4. Hand-build helper

- **World comparison (change `world-comparison`):** a locked placement compares itself with the world.
  - Blocks that already match are hidden; missing blocks show as the normal ghost.
  - Mistakes get a coloured box: red for a wrong block, orange for a wrong orientation (facing, axis, half, slab type
    and the like), cyan for wrong waterlogging, magenta for any block where the schematic has air.
  - Boxes follow the layer setting but ignore the in-hand filter, so follow feet keeps every mistake below the feet in
    view.
  - The overlay shows progress, progress of the visible layers, a count per colour and the expected and found block
    under the crosshair.
  - No mixins: blocks within reach are rechecked every tick, the rest of the placement by a budgeted background scan.
  - Gaps between the regions of a multi-region `.litematic` read as structure voids, so comparison ignores them.
- **Material list (change `material-list`):** what a locked placement still needs against what the player carries.
  - Missing, wrong-block and not yet compared positions need their item; turned blocks and wrong water do not.
  - Only the visible layers count (off, from-to window or follow feet); the in-hand filter has no effect.
  - Two-cell blocks count once, a double slab as two slabs, candles, sea pickles and snow layers by their amount.
    Blocks no item places (water, lava, fire, portals) are listed apart under "can't carry".
  - Carried means the inventory, the offhand and the contents of carried shulker boxes, bundles and other items with
    NeoForge's item capability. Chests and other containers in the world don't count.
  - The overlay shows the three largest shortages; the wheel's Material list opens the full list with needed, have and
    short.
- Step 4 is done. **Next up: material swaps** (still open in step 3), then multiplayer sync.

### 5. Multiplayer sync

- The core mod is optional on the server; when installed, the server relays placements between players.
- Placements are **private by default**, shared opt-in per placement with "everyone on this server" or "invited
  players". Team sharing can come later through the machines addon.
- Without the server mod: cloud "build projects" as fallback (sharded BuildProject actors in the cloud, live updates
  to invited players).
- Game servers fetch private schematics with short-lived, read-only tokens the placing player hands over; the access
  tokens are already ES256 so servers can check them with the public key.

### 6. Machines addon (`heavymatica_machines`, on Servus: Foundation)

- **Material Monitor:** linked to a placement and to inventories (Utility Duct / item networks, any
  `Capabilities.Item`); shows required minus already built minus stored. World diff is incremental (full scan on
  link, then block events, then a slow budgeted re-check), never a per-tick scan.
- **Build Cannon:** FE-powered, pulls materials from linked inventories, builds bottom-up with supports first; energy
  per block and speed from datapack JSON (`MachineStats`), range and speed upgrades. Places block states only, never
  copies block-entity contents (no duplication).
- **Precision Charge:** tiny TNT that removes exactly one block (custom entity, not a vanilla explosion). Cannon ammo
  for obstructions, also placeable by hand. Respects bedrock, unbreakable blocks and claims (FTB Teams via
  `OwnedMachine`); containers spill their contents; drops go to the Cannon's network.
- The addon is also the "server companion" for creative paste.

### 7. Web dashboard

Organize schematics and share links in the browser. Open decisions:

- **Sign-in:** recommended a link code shown in the game library and confirmed in the browser (reuses the existing
  identity); alternative Microsoft OAuth (needs an Azure app registration).
- **Stack:** React/TypeScript with a client generated from the same OpenAPI contract, or Blazor.

### Later: creator marketplace

Separate product. Check Mojang's usage guidelines, CurseForge / Modrinth monetization policies, piracy (files are
easy to copy) and payouts / tax / moderation before investing.

## Open before a public release

- Production hosting, domain and the mod's default `cloudUrl` (currently `http://localhost:5180`).
- Free quota (currently 100 MiB per account) and sign-in rate limits.
- Disclose the mod's network access on its distribution pages.
