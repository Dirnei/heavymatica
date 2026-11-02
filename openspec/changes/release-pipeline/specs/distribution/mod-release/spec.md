## Purpose

Defines how a version of the Heavymatica mod is checked, versioned, released and published to players, and how the mod
presents itself with its logo.

## ADDED Requirements

### Requirement: Every change is built and tested
Every push to any branch and every pull request SHALL build the mod, lint the cloud contract the mod's client is
generated from, and run the mod's tests. A failing build, lint or test SHALL fail the check. The built jar SHALL be
kept as a downloadable artifact of the run.

#### Scenario: Pull request with a failing test
- **WHEN** a pull request changes the mod so one of its tests fails
- **THEN** the pull request's build check fails

#### Scenario: Jar from a push
- **WHEN** a push to a branch builds cleanly
- **THEN** the run offers the built `heavymatica` jar as a download

### Requirement: Releasing by pushing a version tag
Pushing a tag `<mod>-v<version>`, where the version is `major.minor.patch` with an optional `-` suffix, SHALL release
that version of the mod. The version SHALL come only from the tag: a release SHALL NOT need any file in the repository
to be edited, and SHALL NOT commit anything. A tag with the mod's prefix and any other version form SHALL fail without
publishing.

#### Scenario: Version tag pushed
- **WHEN** the tag `heavymatica-v0.3.0` is pushed
- **THEN** version 0.3.0 of `heavymatica` is released, and `main` has no new commit from it

#### Scenario: Malformed version
- **WHEN** the tag `heavymatica-v0.3` is pushed
- **THEN** the run fails because the version is not valid, and nothing is published

### Requirement: Development builds are not releases
Builds made without a release tag SHALL report a development version, `0.0.0-dev`, as their mod version.

#### Scenario: Local build
- **WHEN** a developer builds the mod on their machine after 0.3.0 has been released
- **THEN** the jar reports version 0.0.0-dev, not 0.3.0

### Requirement: Release notes from the commits
The release notes SHALL list the commits since the mod's previous release tag, up to the new tag, that touch the mod's
folder: `feat` commits under "Features" and `fix` commits under "Bug fixes", each with its scope and subject. Commits
of other types, and commits that only touch other folders, SHALL NOT be listed.

#### Scenario: Features and fixes
- **WHEN** `heavymatica-v0.3.0` is pushed and since `heavymatica-v0.2.0` the mod got `feat(placement): add remote overlay` and `fix(library): keep selection after refresh`
- **THEN** the notes list "placement: add remote overlay" under Features and "library: keep selection after refresh" under Bug fixes

#### Scenario: Only a cloud change
- **WHEN** a `feat(cloud)` commit since the previous tag only touches `src/cloud`
- **THEN** it is not listed in the mod's notes

#### Scenario: Other commit types
- **WHEN** a `docs` or `refactor` commit since the previous tag touches the mod
- **THEN** it is not listed in the notes

### Requirement: Tag message in the notes
When the release tag is an annotated tag, its message SHALL appear above the generated list. When the mod has no
previous release tag, the message SHALL be the notes on their own, without a list of commits.

#### Scenario: Annotated tag
- **WHEN** `heavymatica-v0.4.0` is pushed as an annotated tag with the message "Layers on X and Z"
- **THEN** the notes start with "Layers on X and Z", followed by the features and fixes

#### Scenario: First release
- **WHEN** `heavymatica-v0.1.0` is the mod's first tag and is pushed with the message "First public alpha"
- **THEN** the notes are "First public alpha" and list no commits

### Requirement: No release without notes
A release whose notes are empty SHALL NOT be published anywhere. The run SHALL fail and say that the notes are
missing.

#### Scenario: Nothing to list
- **WHEN** a lightweight tag is pushed and no `feat` or `fix` commit touched the mod since the previous tag
- **THEN** the run fails with a message about the missing notes, and no GitHub release or platform file is made

#### Scenario: First tag without a message
- **WHEN** the mod's first tag is pushed as a lightweight tag
- **THEN** the run fails with a message about the missing notes

### Requirement: Released jar matches the release
The released jar SHALL report the released version as its mod version. It SHALL be built only after the build and
tests of the tagged commit have passed.

#### Scenario: Version in the mods list
- **WHEN** a player installs the jar from the `heavymatica-v0.3.0` release
- **THEN** the game's mods list shows Heavymatica version 0.3.0

#### Scenario: Tests fail on the tagged commit
- **WHEN** the tagged commit fails its tests
- **THEN** no GitHub release is made and nothing is published to CurseForge or Modrinth

### Requirement: Publishing to GitHub, CurseForge and Modrinth
Each release SHALL create a GitHub release for its tag, with the notes and the jar. It SHALL be published to CurseForge
and to Modrinth for NeoForge, the mod's Minecraft version and Java 25, with the notes as its changelog. A platform
SHALL be skipped when the repository has no project ID for it, and the GitHub release SHALL be made either way.

#### Scenario: Both platforms configured
- **WHEN** `heavymatica-v0.3.0` is released and both project IDs are set
- **THEN** the GitHub release offers `heavymatica-0.3.0.jar`, and the same jar appears as a new file on the CurseForge project and as a new version on the Modrinth project

#### Scenario: Modrinth not configured yet
- **WHEN** a release runs and only the CurseForge project ID is set
- **THEN** the jar is published to CurseForge and the GitHub release, and the run succeeds without touching Modrinth

### Requirement: Release channel follows the version
A version whose major number is 0 SHALL be published as alpha. From 1.0 on, a version containing `alpha` SHALL be
published as alpha, one containing `beta` as beta, and any other as a release.

#### Scenario: Pre-1.0 version
- **WHEN** version 0.3.0 is published
- **THEN** CurseForge and Modrinth list it as an alpha

#### Scenario: Release after 1.0
- **WHEN** version 1.2.0 is published
- **THEN** it is listed as a release, and version 1.3.0-beta.1 would be listed as a beta

### Requirement: Mod logo
The mod SHALL carry its logo, so the game's mods list shows it next to the mod's name. The same logo SHALL be available
from the repository as a 512×512 PNG for the project pages.

#### Scenario: Mods list
- **WHEN** a player opens the game's mods list and selects Heavymatica
- **THEN** the ghost-cube logo is shown with the mod's details
