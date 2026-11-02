## Why

Heavymatica has no way to ship. The repository has no GitHub workflows, no tags and no changelog, and every build is
made by hand on one machine. The first public alphas are meant to go out on CurseForge and Modrinth, so a release has
to be one merge, not a checklist. It also needs a logo for the mods list and the project pages.

## What Changes

- **Release Please** keeps an open release PR for the `heavymatica` mod. The PR bumps `mod_version` and writes the
  changelog from the Conventional Commits that touch the mod. Merging it tags `heavymatica-v<version>` and creates
  the GitHub release. The setup leaves room for a later `heavymatica_machines` package with its own tags.
- **Publishing in the same workflow:** after the release is created, a publish job builds the jar at that version. It
  attaches the jar to the GitHub release and uploads it to CurseForge and Modrinth. A platform whose project ID is not
  configured is skipped. A release without release notes fails instead of publishing an empty changelog.
- **Release channel from the version:** every 0.x version is published as alpha. From 1.0 on, a version containing
  `alpha` or `beta` is published as that type, and anything else as a release.
- **Build on every push and pull request:** a reusable build workflow compiles, lints the cloud contract and runs the
  tests. The release workflow runs it first and publishes only when it passes.
- **Logo:** the "ghost cube" (concept A from https://claude.ai/artifact/7fDyZZcpQ5G3Dysrn3Koo4). It is an isometric
  cube, solid off-white at the bottom and a translucent cyan ghost at the top, split by a glowing layer line, on a
  smoked-glass tile. Its SVG source and generator live in the repository, with a 512×512 PNG for the project pages.
  The mods list shows it through `logoFile`.
- **Manual setup, documented:** creating the CurseForge and Modrinth projects, adding the tokens and project IDs, and
  allowing GitHub Actions to create pull requests.

## Capabilities

### New Capabilities

- `distribution/mod-release`: how a version of the mod is built, versioned, released and published, and the mod's logo.

### Modified Capabilities

None.

## Impact

- New `.github/workflows/build.yml` and `.github/workflows/release.yml`.
- New `release-please-config.json` and `.release-please-manifest.json` at the repository root.
- `src/mod/heavymatica/gradle.properties`: the version line gets the Release Please marker.
- New `src/mod/heavymatica/CHANGELOG.md`, written by Release Please.
- `src/mod/gradlew` becomes executable in git, because the Linux runners need it.
- New `src/branding/`: the logo's SVG, its generator and the 512×512 PNG.
- Mod resources: the logo PNG, plus `logoFile` in `neoforge.mods.toml`.
- No mod, cloud or network behaviour changes. The disclosure text for the project pages depends on the change
  `cloud-availability`.
