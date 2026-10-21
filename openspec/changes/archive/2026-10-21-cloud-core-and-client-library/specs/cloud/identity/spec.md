# Spec Delta

## Purpose

Lets players prove their Minecraft identity to the Heavymatica Cloud without creating a separate account, and gives them service tokens to use for every other cloud call.

## ADDED Requirements

### Requirement: Sign-in challenge
The service SHALL issue a sign-in challenge on request. A challenge SHALL contain a random, unguessable server ID and SHALL expire 60 seconds after it is issued.

#### Scenario: Challenge issued
- **WHEN** a client requests a sign-in challenge
- **THEN** the service returns a challenge ID and a server ID that no earlier challenge used

#### Scenario: Challenge expires
- **WHEN** a client completes sign-in with a challenge issued more than 60 seconds earlier
- **THEN** the service rejects the sign-in as expired and issues no tokens

### Requirement: Identity verified through the Mojang session server
The service SHALL complete a sign-in only after Mojang's session server confirms that the named player joined the challenge's server ID. The account SHALL be keyed by the Minecraft UUID that Mojang returns, never by a name or UUID the client claims.

#### Scenario: Verified sign-in
- **WHEN** a client completes sign-in with a valid challenge and Mojang confirms the player joined that server ID
- **THEN** the service returns an access token and a refresh token for the UUID Mojang reported

#### Scenario: Mojang does not confirm
- **WHEN** a client completes sign-in and Mojang reports that the player did not join the server ID
- **THEN** the service rejects the sign-in and issues no tokens

#### Scenario: Mojang unavailable
- **WHEN** Mojang's session server cannot be reached during sign-in
- **THEN** the service rejects the sign-in with an error that says the identity provider is unavailable, distinct from a rejected identity

### Requirement: Challenges are single-use
The service SHALL accept each challenge for at most one sign-in attempt, whether or not that attempt succeeds.

#### Scenario: Replayed challenge
- **WHEN** a client completes sign-in with a challenge that was already used
- **THEN** the service rejects the sign-in and issues no tokens

### Requirement: Account created on first sign-in
The service SHALL create an account on the first verified sign-in for a UUID and SHALL record the player's current name, updating it on every later sign-in.

#### Scenario: First sign-in
- **WHEN** a UUID signs in for the first time
- **THEN** a new account exists for that UUID with an empty library

#### Scenario: Player renamed
- **WHEN** a player signs in after changing their Minecraft name
- **THEN** the existing account for their UUID is used and shows the new name

### Requirement: Short-lived access tokens
The service SHALL require a valid access token on every call except challenge, sign-in and refresh. Access tokens SHALL expire 15 minutes after issue.

#### Scenario: Missing or invalid token
- **WHEN** a call arrives without an access token, or with an expired or tampered one
- **THEN** the service rejects it as unauthenticated

### Requirement: Refresh token rotation
The service SHALL exchange a valid refresh token for a new access token and a new refresh token, invalidating the old refresh token. Refresh tokens SHALL expire after 30 days without use.

#### Scenario: Refresh succeeds
- **WHEN** a client presents a valid, unused refresh token
- **THEN** the service returns a new access token and a new refresh token, and the old refresh token stops working

#### Scenario: Reused refresh token
- **WHEN** a client presents a refresh token that was already exchanged
- **THEN** the service rejects it and revokes every refresh token of that sign-in session

### Requirement: Sign-out
The service SHALL revoke a sign-in session's refresh token when the client signs out.

#### Scenario: Signed out
- **WHEN** a client signs out and later presents the session's refresh token
- **THEN** the service rejects the refresh
