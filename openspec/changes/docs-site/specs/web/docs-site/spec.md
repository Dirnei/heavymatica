# Spec Delta

## Purpose

Defines the public documentation site: where players and developers find it, what it covers for each, which parts are
generated from the mod so they cannot drift, how the architecture is shown and how the site is published.

## ADDED Requirements

### Requirement: Public site
The documentation SHALL be published at `https://docs.heavymatica.com` over HTTPS as a static site. It SHALL work without JavaScript for reading text, and SHALL offer a search over all pages. Its sources SHALL live in `docs/` at the repository root.

#### Scenario: Opening the site
- **WHEN** someone opens `https://docs.heavymatica.com`
- **THEN** the start page explains what Heavymatica is and leads to installing it and to the player guide

#### Scenario: Searching
- **WHEN** a reader searches for a feature name such as "Easy Place"
- **THEN** the search lists the page that explains it

### Requirement: Player documentation first
The site SHALL put player documentation first in its navigation and developer documentation after it.

#### Scenario: Navigation order
- **WHEN** a reader opens the navigation
- **THEN** the player sections come before the developer sections

### Requirement: Player feature coverage
The player documentation SHALL cover every player-facing feature of the mod and the player website, including installing, a first build, the remote and keys, placement, layers, comparison, materials, Easy Place, capture, import and export, creative paste, the library, the website, settings, compatibility and troubleshooting.

#### Scenario: Feature coverage
- **WHEN** a player looks up any feature named in this requirement
- **THEN** a player page explains what it does, how to use it and which keys or settings belong to it

### Requirement: Loader differences
The player documentation SHALL describe both Fabric and NeoForge wherever they differ.

#### Scenario: Loader differences
- **WHEN** a feature works differently on Fabric and on NeoForge, such as where the settings button is
- **THEN** the page says how it works on each loader

### Requirement: Developer documentation
The site SHALL contain developer documentation covering the architecture, the repository layout, building and testing, the mod projects and loaders, the cloud, the websites, the API, local development, the development aids and releasing. `CONTRIBUTING.md` SHALL keep only a short guide to contributing with local build and run tips and SHALL link to the developer documentation. `README.md` SHALL keep the introduction and install steps and SHALL link to the site.

#### Scenario: Contributor starts
- **WHEN** a contributor opens `CONTRIBUTING.md`
- **THEN** it tells them how to build and run locally and links to the developer pages for the details

### Requirement: Architecture diagrams
The developer documentation SHALL show interactive architecture diagrams generated from one architecture model kept in the repository. There SHALL be at least a landscape view of all parts and external services, a view of the mod and a view of the cloud. A change to the model SHALL change every diagram that shows the changed element on the next publish.

#### Scenario: Landscape
- **WHEN** a developer opens the architecture page
- **THEN** a diagram shows the mod, the cloud, the player website, the admin site, their storage and the external services they talk to

### Requirement: Generated settings and key reference
The settings reference SHALL list every client setting with its section, name, description, default and allowed values, taken from the mod's default settings file. The key reference SHALL list every Heavymatica key with its name, default binding, category and when it is active, taken from a key list that the mod's tests check against the mod. A setting or key added, removed or changed in the mod without updating the reference SHALL fail the mod's tests.

#### Scenario: New setting
- **WHEN** a developer adds a setting and updates the default settings file the tests require
- **THEN** the next published settings reference lists it without editing a docs page

#### Scenario: Key changed without the list
- **WHEN** a developer changes a key's default binding but not the key list
- **THEN** the mod's tests fail and name the key

### Requirement: Unreleased features
A page or a section SHALL be able to mark a feature as coming in the next release, so readers can tell it apart from what the latest release does.

#### Scenario: Marked feature
- **WHEN** a section is marked as unreleased
- **THEN** the page shows a visible "next release" marker next to it

### Requirement: Publishing
Every push to `main` that changes the site's sources SHALL build the site and publish it. A build that fails, including broken internal links, SHALL publish nothing and leave the current site online. Pull requests SHALL build the site without publishing it.

#### Scenario: Docs change on main
- **WHEN** a commit that changes `docs/` is pushed to `main`
- **THEN** the site at `docs.heavymatica.com` shows the change within minutes

#### Scenario: Broken link
- **WHEN** a page links to a docs page that does not exist
- **THEN** the build fails and the published site stays as it was
