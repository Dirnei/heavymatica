# Spec Delta

## Purpose

Defines the mod loaders Heavymatica runs on and what players can rely on across them: the same features, the same
settings file, the same client-only behavior on any server, and one release that ships every loader.

## ADDED Requirements

### Requirement: Fabric and NeoForge
Heavymatica SHALL run on NeoForge and on Fabric for the same Minecraft version. On Fabric it SHALL require Fabric API and SHALL need no other mod. Each loader SHALL get its own jar, and each jar SHALL refuse to load on the other loader instead of crashing later.

#### Scenario: Fabric client
- **WHEN** a player installs the Fabric jar together with Fabric API on a Fabric client
- **THEN** the game starts with Heavymatica loaded

#### Scenario: Fabric API missing
- **WHEN** a player installs the Fabric jar without Fabric API
- **THEN** the loader reports Fabric API as a missing dependency before the game starts

#### Scenario: Wrong jar
- **WHEN** a player puts the NeoForge jar into a Fabric instance
- **THEN** Fabric does not load it as a mod and the game does not crash because of it

### Requirement: Same features on every loader
Every client feature in the client specifications SHALL behave the same on Fabric and on NeoForge, including the keys and their default bindings, the remote, the wheel, ghosts, layers, comparison, material list, Easy Place, capture, import, paste and the cloud library. Differences SHALL be limited to those stated in this specification.

#### Scenario: Same key bindings
- **WHEN** a player opens the controls screen on Fabric and on NeoForge
- **THEN** both list the same Heavymatica keys with the same default bindings in the same category

#### Scenario: Remote keys share keys with vanilla
- **WHEN** a key of the remote is bound to the same key as a vanilla action and the player holds the remote
- **THEN** on both loaders the remote's key acts and the vanilla action does not, and without the remote the vanilla action acts

### Requirement: Shared settings file
On both loaders the client settings SHALL be stored in `config/heavymatica-client.toml` with the same sections, keys, comments, defaults and allowed ranges. A file written on one loader SHALL load on the other with the same values. Invalid or missing values SHALL fall back to their defaults on both loaders.

#### Scenario: Existing NeoForge settings
- **WHEN** a player with a `heavymatica-client.toml` from an earlier NeoForge version updates Heavymatica
- **THEN** every setting keeps its value and the file keeps its layout

#### Scenario: Move an instance to Fabric
- **WHEN** a player copies the config folder from a NeoForge instance into a Fabric instance with Heavymatica
- **THEN** Heavymatica on Fabric starts with the same settings

#### Scenario: Out of range on Fabric
- **WHEN** the file on Fabric sets the cache size to 10 MiB, below the allowed 64
- **THEN** Heavymatica uses the default cache size and writes the default back to the file

### Requirement: Editing the settings file while the game runs
On NeoForge, changes made to the settings file while the game runs SHALL be picked up as they are today. On Fabric, the file SHALL be read when the game starts; changes made to the file while the game runs SHALL take effect at the next start. Settings changed in the game SHALL apply at once and be saved on both loaders.

#### Scenario: File edited on Fabric
- **WHEN** a player edits the ghost opacity in the file while a Fabric game runs
- **THEN** the old opacity stays until the next start, and the edited value is used after it

### Requirement: Client-only on any server
On both loaders Heavymatica SHALL work on the client alone and SHALL let the client join servers that do not have Heavymatica, and servers that do, without a version check.

#### Scenario: Vanilla server from Fabric
- **WHEN** a Fabric client with Heavymatica joins a server without Heavymatica
- **THEN** it joins, and placements, ghosts, comparison and the library work

### Requirement: Optional integrations on both loaders
The Jade and Iris integrations SHALL work on both loaders when the player has those mods installed, and SHALL stay optional: Heavymatica SHALL work without them on both loaders.

#### Scenario: Jade on Fabric
- **WHEN** a Fabric player with Jade installed looks at a ghost
- **THEN** Jade's tooltip shows the ghost as it does on NeoForge

#### Scenario: No integrations
- **WHEN** a Fabric player has neither Jade nor Iris installed
- **THEN** Heavymatica loads and works without them

### Requirement: One release for both loaders
Each release SHALL ship a Fabric jar and a NeoForge jar with the same version and the same changelog, published together to every platform the release goes to, each marked with its loader. A release SHALL NOT go out when either jar fails to build or its tests fail.

#### Scenario: Release tag
- **WHEN** a release tag is pushed and both jars build and pass their tests
- **THEN** the release on GitHub, CurseForge and Modrinth carries a Fabric jar marked Fabric and a NeoForge jar marked NeoForge, both with that version

#### Scenario: Fabric build fails
- **WHEN** a release tag is pushed and the Fabric jar fails to build
- **THEN** neither jar is published
