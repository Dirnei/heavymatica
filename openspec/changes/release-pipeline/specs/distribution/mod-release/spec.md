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

### Requirement: Release PR from Conventional Commits
The repository SHALL keep one open release pull request per mod. It SHALL be updated whenever commits that touch that
mod's folder land on `main`. The pull request SHALL raise the mod's version and add a changelog section listing those
commits' features and fixes. Commits outside the mod's folder SHALL NOT appear in it.

#### Scenario: A feature lands
- **WHEN** a `feat(...)` commit touching `src/mod/heavymatica` lands on `main` and the mod's last release is 0.2.0
- **THEN** the release pull request proposes 0.3.0 and lists the feature under the new version in the mod's changelog

#### Scenario: Only a cloud change lands
- **WHEN** a commit lands that only touches `src/cloud`
- **THEN** no release pull request for the mod is opened or changed by it

### Requirement: Version rules before 1.0
While the mod's major version is 0, a feature SHALL raise the minor version and a fix SHALL raise the patch version. A
breaking change SHALL raise the minor version and SHALL NOT raise the version to 1.0.

#### Scenario: Breaking change before 1.0
- **WHEN** the last release is 0.4.2 and a `feat!` commit lands for the mod
- **THEN** the release pull request proposes 0.5.0

#### Scenario: Only fixes
- **WHEN** the last release is 0.4.2 and only `fix` commits landed for the mod since
- **THEN** the release pull request proposes 0.4.3

### Requirement: Merging the release PR releases the mod
Merging a mod's release pull request SHALL create the tag `<mod>-v<version>` and a GitHub release for it. The release
notes SHALL be the new changelog section, and the release SHALL carry the mod's jar built at that version.

#### Scenario: Release created
- **WHEN** the release pull request proposing 0.3.0 for `heavymatica` is merged
- **THEN** the tag `heavymatica-v0.3.0` exists, and its GitHub release lists the changes and offers `heavymatica-0.3.0.jar`

### Requirement: Released jar matches the release
The released jar SHALL report the released version as its mod version. It SHALL be built only after the build and
tests of that commit have passed.

#### Scenario: Version in the mods list
- **WHEN** a player installs the jar from the `heavymatica-v0.3.0` release
- **THEN** the game's mods list shows Heavymatica version 0.3.0

#### Scenario: Tests fail on the release commit
- **WHEN** the merged release commit fails its tests
- **THEN** nothing is attached to the release and nothing is published to CurseForge or Modrinth

### Requirement: Publishing to CurseForge and Modrinth
Each release SHALL be published to CurseForge and to Modrinth for NeoForge, the mod's Minecraft version and Java 25,
with the release notes as its changelog. A platform SHALL be skipped when the repository has no project ID for it, and
the GitHub release SHALL be made either way.

#### Scenario: Both platforms configured
- **WHEN** a release is created and both project IDs are set
- **THEN** the same jar appears as a new file on the CurseForge project and as a new version on the Modrinth project

#### Scenario: Modrinth not configured yet
- **WHEN** a release is created and only the CurseForge project ID is set
- **THEN** the jar is published to CurseForge and the GitHub release, and the run succeeds without touching Modrinth

### Requirement: No release without notes
A release whose notes are empty SHALL NOT be published to any platform. The run SHALL fail and say that the notes are
missing.

#### Scenario: Empty notes
- **WHEN** a release is created with an empty changelog section
- **THEN** the publish fails with a message about the missing notes and no platform gets a new file

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
