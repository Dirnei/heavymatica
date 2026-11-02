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
  skips the platform, notes are required, and the type comes from the version. This design follows it, and adds notes
  generated from the commits so that a release needs no file edits.
- The decisions were made with the user in the exploration that preceded this change. See proposal.md for the why.

## Goals / Non-Goals

**Goals:**
- One tag push publishes everywhere. No file is edited and nothing is committed for a release, and no personal access
  token is needed.
- The build that is published is the build that was tested, at exactly the released version.
- Adding `heavymatica_machines` later is one more tag prefix and folder, not a new pipeline.

**Non-Goals:**
- CI or releases for `src/cloud` (.NET), `src/web` or the conformance generator. They are deployed, not distributed,
  and get their own pipeline once hosting exists.
- Signing jars, Maven publishing, or uploading to other platforms.
- Writing the CurseForge or Modrinth project page text, beyond the network disclosure line it has to carry.

## Decisions

### D1: The tag is the release

`.github/workflows/release.yml` runs on `push: tags: ['heavymatica-v*']`. A tag pushed by a person triggers workflows
(only tags made with `GITHUB_TOKEN` don't), so no personal access token is needed.

- **Version:** the tag minus `heavymatica-v`. It must match `^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$`, otherwise the
  run fails before building ("Releasing by pushing a version tag").
- **Jar version:** the root `build.gradle` already uses `release_version_<project>` when it is given, so the release
  build passes `-Prelease_version_heavymatica=<version>`. Nothing in the repository is rewritten.
- **Development version:** `mod_version` in `src/mod/heavymatica/gradle.properties` becomes `0.0.0-dev`, once. It is
  never edited for a release, so every build without the property reports the development version.
- **Tags per mod:** `heavymatica_machines` later gets `heavymatica_machines-v*` and its own folder. The trigger pattern
  `heavymatica-v*` does not match it, because of the `-v` right after the name.
- *Alternative: Release Please.* Tried first and dropped: it needs an open release PR, a forced first version through
  `release-as`, a `bootstrap-sha` that breaks when history is rewritten, and the repository setting that lets Actions
  create pull requests. It also commits the version and changelog, which is exactly the file editing a release should
  not need.

### D2: Notes generated from the commits

`.github/scripts/release-notes.sh <tag>` prints the notes and runs the same way locally and in the workflow:

```
prev = git describe --tags --abbrev=0 --match 'heavymatica-v*' <tag>^    (none -> first release)
message = annotated tag message, only if git cat-file -t <tag> == tag
list = git log --format=%s <prev>..<tag> -- src/mod/heavymatica
         feat(scope)!: subject  -> Features:  - **scope:** subject
         fix(scope)!: subject   -> Bug fixes: - **scope:** subject
         anything else          -> left out
notes = message, blank line, "### Features" / "### Bug fixes" sections (only the non-empty ones)
first release: notes = message only
```

- **Lightweight vs annotated:** `git tag -l --format='%(contents)'` returns the commit message for a lightweight tag,
  so the message is read only when the tag object's type is `tag`.
- **Checkout:** `actions/checkout@v4` with `fetch-depth: 0` (history and tags), then
  `git fetch --force origin "refs/tags/<tag>:refs/tags/<tag>"`, so the annotated tag object is there and not a
  lightweight ref to the commit.
- **Commits by path:** a commit counts for the mod when it touches `src/mod/heavymatica/**`, whatever its scope.
- **Empty notes:** the publish job fails with "Release notes are missing for <version>" before any upload.
- *Alternative: notes from a hand-edited CHANGELOG.md.* Rejected: it is a file to edit before every release.

### D3: One workflow, build then publish

```
push tag heavymatica-v<version>
   |
   v
meta             version from the tag (fails on a bad version), notes from release-notes.sh (fails when empty),
   |             type (D4), minecraft_version
   v
build            (uses: ./.github/workflows/build.yml at the tagged commit)
   |
   v
publish          ./gradlew :heavymatica:build -Prelease_version_heavymatica=<version>
                 -> Kira-NT/mc-publish@v3: GitHub release for the tag + CurseForge + Modrinth
```

- **Meta first:** a bad version or empty notes stop the run before the build spends time on it.
- **Concurrency:** `group: release`, `cancel-in-progress: false`, so two quick tags never publish over each other.
- **Build again in publish:** the reusable build checks the commit at the dev version. The publish job builds once
  more with `-Prelease_version_heavymatica`, as servus does, so the jar carries the released version.

### D4: Platform targets and the version type

Inputs to `mc-publish`:

| Input | Value |
| --- | --- |
| `files` | `src/mod/heavymatica/build/libs/heavymatica-<version>.jar` (exact name, so no sources jar) |
| `name` | `Heavymatica <version>` |
| `version` | `<version>` |
| `version-type` | see below |
| `changelog` | the notes from D2 |
| `loaders` | `neoforge` |
| `game-versions` | `minecraft_version` from `src/mod/gradle.properties` |
| `java` | `25` |
| `dependencies` | `jade(optional)`, matching `neoforge.mods.toml` |
| `github-tag` | `heavymatica-v<version>`; mc-publish creates the GitHub release for it |
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

1. Create the CurseForge project (NeoForge, client) and the Modrinth project. Upload `heavymatica-512.png` as the icon.
   Put the network disclosure on both pages: "Contacts heavymatica.com when you open the schematic library; can be
   turned off in the settings." (from the change `cloud-availability`).
2. Repository variables `CURSEFORGE_ID_HEAVYMATICA` and `MODRINTH_ID_HEAVYMATICA`, and secrets `CURSEFORGE_TOKEN`
   and `MODRINTH_TOKEN`. Either platform can be added later. Until then it is skipped.
3. Check that CurseForge lists the game version from `minecraft_version` (26.1.2). Otherwise its upload fails.

## Risks / Trade-offs

- [Shared files outside the mod folder, such as `src/mod/build.gradle`, `src/mod/gradle.properties` (Minecraft and
  NeoForge versions) or the contract in `src/api`, change the jar but don't appear in the notes] → mention such changes
  in the tag message. Accepted rather than widening the path to `src/mod`, which would mix in the later
  `heavymatica_machines`.
- [A tag on a commit that is not on `main` still releases] → accepted: only the owner pushes tags, and the build and
  tests still gate the publish.
- [CurseForge does not list 26.1.2 yet] → the CurseForge upload fails while GitHub and Modrinth succeed. D7 step 3
  checks it before the first release. mc-publish's `fail-mode` can be set to `warn` if it keeps happening.
- [mc-publish does not create the GitHub release for an existing tag] → the publish job creates it first with
  `gh release create <tag> --notes-file` and lets mc-publish attach the jar. The first release shows which is needed.
- [Publishing fails halfway] → re-run the failed job from the Actions UI. For a wrong tag, delete it locally and on
  the remote, along with any GitHub release it made, and push it again.

## Migration Plan

1. Land this change on `main`: workflows, notes script, Release Please removed, dev version, `gradlew` mode, logo.
   The build workflow runs on that push. Nothing is released.
2. Do the manual setup (D7).
3. Release the first version: `git tag -a heavymatica-v0.1.0 -m "First public alpha"` and
   `git push origin heavymatica-v0.1.0`. Check the GitHub release, CurseForge and Modrinth.

Rollback: delete the two workflow files. Tags and releases already made stay, and nothing in the mod depends on them.

## Open Questions

- The disclosure wording is final only once `cloud-availability` is implemented. It is page text, so it can change
  without touching this pipeline.
