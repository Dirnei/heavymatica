## 1. Build on every push and pull request

- [x] 1.1 Mark `src/mod/gradlew` executable in git (`git update-index --chmod=+x src/mod/gradlew`). Verify `git ls-files -s src/mod/gradlew` shows `100755`.
- [ ] 1.2 Add `.github/workflows/build.yml` as in D5: triggers `push` (all branches), `pull_request` and `workflow_call`; Temurin 25, Node 22 with the npm cache keyed on `src/api/package-lock.json`, `gradle/actions/setup-gradle@v4`, `./gradlew build --no-daemon` in `src/mod`, and the jar uploaded with `if-no-files-found: error`. Verify on a pushed branch that the run is green and offers `heavymatica-0.1.0.jar`. Then make a test fail on a throwaway branch and check that the run fails ("Every change is built and tested"). Delete that branch afterwards.

## 2. Release Please

- [x] 2.1 In `src/mod/heavymatica/gradle.properties`, wrap `mod_version` in `# x-release-please-start-version` and `# x-release-please-end` (D1). Verify that `./gradlew :heavymatica:jar` in `src/mod` still produces `heavymatica-0.1.0.jar`, and that the generated `neoforge.mods.toml` in `build/` says `version="0.1.0"`.
- [x] 2.2 Add `release-please-config.json` (D1, with `"release-as": "0.2.0"` for the first release, D2) and `.release-please-manifest.json` with `{"src/mod/heavymatica": "0.1.0"}`. Set `bootstrap-sha` to the parent of the commit that adds them, filled in when committing. Add an empty `src/mod/heavymatica/CHANGELOG.md` with a one-line heading. Verify both JSON files parse (`python -m json.tool`).
- [ ] 2.3 Add `.github/workflows/release.yml` with the `release-please` job (D3: `googleapis/release-please-action@v4`, `GITHUB_TOKEN`, permissions `contents: write` and `pull-requests: write`, concurrency group `release`). Gate the following jobs on `outputs['src/mod/heavymatica--release_created']`. Verify after pushing that a release PR titled for `heavymatica` 0.2.0 is opened, once the Actions permission from 5.1 is set. It must list only commits after `bootstrap-sha` that touch `src/mod/heavymatica`, and change `mod_version` and `CHANGELOG.md` only.

## 3. Publishing

- [x] 3.1 In `release.yml`, add the `build` job (`uses: ./.github/workflows/build.yml`) and the `publish` job (`needs: build`, D3/D4). The publish job reads version, tag and notes from the release-please outputs, falling back to the `## <version>` section of `CHANGELOG.md` for the notes. It fails with "Release notes are missing" when they are empty, works out the version type with the D4 rules, and reads `minecraft_version` from `src/mod/gradle.properties`. It builds with `-Prelease_version_heavymatica=<version>` and calls `Kira-NT/mc-publish@v3` with the inputs in D4, passing each platform token only when its project ID variable is set. Verify the type rules with a small shell test in the workflow step's script, or by running the step's script locally for `0.3.0`, `1.2.0`, `1.3.0-beta.1` and `1.4.0-alpha.2`: the results must be alpha, release, beta, alpha.
- [ ] 3.2 Run the release workflow file through `actionlint` (or the GitHub Actions VS Code linter) and fix what it reports. Verify that no error remains.

## 4. Logo

- [x] 4.1 Add `src/branding/generate.py` (D6). It writes `heavymatica.svg` and `heavymatica-small.svg` from concept A's geometry and exports `heavymatica-512.png`, and the 256×256 `src/mod/heavymatica/src/main/resources/heavymatica.png`, rasterised with Pillow, with a clear message when Pillow is missing. Add a short `src/branding/README.md` with how to run it. Verify by running it, checking both PNG sizes with Python, and comparing the SVG in a browser against concept A on https://claude.ai/artifact/7fDyZZcpQ5G3Dysrn3Koo4.
- [x] 4.2 Add `logoFile="heavymatica.png"` to the `[[mods]]` block of `src/mod/heavymatica/src/main/templates/META-INF/neoforge.mods.toml`. Verify in `runClient` that the mods list shows the logo for Heavymatica ("Mod logo").

## 5. First release

- [ ] 5.1 Manual, by the user (D7): allow GitHub Actions to create pull requests; create the CurseForge and Modrinth projects with `heavymatica-512.png` as the icon and the network disclosure on both pages; add `CURSEFORGE_ID_HEAVYMATICA`, `MODRINTH_ID_HEAVYMATICA`, `CURSEFORGE_TOKEN` and `MODRINTH_TOKEN`; check that CurseForge lists the game version from `minecraft_version`. Verify each item is ticked off with the user.
- [ ] 5.2 Edit the first release PR's changelog section to the "First public alpha" summary and merge it. Verify, against the spec's scenarios:
  - `heavymatica-v0.2.0` and its GitHub release exist with `heavymatica-0.2.0.jar`
  - CurseForge and Modrinth list 0.2.0 as alpha with the same notes
  - the downloaded jar shows version 0.2.0 and the logo in the mods list
- [ ] 5.3 Remove `release-as` from `release-please-config.json` (D2). Verify that the next mod commit's release PR proposes the normal next version.
- [x] 5.4 Add a short "Releasing" section to the root `README.md`: Conventional Commits drive the release PR; merging it publishes; the repository variables and secrets; the `Release-As` footer for build-only changes (design risks). Verify that the section matches the workflow files.

## Workflow follow-up

- The disclosure text on the project pages depends on `cloud-availability` being implemented.
- Archive this change after the first release has gone out.
