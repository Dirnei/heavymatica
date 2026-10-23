# Heavymatica Roadmap

Heavymatica is a schematic mod in the spirit of Litematica, with a much simpler UI, MineColonies-style placement and a
cloud library (Heavymatica Cloud) that follows the player to every world and server. Each step below becomes one
OpenSpec change (`/opsx:propose`); finished changes live in `openspec/changes/archive/`, their requirements in
`openspec/specs/`.

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
    follow feet), Material list, Materials and Library.
  - HOLD (default: hold V, point, let go) or TOGGLE (tap V, click) in the client config. The camera stays still and
    WASD keeps working while it is open.
  - Remove and Unlock ask Yes / Cancel on the wheel, so one flick never removes anything.
  - Direct keys stay for continuous controls: arrows, numpad + / -, R and M, plus H for the library. The wheel's
    actions are still keys in the controls screen, unbound by default; a bound remove or unlock key keeps the
    press-twice rule.
- **Material swaps (change `material-swaps`):**
  - A placement swaps block types of its schematic for other blocks, per block type and not by family: oak stairs to
    spruce stairs leaves oak planks and slabs as they are. Each swap covers every state of its block, and properties
    the new block also has (facing, half, waterlogged) carry over.
  - Swaps are stored with the placement and saved with it, in every state; on a locked placement the comparison and
    the material list start over with the new blocks.
  - The wheel's Materials segment (or an unbound key) opens the materials screen: every block type with its count and
    swap, a block picker with same-kind suggestions and search, and a reset per row. The overlay shows the number of
    swaps.
  - Later: saving or sharing a set of swaps as a variant on top of the cloud version, and family-wide swaps.
- Step 3 is done. Dragging ghosts with the mouse is dropped for now.

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
- **Block equivalents (change `block-equivalents`):** planned blocks that are not placed as themselves.
  - A built-in table names, per planned block, the item that places it, the blocks that already count as it and the
    blocks that only need a finishing step: grass and dirt count as each other, dirt paths need a shovel and farmland
    a hoe on dirt, stripped wood needs an axe on its log, and concrete needs water on its powder.
  - Blocks that need finishing get a yellow box, don't count as done and need no item. The overlay counts them per
    tool over the visible layers, and the materials screen shows them in a Finish column.
  - Need and have are kept per planned block, and each carried item counts toward one planned block only, so
    concrete never stands in for planned powder.
  - Later rows: copper weathering accepted as equivalent, waxed copper (copper plus honeycomb), snow layers, crop
    ages and leaf persistence.
- **Ghost targeting (change `ghost-targeting`):** the crosshair targets ghosts as if they were real blocks.
  - A ray from the eye finds the nearest drawn position of a locked placement (a ghost the layers and the in-hand
    filter show, or a mistake or finishing box), unless a real block elsewhere is hit first.
  - A small HUD below the crosshair names the planned block, its orientation and its state in the comparison colours,
    the placement when it is not selected, and whether it is out of reach. Its range is a setting (32 blocks, 0 off).
  - With Jade installed, Jade's tooltip shows the ghost instead of the HUD. Jade stays optional.
  - Middle click on a ghost within reach picks its item, own item first, then the placing item, then the equivalents:
    selects it in the hotbar, swaps it in from the main inventory, or adds it in creative. Works on vanilla servers;
    sneak or the setting gives vanilla's pick.
  - The overlay's expected and found lines follow the ghost target.
  - Later: picking from carried containers and chests, and targeting anchored or following placements.
- Step 4 is done. **Next up: the web UIs (step 5), starting with `web-library`.**

### 5. Web UIs

Two separate web apps on the same cloud. They come before multiplayer sync and the machines addon: they only need the
cloud (steps 1 + 2), and a visible web library makes the cloud easier to test.

- **Player UI** on the public domain: organize schematics and share links in the browser.
- **Admin UI** on its own hostname (for example `admin.heavymatica.example`) and its own listener in the cloud. The
  public player domain never routes to admin paths.
  - It starts **public**, hardened: rate limits on sign-in, setup code and invitations, short-lived single-use setup
    codes and invitation links, a short-lived admin cookie bound to the admin host, and an audit log of every admin
    action and every new passkey.
  - It can move **behind WireGuard** later by changing only DNS (to the VPN address) and the proxy or firewall rules.
    The hostname stays the same, because passkeys are bound to it; a new hostname would make every admin register
    again.
  - Certificates come from Let's Encrypt with the usual HTTP challenge while public; behind the VPN that challenge
    can't reach the host, so the move switches to the DNS-01 challenge.

