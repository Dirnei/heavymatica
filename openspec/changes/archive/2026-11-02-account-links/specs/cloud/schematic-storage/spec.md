# Spec Delta

## MODIFIED Requirements

### Requirement: Upload creates a schematic or a new version
The service SHALL accept a schematic file upload either as a new schematic or as a new version of an existing schematic that the caller's account owns. A new schematic SHALL be owned by the caller's account, SHALL start at version 1, and each new version SHALL take the next version number.

#### Scenario: New schematic
- **WHEN** a signed-in player uploads a valid file without naming an existing schematic
- **THEN** a new schematic owned by that player's account exists with version 1, named after the given name or, if none is given, the file name

#### Scenario: New version
- **WHEN** a player uploads a valid file for a schematic their account owns whose latest version is 3
- **THEN** the schematic has version 4 and versions 1 to 3 are unchanged

#### Scenario: Upload from the website
- **WHEN** a player signed in on the website uploads a valid file as a new schematic
- **THEN** the new schematic is owned by the account of their website session, exactly as for an upload from the game

#### Scenario: New version from an alt
- **WHEN** a player signed in as `SteveBuilds` uploads a new version of a schematic uploaded as `Steve`, and both identities are linked to the same account
- **THEN** the schematic gets the new version

### Requirement: Owner-only access
The service SHALL let only the account that owns a schematic read, download, change or delete it, through any identity linked to that account. To anyone else, a schematic SHALL be indistinguishable from one that does not exist.

#### Scenario: Another player's schematic
- **WHEN** a player requests a schematic ID owned by a different account
- **THEN** the service responds exactly as it does for a non-existent schematic

#### Scenario: Separate GitHub and Minecraft accounts
- **WHEN** a player signed in on the website with GitHub requests a schematic they uploaded from the game, and the GitHub and Minecraft identities belong to different accounts
- **THEN** the service responds exactly as it does for a non-existent schematic

#### Scenario: Unlinked identity
- **WHEN** an identity is unlinked from an account and then signs in to a new account
- **THEN** the schematics of the old account are reported as not found to it

### Requirement: Storage quota
The service SHALL enforce a storage quota per account, an entitlement of the account shared by all its identities. Usage SHALL be the sum of the sizes of all the account's stored versions and thumbnails. The service SHALL report usage and quota to the owner, and linking or unlinking identities SHALL change neither.

#### Scenario: Upload over quota
- **WHEN** an upload would take the account's usage above its quota
- **THEN** the service rejects it as over quota and stores nothing

#### Scenario: Usage reported
- **WHEN** an owner asks for their storage usage
- **THEN** the service returns the bytes used and the quota

#### Scenario: Quota kept through the migration
- **WHEN** a Minecraft-keyed account with a quota of 100 MiB and 40 MiB used is migrated
- **THEN** the migrated account reports 40 MiB used of 100 MiB

#### Scenario: Alts share the quota
- **WHEN** `Steve` has used 90 MiB of a 100 MiB quota and `SteveBuilds`, linked to the same account, uploads a 20 MiB file
- **THEN** the service rejects it as over quota
