# Spec Delta

## ADDED Requirements

### Requirement: Place a version from the library
The entry details SHALL offer a Place action for the chosen version. Choosing it SHALL load that version (from the cache or by downloading it), close the library and start a new placement of it. A version that is unavailable offline SHALL have Place disabled with the reason shown.

#### Scenario: Place a cached version
- **WHEN** a player picks version 2 of `Starter Hut` and chooses Place
- **THEN** the library closes and a placement of version 2 starts

#### Scenario: Offline, not cached
- **WHEN** the cloud is unreachable and the chosen version is not in the cache
- **THEN** Place is disabled and says the version needs a connection

#### Scenario: Local entry
- **WHEN** a player chooses Place on a local-only entry while signed out
- **THEN** a placement of that local entry starts
