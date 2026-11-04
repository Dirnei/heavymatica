# Spec Delta

## Purpose

Defines how the documentation's screenshots are made: from the repository alone, the same way every time, without
touching a developer's own worlds or cloud data, and committed as plain images.

## ADDED Requirements

### Requirement: Reproducible screenshots
The documentation screenshots SHALL be produced by one local script from files in the repository: a world seed and
world settings, the commands and steps that set up each scene, and the website steps. The script SHALL NOT need a
committed world, committed schematic files from outside the repository's own sources, or a GitHub or Microsoft account.

#### Scenario: Fresh checkout
- **WHEN** a developer with the build tools runs the script on a fresh checkout
- **THEN** it writes every documentation screenshot without any manual step

#### Scenario: Same scene
- **WHEN** the script runs twice on the same game version
- **THEN** both runs show the same terrain, time of day, weather and builds in each screenshot

### Requirement: Isolated from developer data
The script SHALL use its own game run folder and a local cloud with throwaway storage. It SHALL NOT read or change the
developer's dev run folders, worlds, settings or local cloud data.

#### Scenario: Developer world untouched
- **WHEN** the script finishes
- **THEN** the developer's dev client run folder and local cloud volumes are as they were before

### Requirement: Committed images
The screenshots SHALL be committed as images next to the documentation sources and SHALL be rebuilt only by running
the script, not by the documentation build.

#### Scenario: Docs build
- **WHEN** the documentation site is built in CI
- **THEN** it uses the committed images and starts no game, cloud or browser
