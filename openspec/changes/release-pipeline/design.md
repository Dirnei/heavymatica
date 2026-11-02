## Context

- The repository has no `.github/` folder, no tags and no changelog. The remote is `github.com/Dirnei/heavymatica`, and
  work lands directly on `main`.
- The Gradle build lives in `src/mod`, with `gradlew` there and one subproject, `heavymatica`. The root
  `build.gradle` already sets each subproject's version to `release_version_<project>` when that property is given,
  and to `mod_version` from the subproject's `gradle.properties` otherwise. The jar is
  `src/mod/heavymatica/build/libs/heavymatica-<version>.jar`.
- Compiling depends on `:lintContract`, which runs Spectral through npm in `src/api` (`package-lock.json` is there).
  It can be skipped with `-PskipContractLint`, but CI should run it.
- `src/mod/gradlew` is stored in git as `100644`, not executable.
- Commits already follow Conventional Commits (`CLAUDE.md`), with feature scopes such as `placement` or `wheel`, not
  package scopes.
- The servus mods repository (`../servus_mc_mods/.github/workflows/`) has a tag-driven `release.yml` with a reusable
  `build.yml`, using `Kira-NT/mc-publish@v3`. This design reuses its shape: project IDs as variables, a missing ID
  skips the platform, notes are required, and the type comes from the version. The difference is that Release Please
  creates the tag.
- The decisions were made with the user in the exploration that preceded this change. See proposal.md for the why.

## Goals / Non-Goals

**Goals:**
- One merge of the release PR publishes everywhere, with no personal access token and no manual tag.
- The build that is published is the build that was tested, at exactly the released version.
- Adding `heavymatica_machines` later is one more package entry, not a new pipeline.

**Non-Goals:**
- CI or releases for `src/cloud` (.NET), `src/web` or the conformance generator. They are deployed, not distributed,
  and get their own pipeline once hosting exists.
- Signing jars, Maven publishing, or uploading to other platforms.
- Writing the CurseForge or Modrinth project page text, beyond the network disclosure line it has to carry.

## Decisions

### D1: Release Please in manifest mode, one package per mod

`release-please-config.json` at the repository root:

```json
{
  "packages": {
    "src/mod/heavymatica": {
      "component": "heavymatica",
      "release-type": "simple",
      "include-component-in-tag": true,
      "tag-separator": "-",
      "changelog-path": "CHANGELOG.md",
      "bump-minor-pre-major": true,
      "extra-files": [{ "type": "generic", "path": "gradle.properties" }]
    }
  },
  "changelog-sections": [
    { "type": "feat", "section": "Features" },
    { "type": "fix", "section": "Bug Fixes" },
    { "type": "perf", "section": "Performance" },
    { "type": "refactor", "hidden": true }, { "type": "test", "hidden": true },
    { "type": "docs", "hidden": true }, { "type": "build", "hidden": true },
    { "type": "chore", "hidden": true }
  ],
  "bootstrap-sha": "<the commit that adds this file>"
}
```

- **Version file:** in `src/mod/heavymatica/gradle.properties`, `mod_version=0.1.0` is wrapped in
  `# x-release-please-start-version` and `# x-release-please-end` comment lines. The generic updater rewrites the
  version between them. The inline `# x-release-please-version` marker can't be used here: a Java properties file
  doesn't treat a `#` in the middle of a line as a comment, so the marker would become part of the value. With the
  block markers, the build keeps reading a clean `0.1.0`.
- **Tags:** `heavymatica-v<version>`, from the component plus the `-` separator. `heavymatica_machines` later gets
  its own entry, `src/mod/heavymatica_machines`, and its own tags.
- **Commits are assigned to packages by path.** A commit counts for the mod when it touches `src/mod/heavymatica/**`,
  whatever its scope.
- *Alternative: `release-type: java` or a Gradle plugin.* Rejected: the version lives in one properties line, and
  `simple` plus a generic updater handles that without Maven assumptions.

### D2: Bootstrapping from 0.1.0 with no tags

`.release-please-manifest.json` starts as `{ "src/mod/heavymatica": "0.1.0" }`. That treats 0.1.0, the current
development version, as released, so it is never published. `bootstrap-sha` is set to the commit that adds the
Release Please files. Otherwise Release Please would read the whole history back to the first commit and fill the first
changelog with weeks of development commits.

