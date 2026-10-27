# Spec Delta

## MODIFIED Requirements

### Requirement: Account created on first sign-in
A Minecraft sign-in with a linked UUID SHALL reach that identity's account and SHALL update the identity's name. For a UUID that is not linked to any account, the service SHALL return a pending sign-in when the client can ask the player and linking is available. Otherwise it SHALL create a new account holding that UUID as its only identity, with an empty library.

#### Scenario: First sign-in, linking available
- **WHEN** a UUID that is not linked to any account signs in through the sign-in call and linking is available
- **THEN** the service returns a pending sign-in and creates no account

#### Scenario: First sign-in
- **WHEN** a UUID that is not linked to any account signs in and linking is unavailable
- **THEN** a new account exists holding that UUID as its only identity, with an empty library, and the service returns tokens for it

#### Scenario: Player renamed
- **WHEN** a player signs in after changing their Minecraft name
- **THEN** the account linked to their UUID is used and its Minecraft identity shows the new name

### Requirement: Short-lived access tokens
The service SHALL require a valid access token or website session on every call except challenge, sign-in, refresh, starting a web sign-in and the calls open to a pending sign-in. Access tokens SHALL expire 15 minutes after issue, SHALL name the account ID as their subject and SHALL name the identity that signed in.

#### Scenario: Missing or invalid token
- **WHEN** a call arrives without an access token or website session, or with an expired or tampered token
- **THEN** the service rejects it as unauthenticated

#### Scenario: Token names account and identity
- **WHEN** a player signs in with their alt `SteveBuilds` on an account that also holds `Steve`
- **THEN** the access token's subject is the account ID and it names the `SteveBuilds` identity

#### Scenario: Token from before the migration
- **WHEN** a call carries an access token issued before accounts were migrated
- **THEN** the service rejects it as unauthenticated, so the client refreshes

### Requirement: GitHub sign-in on the website
The service SHALL sign players in on the website through GitHub's OAuth2 flow, run by the service itself. The identity SHALL be the GitHub numeric user ID; the service SHALL NOT request or store the email. "Register" SHALL create an account holding that identity, or reach its linked account. "Sign in" SHALL reach the linked account; for an unknown identity it SHALL give a pending sign-in when linking is available, and create nothing.

#### Scenario: Register
- **WHEN** a player with GitHub user ID `583231` that is not linked to any account chooses "Register" and approves on GitHub
- **THEN** a new account holding (`github`, `583231`) exists with an empty library, and the player is signed in on the website

#### Scenario: Sign in
- **WHEN** a player whose GitHub identity is linked to an account chooses "Sign in" and approves on GitHub
- **THEN** the player is signed in on the website to that account

#### Scenario: Sign in without an account, linking available
- **WHEN** a player whose GitHub identity is not linked to any account chooses "Sign in" and linking is available
- **THEN** no account is created and the website holds a pending sign-in for that GitHub identity

#### Scenario: Sign in without an account
- **WHEN** a player whose GitHub identity is not linked to any account chooses "Sign in" and linking is unavailable
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

### Requirement: Older clients keep working
Mods released before this version SHALL keep signing in, refreshing and using the library without an update. A stored session from before the migration SHALL refresh into tokens for the migrated account. The existing verify call SHALL sign in a linked identity to its account and SHALL create an account for an unknown identity, whether or not linking is available.

#### Scenario: Older mod after the migration
- **WHEN** an older mod that was signed in before the migration calls the library with its old access token
- **THEN** the call is rejected as unauthenticated once, the mod's refresh succeeds, and the retried call returns the player's schematics

#### Scenario: Older mod, new player
- **WHEN** an older mod signs in through the verify call with a UUID that is not linked to any account, while linking is available
- **THEN** a new account holding that UUID exists and the service returns tokens for it

## ADDED Requirements

### Requirement: Pending sign-in
A pending sign-in SHALL prove a verified identity that has no account, in the game or on the website, and SHALL expire 10 minutes after issue. It SHALL allow only creating an account, completing the sign-in once the identity has joined an account, and issuing, viewing, confirming, cancelling or redeeming link codes. It SHALL give no access to any account's data.

#### Scenario: Create an account
- **WHEN** a pending sign-in asks to create an account
- **THEN** a new account holding that identity exists and the service returns tokens or a website session for it

#### Scenario: Complete after joining
- **WHEN** a pending sign-in's identity was linked to an account through a confirmed link code and the pending sign-in asks to complete
- **THEN** the service returns tokens or a website session for that account

#### Scenario: No library access
- **WHEN** a pending sign-in is used to list schematics
- **THEN** the service rejects it as unauthenticated