Player accounts (decided):

- An **account** owns the schematics and holds a list of linked identities: any number of **Minecraft accounts**
  (UUID, verified through Mojang as today) and any number of **OIDC logins** (provider + user id, never the email).
  This allows alts and several providers on one account.
- **First sign-in** with an unknown identity, in the game or on the web, asks: create a new account, or join an
  existing one. Creating is one click, so players who never use the web lose nothing.
- **Joining and linking use a code entered on the website.** The game library shows a short, single-use code that
  expires after a few minutes, together with a QR code of the link URL, so a phone can open it and sign in there.
  - **Web identity, Minecraft account exists:** the player signs in on the web, chooses "join existing", and enters
    the code that the game library shows.
  - **Minecraft identity, web account exists:** the game library shows the code, and the player enters it on the
    website while signed in to that account.
  - **Account settings:** link more identities later with the same flow. An identity can be unlinked, except the
    last one.
- **No merging for now:** "join existing" only works for an identity that has no account yet. Combining two accounts
  that both have data is a later "merge accounts" feature.
- **Migration:** every existing Minecraft account becomes an account with one Minecraft link. Ownership moves from
  the UUID to the account ID, and tokens carry the account ID as subject plus the identity that signed in.
- **Quota stays a per-account entitlement**, so paid plans could raise it later without touching the schematics side.

Sign-in (decided):

- **Players** register and sign in on the web with **GitHub** first (OAuth2, the aspnet-contrib handler), handled by
  the cloud itself, no broker. **Microsoft and Google** follow later as further providers of the same kind. The game
  keeps the Mojang handshake and its ES256 tokens.
- **Admins** sign in with **passkeys only**, built into the cloud (ASP.NET Core Identity passkeys, .NET 10). Admin
  accounts have no password at all.
  - The first admin registers with a one-time setup code created by a CLI command on the server.
  - Further admins join by invitation from an existing admin.
  - Every admin registers at least two passkeys; lost access is restored by another admin's invitation or the
    server CLI.
- Admins are their own principals, with no player account and no Minecraft link.

Stack (decided):

- **Astro**, built statically, with **Svelte** islands for the interactive parts. The cloud serves the built files on
  the same origin as its API, so the web session is a cookie, with no tokens in the browser and no CORS. The admin UI
  is a second Astro project, served on the admin host, so the two sessions never mix.
- A typed API client is generated from `src/api/heavymatica-cloud.v1.yaml`.
- Share pages get their title and thumbnail meta tags from the cloud, so links show a preview in chat apps.
- Aspire runs the Astro dev servers next to the cloud during development; production runs only the cloud.

Changes, in order:

1. **`web-library` (done):** the website first. Register and sign in with GitHub only; upload schematics and see and manage
   them in the browser (list and search, details, versions, rename, delete, storage use). It also brings the account
   foundation: accounts with a list of linked identities, the migration of existing Minecraft accounts, and tokens
   with the account ID as subject. Nothing changes for the game yet.
2. **`account-links` (next):** connecting the game. The game library shows a link code with a QR code, entered on the
   website; the first-sign-in choice (create new or join existing); linking and unlinking more identities. After
   this, the game and the website show the same library.
3. **`admin-web`:** the admin host and its listener, passkey admins with setup code and invitations, account lookup and
   quota adjustments.

Later in this step: Microsoft and Google sign-in, and share links with previews.

Out of scope for now: billing, subscriptions and marketplace orders.

### 6. Multiplayer sync

- The core mod is optional on the server; when installed, the server relays placements between players.
- Placements are **private by default**, shared opt-in per placement with "everyone on this server" or "invited
  players". Team sharing can come later through the machines addon.
- Without the server mod: cloud "build projects" as fallback (sharded BuildProject actors in the cloud, live updates
  to invited players).
- Game servers fetch private schematics with short-lived, read-only tokens the placing player hands over; the access
  tokens are already ES256 so servers can check them with the public key.

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
- The addon is also the "server companion" for creative paste.

### Later: creator marketplace

Separate product. Check Mojang's usage guidelines, CurseForge / Modrinth monetization policies, piracy (files are
easy to copy) and payouts / tax / moderation before investing.

## Open before a public release

- Production hosting, domain and the mod's default `cloudUrl` (currently `http://localhost:5180`).
- Free quota (currently 100 MiB per account) and sign-in rate limits.
- Disclose the mod's network access on its distribution pages.