The first release is forced: the package entry gets `"release-as": "0.2.0"` in the same commit. The first release PR
then proposes 0.2.0 as soon as any commit touches the mod. Its changelog section is edited by hand in the PR before
merging, to a short "First public alpha" summary. After that release the `release-as` line is removed again.

- *Alternative: publish 0.1.0 as the first release.* Rejected: Release Please needs a previous version to bump from,
  and making 0.1.0 itself the first release needs the same `release-as` trick with no gain.

### D3: One workflow, release then publish

`.github/workflows/release.yml` runs on every push to `main`:

```
push to main
   |
   v
release-please   (googleapis/release-please-action@v4, GITHUB_TOKEN,
   |              permissions: contents write, pull-requests write)
   |  outputs['src/mod/heavymatica--release_created'] == 'true' ?
   v
build            (uses: ./.github/workflows/build.yml at the release commit)
   |
   v
publish          version, tag and notes from the release-please outputs
                 -> ./gradlew :heavymatica:build -Prelease_version_heavymatica=<version>
                 -> Kira-NT/mc-publish@v3: GitHub release (existing tag) + CurseForge + Modrinth
```

- **Why one workflow:** a tag or release made with `GITHUB_TOKEN` does not trigger other workflows. A separate
  tag-triggered `release.yml`, as in servus, would never run without a personal access token. Gating jobs on the
  outputs avoids that secret entirely.
- **Notes:** the publish job takes the release body from the action's `src/mod/heavymatica--body` output. If that
  output is not available in the action version used, it falls back to the `## <version>` section of
  `src/mod/heavymatica/CHANGELOG.md`, cut out the way servus does. If the notes are empty, the job fails before any
  upload ("No release without notes").
- **Concurrency:** `group: release`, `cancel-in-progress: false`, so two quick pushes never publish twice.
- **Build again in publish:** the reusable build checks the commit. The publish job then builds once more with
  `-Prelease_version_heavymatica`, exactly as servus does, so the jar carries the released version even though
  `gradle.properties` already holds it after the merge. This keeps it correct if the version line and the tag ever
  disagree.

### D4: Platform targets and the version type

Inputs to `mc-publish`:

| Input | Value |
| --- | --- |
| `files` | `src/mod/heavymatica/build/libs/heavymatica-<version>.jar` (exact name, so no sources jar) |
| `name` | `Heavymatica <version>` |
| `version` | `<version>` |
| `version-type` | see below |
| `changelog` | the notes from D3 |
| `loaders` | `neoforge` |
| `game-versions` | `minecraft_version` from `src/mod/gradle.properties` |
| `java` | `25` |
| `dependencies` | `jade(optional)`, matching `neoforge.mods.toml` |
| `github-tag` | `heavymatica-v<version>`, so the jar goes on the release Release Please made |
| `curseforge-id` / `curseforge-token` | `vars.CURSEFORGE_ID_HEAVYMATICA` / `secrets.CURSEFORGE_TOKEN`, token only when the ID is set |
| `modrinth-id` / `modrinth-token` | `vars.MODRINTH_ID_HEAVYMATICA` / `secrets.MODRINTH_TOKEN`, token only when the ID is set |

The version type is computed in a small shell step:

```
major 0            -> alpha
*alpha*            -> alpha
*beta*             -> beta
otherwise          -> release
```

Leaving a token empty is how servus skips a platform. mc-publish only publishes to targets that have a token.

### D5: Reusable build workflow

`.github/workflows/build.yml` runs on `push` (all branches), `pull_request` and `workflow_call`:

- `actions/checkout@v4`
- `actions/setup-java@v4` (Temurin 25)
- `actions/setup-node@v4` (Node 22, npm cache keyed on `src/api/package-lock.json`)
- `gradle/actions/setup-gradle@v4`
- `./gradlew build --no-daemon` with `working-directory: src/mod`. This compiles, runs `:lintContract`, generates the
  cloud client and runs the tests.
- `actions/upload-artifact@v4` with `src/mod/heavymatica/build/libs/heavymatica-*.jar`, `if-no-files-found: error`.

The Gradle settings already provision the Java 25 toolchain through foojay. Installing Temurin 25 explicitly saves
the download and lets Gradle itself run on 25. `gradlew` is marked executable in git
(`git update-index --chmod=+x src/mod/gradlew`), rather than calling it through `bash` in every step.

### D6: Logo source under `src/branding`

