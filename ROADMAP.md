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
    R rotate, M mirror, Delete remove (press twice).
  - Many placements per world; right-click a ghost to select it. A side overlay shows the state and key hints.
    Anchored placements are saved per world or server.
- Still open in this step: material swaps and layers (below). Dragging ghosts with the mouse is dropped for now.
- **Material swaps:** swap by block family, so oak to spruce also turns stairs, slabs, fences, doors and trapdoors and
  keeps their block states. Non-destructive: a swap is a small "variant" stored on top of the original version in the
  cloud.
- **Layers:** axis toggle Y / X / Z, slider, modes "single" and "up to", and "follow player".

### 4. Hand-build helper

- Progress in percent; highlight wrong blocks (red) and missing blocks in the current layer.
- Material list against the player's own inventory ("4 short"). Works client-only from loaded chunks.

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
