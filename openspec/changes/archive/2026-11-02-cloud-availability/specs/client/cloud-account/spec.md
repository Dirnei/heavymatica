# Spec Delta

## MODIFIED Requirements

### Requirement: First sign-in is explicit
Without a stored session, the client SHALL NOT contact the Heavymatica Cloud until the player opens the library. Until the player signs in, the only request SHALL be the anonymous status request of `client/cloud-availability`. With `cloud.access` on `OFF`, the client SHALL contact the cloud not even then.

#### Scenario: Fresh install
- **WHEN** the mod is installed, the player has never signed in and plays without opening the library
- **THEN** the client makes no request to the Heavymatica Cloud

#### Scenario: Library opened before signing in
- **WHEN** a player who has never signed in opens the library
- **THEN** the only request to the Heavymatica Cloud is the status request, until the player chooses "Sign in"

### Requirement: Account status visible
While the library shows the cloud parts, the client SHALL show in the library whether the player is signed in, and when signed in, their name and storage usage against quota. While the cloud parts are hidden, the library SHALL show no account status.

#### Scenario: Usage shown
- **WHEN** a signed-in player opens the library
- **THEN** it shows their Minecraft name and storage used out of their quota

#### Scenario: Cloud hidden
- **WHEN** a player without a stored session opens the library while the cloud is not open
- **THEN** the library shows neither a signed-out state nor a Sign in action
