# Proposal

## Why

Players learn Heavymatica from a 4 KB README, and developers from a 13 KB CONTRIBUTING.md. Neither explains features
in depth, shows how the parts fit together, or has a place to grow. Fabric support brings the players Heavymatica is
built for, and they need a real manual before the first public releases.

## What Changes

- A documentation site at `https://docs.heavymatica.com`, built with Astro and Starlight from `docs/` at the repository
  root and published to GitHub Pages on every push to `main` that touches it.
- Player documentation comes first and covers every feature: installing on Fabric or NeoForge, the first build, the
  remote and keys, placement, layers, world comparison and the mistake pointer, materials and swaps, Easy Place and
  placement restriction, capture, creative paste, the library and the cloud, the website, settings, compatibility with
  other mods and servers, and troubleshooting.
- Developer documentation comes second: architecture, building, the mod projects, the cloud, the websites, the API,
  releasing and the development aids. The content of `CONTRIBUTING.md` moves there. `CONTRIBUTING.md` keeps a short
  guide to contributing with local build and run tips and links to the site.
- Architecture diagrams come from a LikeC4 model in `docs/architecture/`: a landscape view, a view of the mod and a view
  of the cloud.
- The settings reference is generated from the mod's default settings file and the key reference from a key list the
  mod's tests keep in sync, so neither can drift from the mod.
- Pages can mark a feature as coming in the next release.
- `README.md` keeps the pitch and install steps and links to the site.

## Capabilities

### New Capabilities

- `web/docs-site`: the public documentation site: where it lives, what it covers for players and developers, the
  generated settings and key references, architecture diagrams, unreleased markers and how it is published.

### Modified Capabilities

None.

## Impact

- New `docs/` folder (Astro, Starlight, React for the LikeC4 views, LikeC4). It is the one top-level folder with source
  outside `src/`.
- New workflow `.github/workflows/docs.yml` (build and deploy to Pages).
- `src/mod/heavymatica/common`: a test that writes and checks `docs/src/data/keys.json`; the key classes expose their
  definitions for it.
- `README.md` and `CONTRIBUTING.md` shrink; `CONTRIBUTING.md` sections move to `docs/`.
- Manual steps for the repository owner: the DNS record for `docs.heavymatica.com`, verifying the domain on GitHub and
  setting the Pages source to GitHub Actions.
- Screenshots are added by a separate change; until then pages use few or no images.
