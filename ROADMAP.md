# Heavymatica Roadmap

Heavymatica is a schematic mod in the spirit of Litematica, with a much simpler UI, MineColonies-style placement and a
cloud library (Heavymatica Cloud) that follows the player to every world and server. Each step below becomes one
OpenSpec change (`/opsx:propose`); finished changes live in `openspec/changes/archive/`, their requirements in
`openspec/specs/`. This file lists only what is still open.

## Ground rules (decided)

- **Hybrid:** the core mod `heavymatica` works client-only on any server, vanilla included. Server-side features are
  optional and only active when the server has them.
- **Cloud is the source of truth.** Client and game server both fetch schematics from the cloud; placements pin an
  immutable version (id + version + position/rotation/mirror + material swaps; saved variants later).
- **Simple UI**, three verbs: place, swap materials, show layers.
- **Building by hand is first-class**; machines are a bonus.
- **Two mods, one repo:** `heavymatica` (no Foundation dependency) and `heavymatica_machines` (depends on
  Servus: Foundation in `../servus_mc_mods`).
- **Stack:** NeoForge 26.1.2 / Java 25; cloud in C# / .NET with Akka.NET, Akka.Streams, MongoDB and S3-compatible
  storage, run locally with Aspire; contract-first OpenAPI (`src/api`) with generated server and client.

## Open in the mod

### Litematica gaps

- **Entities:** item frames, armor stands, paintings and minecarts are dropped on import and never captured. Minimum:
  show them in the ghost and keep them through import, capture and export, even if the comparison ignores them.
- **X and Z layers:** only the Y axis is sliced today; walls and facades would benefit from X and Z.
- **Block-entity data in server captures:** chest contents and the like need the optional server mod, as Litematica
  uses Servux.
- Deliberately skipped: moving parts within a placement, multi-box selections, fill / replace / delete tools
  (WorldEdit's job), the legacy MCEdit `.schematic` format, and material list export (the web library shows the
  list).

### Smaller follow-ups

- **Material swaps:** saving or sharing a set of swaps as a variant on top of the cloud version, and family-wide swaps.
- **Block equivalents:** copper weathering accepted as equivalent, waxed copper (copper plus honeycomb), snow layers,
  crop ages and leaf persistence.
- **Ghost targeting:** picking from carried containers and chests, and targeting anchored or following placements.
- **Capture and export:** ignoring air on a placement (a placement option next to material swaps), converting formats
  on export.
- **Mistake pointer:** a beam or world marker, if the HUD arrow turns out not to be enough.
- **Conformance:** real Litematica / WorldEdit / structure-block exports for `src/conformance/files/real/`.
  `.blueprint` (Structurize) import stays out of scope.

## Open in the cloud and on the web

- **Sign-in:** Microsoft and Google as further web providers, next to GitHub.
- **Share links** with title and thumbnail previews in chat apps.
- **Merging accounts:** "join existing" only works for an identity without an account; combining two accounts that
  both have data is a later feature.
- **Admin host behind WireGuard:** possible later by changing only DNS and the proxy or firewall rules, keeping the
  hostname (passkeys are bound to it) and switching Let's Encrypt to the DNS-01 challenge.
- Out of scope for now: billing, subscriptions and marketplace orders.

## Next steps

### 6. Multiplayer sync

- The core mod is optional on the server; when installed, the server relays placements between players.
- Placements are **private by default**, shared opt-in per placement with "everyone on this server" or "invited
  players". Team sharing can come later through the machines addon.
- Without the server mod: cloud "build projects" as fallback (sharded BuildProject actors in the cloud, live updates
  to invited players).
- Game servers fetch private schematics with short-lived, read-only tokens the placing player hands over; the access
  tokens are already ES256 so servers can check them with the public key.
- The optional server mod could also accept a clean "place state X at Y" request for Easy Place.

### 7. Machines addon (`heavymatica_machines`, on Servus: Foundation)

- **Material Monitor:** linked to a placement and to inventories (Utility Duct / item networks, any
  `Capabilities.Item`); shows required minus already built minus stored. World diff is incremental (full scan on
  link, then block events, then a slow budgeted re-check), never a per-tick scan.
- **Build Cannon:** FE-powered, pulls materials from linked inventories, builds bottom-up with supports first; energy
  per block and speed from datapack JSON (`MachineStats`), range and speed upgrades. Places block states only, never
  copies block-entity contents (no duplication).
- **Precision Charge:** tiny TNT that removes exactly one block (custom entity, not a vanilla explosion). Cannon ammo
  for obstructions, also placeable by hand. Respects bedrock, unbreakable blocks and claims (FTB Teams via
  `OwnedMachine`); containers spill their contents; drops go to the Cannon's network.
- The addon is also the server companion for creative paste in multiplayer; single player already has it.

### Later: creator marketplace

Separate product. Check Mojang's usage guidelines, CurseForge / Modrinth monetization policies, piracy (files are
easy to copy) and payouts / tax / moderation before investing.

## Open before a public release

- Production hosting. The domain and the mod's default address are settled: the mod talks to `https://api.heavymatica.com`.
- Refusing new sign-ups while the cloud is closed: today "closed" only hides sign-in in the game.
- Free quota (currently 100 MiB per account) and sign-in rate limits.
- Disclose the mod's network access on its distribution pages.
