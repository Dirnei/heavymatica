# client/cloud-availability Specification

## Purpose
Decides whether the game shows the Heavymatica Cloud and whether it may contact it at all: which address it talks to,
when it asks the cloud for its status, and how that answer, a stored session and the player's setting combine.

## Requirements

### Requirement: Production address
The client's default cloud address SHALL be `https://api.heavymatica.com`. A cloud address set in the client config SHALL replace it.

#### Scenario: Fresh install
- **WHEN** a player installs a released mod and opens the library with the cloud open
- **THEN** every cloud request goes to `https://api.heavymatica.com`

### Requirement: Dev runs use the local cloud
A game started through the project's dev run tasks SHALL use `http://localhost:5180`, or the address given in the `HEAVYMATICA_CLOUD_URL` environment variable, over both the default and the config file. A dev run SHALL NOT contact the production address unless that variable names it.

#### Scenario: Fresh clone
- **WHEN** a developer clones the repository and starts the dev client
- **THEN** its cloud requests go to `http://localhost:5180`, even though the newly written config file holds the production address

#### Scenario: Other local address
- **WHEN** the dev client is started with `HEAVYMATICA_CLOUD_URL=http://localhost:6000`
- **THEN** its cloud requests go to `http://localhost:6000`

### Requirement: Cloud access setting
The client setting `cloud.access` SHALL take `AUTO` (the default), `ON` or `OFF`. `AUTO` SHALL decide from the cloud's status. `ON` SHALL show the cloud parts without asking for the status. `OFF` SHALL hide every cloud part, and the client SHALL make no request to the cloud at all, the status included.

#### Scenario: Tester before launch
- **WHEN** the status says `closed`, the player sets `cloud.access` to `ON` and opens the library
- **THEN** the library offers Sign in without having asked for the status

#### Scenario: Off
- **WHEN** a player with a stored session sets `cloud.access` to `OFF` and opens the library
- **THEN** the library shows only local entries, offers no sign-in or account, and no request reaches the cloud

### Requirement: Status asked when the library opens
With `cloud.access` on `AUTO`, the client SHALL ask the cloud for its status when the player opens the library, and at no other time except Refresh. It SHALL ask in the background and SHALL show local entries at once. A successful answer SHALL be kept until the game closes. A failed request SHALL be asked again the next time the library opens.

#### Scenario: Game start
- **WHEN** the game starts and the player plays without opening the library
- **THEN** the client does not ask the cloud for its status

#### Scenario: Second opening
- **WHEN** the player opens the library a second time in the same game session after an answer arrived
- **THEN** the client does not ask again

#### Scenario: Slow cloud
- **WHEN** the player opens the library and the status takes several seconds to answer
- **THEN** local entries are listed at once, and the cloud parts appear when the answer arrives

#### Scenario: Refresh
- **WHEN** the player chooses Refresh in the library
- **THEN** the client asks for the status again

### Requirement: The status request is anonymous
The status request SHALL carry no token, cookie, Minecraft name or UUID, and nothing else that identifies the player.

#### Scenario: Inspected request
- **WHEN** the status request of a signed-in player is inspected
- **THEN** it has no Authorization header and no player data

### Requirement: Who sees the cloud
Without a stored session, the library SHALL show Sign in and the other cloud parts only while the status says `open`. A `closed` answer, an unreachable cloud, a timeout or an answer still pending SHALL hide them, with no error shown. With a stored session, the cloud parts SHALL stay whatever the status says.

#### Scenario: Not launched yet
- **WHEN** a player without a stored session opens the library and nothing answers at the cloud address
- **THEN** the library shows local entries only, with no Sign in and no error

#### Scenario: Open
- **WHEN** a player without a stored session opens the library and the status says `open`
- **THEN** the library offers Sign in

#### Scenario: Stored session while closed
- **WHEN** a signed-in tester opens the library and the status says `closed`
- **THEN** the library shows their cloud schematics as usual

#### Scenario: Stored session while unreachable
- **WHEN** a signed-in player opens the library and the cloud cannot be reached
- **THEN** the library shows their last known cloud listing marked as offline

### Requirement: Too old for the cloud
When the status names a `minClient` newer than the running mod, the library SHALL say that this version of Heavymatica is too old for the cloud and ask the player to update. It SHALL offer no sign-in, and a stored session SHALL show its last known listing as offline without further cloud requests.

#### Scenario: Outdated mod, signed in
- **WHEN** a signed-in player on mod version 0.2.0 opens the library and the status names `minClient` 0.3.0
- **THEN** the library shows the last known listing as offline with the hint to update, and fetches nothing from the cloud

#### Scenario: Outdated mod, signed out
- **WHEN** a player without a stored session on an outdated mod opens the library while the status says `open`
- **THEN** the library shows the hint to update and offers no Sign in

### Requirement: No cloud downloads without a session
Without a stored session, or with `cloud.access` on `OFF`, the client SHALL NOT download cloud schematic files or thumbnails. Placements of cloud versions SHALL still load from the local cache, and versions that are not cached SHALL stay unavailable until the player signs in.

#### Scenario: Cached placement while off
- **WHEN** `cloud.access` is `OFF` and the player joins a world with a placement of a cached cloud version
- **THEN** the placement loads from the cache and no request reaches the cloud

#### Scenario: Missing version without a session
- **WHEN** a player who signed out joins a world with a placement of a cloud version that is not cached
- **THEN** the placement stays unavailable and the client does not contact the cloud
