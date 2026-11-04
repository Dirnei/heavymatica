---
title: Repository layout
description: Where each part of Heavymatica lives in the repository.
---

```
src/
  api/           OpenAPI contracts of the Heavymatica Cloud (source of truth, code is generated from them)
  cloud/         .NET solution: the cloud service, its tests and the Aspire AppHost
  web/player/    the player website (Astro with Svelte islands), served by the cloud
  web/admin/     the admin site (Astro with Svelte islands), served on the admin listener
  mod/           Gradle build of the mods: per mod a loader-free common project and one project per loader
  conformance/   schematic sample files and expected results, shared by the cloud and mod tests
  branding/      the logo source and its exports (python src/branding/generate.py)
docs/            this documentation site (Astro, Starlight) and the architecture model (docs/architecture)
openspec/        specs and changes
.github/         CI: build, release and docs workflows
```

All code lives under `src/`, one folder per part. `docs/` is the one exception: it holds the documentation, not code
that ships.

## Branding

`src/branding/generate.py` writes the logo from one list of shapes: the SVG logo, a small variant for 32 px and less,
the 512 px project icon for CurseForge and Modrinth, the 256 px logo in the game's mods list and this site's favicon.
Run it from the repository root after changing a shape and commit the results:

```
python -m pip install pillow
python src/branding/generate.py
```

## Conformance files

`src/conformance` holds sample schematic files and the result every Heavymatica reader must produce for them. The cloud
and the mod both run every file in their test suites, so the two readers cannot drift apart. A fix to a reader starts
with a new fixture. `src/conformance/README.md` explains the expected results and the generator.
