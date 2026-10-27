# Spec Delta

## ADDED Requirements

### Requirement: Accounts hold linked identities
An account SHALL own its schematics and storage usage and SHALL hold one or more linked identities: Minecraft identities (a UUID verified through Mojang, with its last known name) and web identities (a provider and the provider's stable user ID, never an email address). An account MAY hold several of each kind. An identity SHALL belong to at most one account.

#### Scenario: GitHub and Minecraft accounts are separate
- **WHEN** a player registers on the website with GitHub and also signs in from the game with their Minecraft account
- **THEN** there are two accounts, one holding the GitHub identity and one holding the Minecraft identity, each with its own library

#### Scenario: Identity already taken
- **WHEN** an identity that is already linked to an account would be added to another account
- **THEN** the service rejects it as already linked and neither account changes

### Requirement: Account IDs are not identities
Each account SHALL have an opaque ID that is not derived from any identity. Ownership, quota and sign-in sessions SHALL refer to the account ID, never to a Minecraft UUID or a provider user ID.

#### Scenario: New GitHub account
- **WHEN** a player registers with the GitHub user ID `583231`
- **THEN** the new account's ID is neither `583231` nor derived from it, and the account holds the identity (`github`, `583231`)

#### Scenario: Migrated Minecraft account
- **WHEN** an account keyed by a Minecraft UUID is migrated
- **THEN** its new ID is not that UUID, and its Minecraft identity holds the UUID

### Requirement: Identity limit
An account SHALL hold at most 10 linked identities.

#### Scenario: Eleventh identity
- **WHEN** an identity would be added to an account that already holds 10
- **THEN** the service rejects it as too many identities and the account is unchanged

### Requirement: Account audit log
The service SHALL record an audit entry for every account creation and migration, and every identity added to or removed from an account. Each entry SHALL hold the time, the account and the acting identity. Entries SHALL be kept for as long as the account exists.

#### Scenario: Registration recorded
- **WHEN** a player registers with GitHub
- **THEN** the audit log holds an account creation entry with the time, the new account and the GitHub identity

#### Scenario: Migration recorded
- **WHEN** an existing Minecraft-keyed account is migrated
- **THEN** the audit log holds a migration entry for the new account naming its Minecraft identity

### Requirement: Existing accounts are migrated
When this version first starts, every existing UUID-keyed account SHALL become an account with a new ID and one Minecraft identity holding its UUID and name. Its schematics, versions, stored files, thumbnails, usage, quota and sign-in sessions SHALL move to the new ID before the service accepts requests. An interrupted migration SHALL complete on the next start without losing or duplicating data.

#### Scenario: Library intact
- **WHEN** an account with 3 schematics, 5 versions, 2 thumbnails and 4 MiB used is migrated
- **THEN** signing in with its Minecraft account shows the same 3 schematics with all versions, thumbnails and downloads, and 4 MiB used of the same quota

#### Scenario: Signed-in session survives
- **WHEN** a client signed in before the migration refreshes with its stored refresh token afterwards
- **THEN** it gets new tokens whose subject is the account's new ID

#### Scenario: Interrupted migration
- **WHEN** the service stops partway through the migration and starts again
- **THEN** every account ends up migrated exactly once, with no schematic, file or session lost

#### Scenario: Two instances start together
- **WHEN** two instances of the service start against the same unmigrated data
- **THEN** only one runs the migration, and the other accepts requests only after it has finished
