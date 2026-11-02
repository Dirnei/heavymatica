## 1. Build on every push and pull request

- [x] 1.1 Mark `src/mod/gradlew` executable in git (`git update-index --chmod=+x src/mod/gradlew`). Verify `git ls-files -s src/mod/gradlew` shows `100755`.
- [ ] 1.2 Add `.github/workflows/build.yml` as in D5: triggers `push` (all branches), `pull_request` and `workflow_call`; Temurin 25, Node 22 with the npm cache keyed on `src/api/package-lock.json`, `gradle/actions/setup-gradle@v4`, `./gradlew build --no-daemon` in `src/mod`, and the jar uploaded with `if-no-files-found: error`. Verify on a pushed branch that the run is green and offers `heavymatica-0.0.0-dev.jar` (after 2.2).

## 2. Remove Release Please, set the development version

- [x] 2.1 Delete `release-please-config.json`, `.release-please-manifest.json` and `src/mod/heavymatica/CHANGELOG.md`, which were added for Release Please (D1 alternative). Verify `git ls-files | grep -i -E "release-please|CHANGELOG"` finds nothing.
- [x] 2.2 In `src/mod/heavymatica/gradle.properties`, remove the `# x-release-please-start-version` and `# x-release-please-end` lines and set `mod_version=0.0.0-dev` (D1). Verify `./gradlew :heavymatica:jar` in `src/mod` produces `heavymatica-0.0.0-dev.jar`, and that `-Prelease_version_heavymatica=0.3.0` produces `heavymatica-0.3.0.jar` with `version="0.3.0"` in the generated `neoforge.mods.toml` ("Development builds are not releases", "Released jar matches the release").

## 3. Tag-triggered publishing

- [x] 3.1 Add `.github/scripts/release-notes.sh <tag>` (D2): previous `heavymatica-v*` tag by `git describe`, the annotated message only when `git cat-file -t` says `tag`, `feat` and `fix` subjects of commits touching `src/mod/heavymatica` as `- **scope:** subject` under "### Features" and "### Bug fixes", the message alone for a first release, and empty output when there is nothing. Verify locally with throwaway tags that are deleted afterwards: a lightweight tag on `HEAD` with an annotated older tag before it lists only mod `feat`/`fix` commits (no `docs`, no cloud-only commits); an annotated tag puts its message on top; a first annotated tag prints only its message; a first lightweight tag prints nothing.
- [x] 3.2 Rework `.github/workflows/release.yml` (D1, D3): trigger `push: tags: ['heavymatica-v*']`; remove the `release-please` job and its outputs and `pull-requests: write`; add a `meta` job that checks out with `fetch-depth: 0`, re-fetches the tag, parses and checks the version, runs `release-notes.sh` and fails with "Release notes are missing for <version>" when empty, and outputs version, notes, type and `minecraft_version`; then `build` (`uses: ./.github/workflows/build.yml`) and `publish` with `needs: [meta, build]`, building with `-Prelease_version_heavymatica` and calling `mc-publish` with `github-tag` set to the pushed tag (D4). Verify the version check and the type rules by running the step's script locally for `heavymatica-v0.3.0`, `heavymatica-v1.2.0`, `heavymatica-v1.3.0-beta.1`, `heavymatica-v1.4.0-alpha.2` (alpha, release, beta, alpha) and `heavymatica-v0.3` (fails).
- [ ] 3.3 Run the workflow files through `actionlint` (or the GitHub Actions VS Code linter) and fix what it reports. Verify that no error remains.

## 4. Logo

- [x] 4.1 Add `src/branding/generate.py` (D6). It writes `heavymatica.svg` and `heavymatica-small.svg` from concept A's geometry and exports `heavymatica-512.png`, and the 256×256 `src/mod/heavymatica/src/main/resources/heavymatica.png`, rasterised with Pillow, with a clear message when Pillow is missing. Add a short `src/branding/README.md` with how to run it. Verify by running it, checking both PNG sizes with Python, and comparing the SVG in a browser against concept A on https://claude.ai/artifact/7fDyZZcpQ5G3Dysrn3Koo4.
- [x] 4.2 Add `logoFile="heavymatica.png"` to the `[[mods]]` block of `src/mod/heavymatica/src/main/templates/META-INF/neoforge.mods.toml`. Verify in `runClient` that the mods list shows the logo for Heavymatica ("Mod logo").

## 5. Documentation and first release

- [x] 5.1 Rewrite the "Releasing" section of the root `README.md` for the tag release: push `heavymatica-v<version>`, optionally annotated with a message; notes from `feat`/`fix` commits touching the mod; the first release needs a message; the 0.x/alpha/beta rules; the repository variables and secrets; shared files outside the mod folder go in the tag message; how to redo a failed release. Verify that the section matches the workflow files and mentions neither Release Please nor a release PR.
- [ ] 5.2 Manual, by the user (D7): create the CurseForge and Modrinth projects with `heavymatica-512.png` as the icon and the network disclosure on both pages; add `CURSEFORGE_ID_HEAVYMATICA`, `MODRINTH_ID_HEAVYMATICA`, `CURSEFORGE_TOKEN` and `MODRINTH_TOKEN`; check that CurseForge lists the game version from `minecraft_version`. Verify each item is ticked off with the user.
- [ ] 5.3 Release the first version with `git tag -a heavymatica-v0.1.0 -m "First public alpha"` and `git push origin heavymatica-v0.1.0`. Verify, against the spec's scenarios:
  - the GitHub release `heavymatica-v0.1.0` exists with `heavymatica-0.1.0.jar` and the notes "First public alpha"
  - CurseForge and Modrinth list 0.1.0 as alpha with the same notes
  - the downloaded jar shows version 0.1.0 and the logo in the mods list
  - `main` got no commit from the release

## Workflow follow-up

- The disclosure text on the project pages depends on `cloud-availability` being implemented.
- Commit the implementation once all tasks up to 5.1 are done (Conventional Commits, for example `build(release): publish the mod on version tags`).
- Archive this change after the first release has gone out.
