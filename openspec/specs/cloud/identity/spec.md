# cloud/identity Specification

## Purpose

Lets players prove their Minecraft identity to the Heavymatica Cloud without creating a separate account, and gives them service tokens to use for every other cloud call.

## Requirements

### Requirement: Sign-in challenge
The service SHALL issue a sign-in challenge on request. A challenge SHALL contain a random, unguessable server ID and SHALL expire 60 seconds after it is issued.

#### Scenario: Challenge issued
- **WHEN** a client requests a sign-in challenge
- **THEN** the service returns a challenge ID and a server ID that no earlier challenge used

#### Scenario: Challenge expires
- **WHEN** a client completes sign-in with a challenge issued more than 60 seconds earlier
- **THEN** the service rejects the sign-in as expired and issues no tokens

### Requirement: Identity verified through the Mojang session server
The service SHALL complete a Minecraft sign-in only after Mojang's session server confirms that the named player joined the challenge's server ID. The Minecraft identity SHALL be the UUID that Mojang returns, never a name or UUID the client claims.

#### Scenario: Verified sign-in
- **WHEN** a client completes sign-in with a valid challenge and Mojang confirms the player joined that server ID
- **THEN** the service signs in the Minecraft identity with the UUID Mojang reported

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
A Minecraft sign-in with a linked UUID SHALL reach that identity's account and SHALL update the identity's name. A Minecraft sign-in with a UUID that is not linked to any account SHALL create a new account holding that UUID as its only identity, with an empty library.

#### Scenario: First sign-in
- **WHEN** a UUID that is not linked to any account signs in
- **THEN** a new account exists holding that UUID as its only identity, with an empty library, and the service returns tokens for it

#### Scenario: Player renamed
- **WHEN** a player signs in after changing their Minecraft name
- **THEN** the account linked to their UUID is used and its Minecraft identity shows the new name

### Requirement: Short-lived access tokens
The service SHALL require a valid access token or website session on every call except challenge, sign-in, refresh and starting a web sign-in. Access tokens SHALL expire 15 minutes after issue, SHALL name the account ID as their subject and SHALL name the identity that signed in.

#### Scenario: Missing or invalid token
- **WHEN** a call arrives without an access token or website session, or with an expired or tampered token
- **THEN** the service rejects it as unauthenticated

#### Scenario: Token names account and identity
- **WHEN** a player signs in from the game as `Steve`
- **THEN** the access token's subject is the ID of the account linked to Steve's UUID, and it names the `Steve` identity

#### Scenario: Token from before the migration
- **WHEN** a call carries an access token issued before accounts were migrated
- **THEN** the service rejects it as unauthenticated, so the client refreshes

### Requirement: Refresh token rotation
The service SHALL exchange a valid refresh token for a new access token and a new refresh token, invalidating the old refresh token. Refresh tokens SHALL expire after 30 days without use.

#### Scenario: Refresh succeeds
- **WHEN** a client presents a valid, unused refresh token
- **THEN** the service returns a new access token and a new refresh token, and the old refresh token stops working

#### Scenario: Reused refresh token
- **WHEN** a client presents a refresh token that was already exchanged
- **THEN** the service rejects it and revokes every refresh token of that sign-in session

### Requirement: Sign-out
The service SHALL revoke a sign-in session's refresh token when the client signs out, and SHALL end a website session when the player signs out on the website.

#### Scenario: Signed out
- **WHEN** a client signs out and later presents the session's refresh token
- **THEN** the service rejects the refresh

#### Scenario: Signed out on the website
- **WHEN** a player signs out on the website and a request later carries the old session cookie
- **THEN** the service rejects it as unauthenticated

### Requirement: GitHub sign-in on the website
The service SHALL sign players in on the website through GitHub's OAuth2 flow, run by the service itself. The identity SHALL be the GitHub numeric user ID; the service SHALL NOT request or store the email. "Register" SHALL create an account holding that identity, or reach the account it is already linked to. "Sign in" SHALL reach the linked account and SHALL create nothing for an unknown identity.

#### Scenario: Register
- **WHEN** a player with GitHub user ID `583231` that is not linked to any account chooses "Register" and approves on GitHub
- **THEN** a new account holding (`github`, `583231`) exists with an empty library, and the player is signed in on the website

#### Scenario: Sign in
- **WHEN** a player whose GitHub identity is linked to an account chooses "Sign in" and approves on GitHub
- **THEN** the player is signed in on the website to that account

#### Scenario: Sign in without an account
- **WHEN** a player whose GitHub identity is not linked to any account chooses "Sign in"
- **THEN** no account is created and the website says there is no account yet and offers to register

#### Scenario: Register twice
- **WHEN** a player whose GitHub identity is already linked to an account chooses "Register"
- **THEN** no second account is created and the player is signed in to the existing one

#### Scenario: Denied on GitHub
- **WHEN** a player cancels on GitHub's consent page
- **THEN** no account is created and the website says the sign-in was cancelled

#### Scenario: GitHub not configured
- **WHEN** the service runs without GitHub credentials and a player starts a web sign-in
- **THEN** the service rejects it as web sign-in unavailable, and the game's sign-in and API keep working

### Requirement: Website session
A web sign-in SHALL produce a session held in an HttpOnly cookie for the service's own origin. No access or refresh token SHALL reach the browser. The session SHALL end 30 days after its last use, on sign-out, or when its identity no longer belongs to the account.

#### Scenario: No tokens in the browser
- **WHEN** a player signs in on the website
- **THEN** the browser holds only an HttpOnly session cookie, and no response to the website carries an access or refresh token

#### Scenario: Session unused for 30 days
- **WHEN** a website session has not been used for 30 days and a request carries its cookie
- **THEN** the service rejects it as unauthenticated

### Requirement: Website requests protected from other sites
A request that is authenticated by a website session and changes data SHALL be accepted only when it comes from the service's own origin and carries the website's request marker. Requests authenticated by an access token SHALL NOT need either.

#### Scenario: Forged delete
- **WHEN** a page on another site makes a signed-in player's browser send a request to delete a schematic
- **THEN** the service rejects it as forbidden and the schematic is unchanged

#### Scenario: Game client unaffected
- **WHEN** the mod deletes a schematic with its access token
- **THEN** the service deletes it without any origin or marker check

### Requirement: Older clients keep working
Mods released before this version SHALL keep signing in, refreshing and using the library without an update. A stored session from before the migration SHALL refresh into tokens for the migrated account.

#### Scenario: Older mod after the migration
- **WHEN** an older mod that was signed in before the migration calls the library with its old access token
- **THEN** the call is rejected as unauthenticated once, the mod's refresh succeeds, and the retried call returns the player's schematics
