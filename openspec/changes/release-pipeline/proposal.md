## Why

Heavymatica has no way to ship. The repository has no GitHub workflows, no tags and no changelog, and every build is
made by hand on one machine. The first public alphas are meant to go out on CurseForge and Modrinth, so a release has
to be one tag push, with no files to edit and no checklist. It also needs a logo for the mods list and the project
pages.

## What Changes

- **Release by tag:** pushing a tag `heavymatica-v<version>` releases that version. The version comes only from the
  tag; nothing in the repository is edited or committed for a release. The prefix leaves room for a later
  `heavymatica_machines` with its own tags.
- **Notes from the commits:** the release notes are generated from the Conventional Commits since the previous
  `heavymatica-v*` tag that touch the mod: features and fixes, nothing else. An annotated tag's message goes above the
  list, and for the first release it is the notes on its own. A release without notes fails instead of publishing an
  empty changelog.
- **Publishing in the same workflow:** the tag's workflow builds and tests the tagged commit, builds the jar at the
  tag's version, creates the GitHub release with the jar, and uploads it to CurseForge and Modrinth. A platform whose
  project ID is not configured is skipped.
- **Release channel from the version:** every 0.x version is published as alpha. From 1.0 on, a version containing
  `alpha` or `beta` is published as that type, and anything else as a release.
- **Build on every push and pull request:** a reusable build workflow compiles, lints the cloud contract and runs the
  tests. The release workflow runs it first and publishes only when it passes.
- **Development version:** the version in the mod's `gradle.properties` becomes `0.0.0-dev` once, so a local or CI
  build is never mistaken for a release.
- **Logo:** the "ghost cube" (concept A from https://claude.ai/artifact/7fDyZZcpQ5G3Dysrn3Koo4). It is an isometric
  cube, solid off-white at the bottom and a translucent cyan ghost at the top, split by a glowing layer line, on a
  smoked-glass tile. Its SVG source and generator live in the repository, with a 512×512 PNG for the project pages.
  The mods list shows it through `logoFile`.
- **Manual setup, documented:** creating the CurseForge and Modrinth projects and adding the tokens and project IDs.

## Capabilities

### New Capabilities

- `distribution/mod-release`: how a version of the mod is built, versioned, released and published, and the mod's logo.

### Modified Capabilities

None.

## Impact

- New `.github/workflows/build.yml` and `.github/workflows/release.yml`, and a release notes script beside them.
- Removed again: the Release Please setup added earlier in this change (`release-please-config.json`,
  `.release-please-manifest.json`, the version markers in `src/mod/heavymatica/gradle.properties` and
  `src/mod/heavymatica/CHANGELOG.md`).
- `src/mod/heavymatica/gradle.properties`: `mod_version` becomes `0.0.0-dev`.
- `src/mod/gradlew` becomes executable in git, because the Linux runners need it.
- New `src/branding/`: the logo's SVG, its generator and the 512×512 PNG.
- Mod resources: the logo PNG, plus `logoFile` in `neoforge.mods.toml`.
- The README's "Releasing" section describes the tag release.
- No mod, cloud or network behaviour changes. The disclosure text for the project pages depends on the change
  `cloud-availability`.