The repository rule puts all source under `src/`, one folder per part. Branding is its own part, so it goes in
`src/branding/`, not at the root as in servus:

- `generate.py`: writes `heavymatica.svg` and `heavymatica-small.svg` from the geometry of concept A. That is the cube
  with points `126,195 / 256,120 / 386,195 / 256,270` (top), the layer cut at 45% height, the dashed ghost edges, the
  layer line with glow, the `#1C232C` to `#0E1218` tile with `rx=112`, the white rim at 12%, and the cyan radial glow.
  The small variant drops the dashes and the glow, raises the cyan fills to 25 to 45% and widens the layer line, for
  32 px and smaller.
- `heavymatica.svg`, `heavymatica-small.svg`: generated, and committed so they can be viewed without running anything.
- `heavymatica-512.png`: 512×512, for the CurseForge and Modrinth project icons.
- PNG export rasterises the same shape list with Pillow at 4× and scales it down, so no SVG renderer is needed.
  `cairosvg` was the first plan, but it needs the native cairo library, which Windows does not have. The script says
  how to install Pillow when it is missing. PNGs are committed, and nothing in the build or CI runs the script.
- `heavymatica-small-64.png`: a 64 px preview of the small variant, to check it at launcher size.

The mod gets `src/mod/heavymatica/src/main/resources/heavymatica.png`: a 256×256 export, enough for the mods list and
smaller in the jar. `neoforge.mods.toml` gets `logoFile="heavymatica.png"` in the `[[mods]]` block. NeoForge scales the
logo down with smoothing by default (`logoBlur`), so the large variant is used, not the small one.

- *Alternative: hand-drawn PNGs only.* Rejected: the SVG keeps the logo editable and matches how servus keeps its
  branding.

### D7: Manual setup, done once

These steps are outside the repository. They are tasks for the user and are listed in tasks.md:

1. Repository settings → Actions → General: allow GitHub Actions to create and approve pull requests. Release Please
   needs it.
2. Create the CurseForge project (NeoForge, client) and the Modrinth project. Upload `heavymatica-512.png` as the icon.
   Put the network disclosure on both pages: "Contacts heavymatica.com when you open the schematic library; can be
   turned off in the settings." (from the change `cloud-availability`).
3. Repository variables `CURSEFORGE_ID_HEAVYMATICA` and `MODRINTH_ID_HEAVYMATICA`, and secrets `CURSEFORGE_TOKEN`
   and `MODRINTH_TOKEN`. Either platform can be added later. Until then it is skipped.
4. Check that CurseForge lists the game version from `minecraft_version` (26.1.2). Otherwise its upload fails.

## Risks / Trade-offs

- [Shared files outside the package folder, such as `src/mod/build.gradle`, `src/mod/gradle.properties` (Minecraft
  and NeoForge versions) or the contract in `src/api`, change the jar but do not count as mod commits] → for such a
  change, add a `Release-As: x.y.z` footer, or touch the package with a `fix` commit that says what changed. Accepted
  rather than widening the package path to `src/mod`, which would mix in the later `heavymatica_machines`.
- [CurseForge does not list 26.1.2 yet] → the CurseForge upload fails while GitHub and Modrinth succeed. D7 step 4
  checks it before the first release. mc-publish's `fail-mode` can be set to `warn` if it keeps happening.
- [The first public changelog depends on a hand edit (D2)] → the release PR is reviewed before merging anyway, and
  "No release without notes" catches an empty section.
- [The `--body` output name is wrong for the action version] → D3 falls back to the CHANGELOG section. The first run
  shows which path was taken.
- [A release is created but publishing fails halfway] → the tag and GitHub release stay. The publish job is re-run
  from the Actions UI (`workflow_dispatch` is not needed, because re-running the failed job reuses the same outputs).

## Migration Plan

1. Merge this change on `main`: workflows, config, `gradlew` mode, logo. The build workflow runs on that push. The
   release job opens the first release PR (0.2.0, from `release-as`) once a commit touches the mod.
2. Do the manual setup (D7) before merging that PR.
3. Edit the PR's changelog to the first-alpha summary and merge. Check the GitHub release, CurseForge and Modrinth.
4. Remove `release-as` from the config.

Rollback: delete the two workflow files. Tags and releases already made stay, and nothing in the mod depends on them.

## Open Questions

- The disclosure wording is final only once `cloud-availability` is implemented. It is page text, so it can change
  without touching this pipeline.
