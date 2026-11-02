# Spec Delta

## MODIFIED Requirements

### Requirement: Accounts hold linked identities
An account SHALL own its schematics and storage usage and SHALL hold one or more linked identities: Minecraft identities (a UUID verified through Mojang, with its last known name) and web identities (a provider and the provider's stable user ID, never an email address). An account MAY hold several of each kind. An identity SHALL belong to at most one account.

#### Scenario: Alt on the same account
- **WHEN** an account has the Minecraft identities `Steve` and `SteveBuilds` and a player signs in as either
- **THEN** both sign-ins reach the same account and the same library

#### Scenario: Game and website on one account
- **WHEN** a player's Minecraft identity and GitHub identity are linked to the same account
- **THEN** the game and the website show the same library

#### Scenario: Identity already taken
- **WHEN** a link would add an identity that is already linked to another account
- **THEN** the service rejects it as already linked and neither account changes

### Requirement: Account IDs are not identities
Each account SHALL have an opaque ID that is not derived from any identity. Ownership, quota and sign-in sessions SHALL refer to the account ID, never to a Minecraft UUID or a provider user ID.

#### Scenario: New GitHub account
- **WHEN** a player registers with the GitHub user ID `583231`
- **THEN** the new account's ID is neither `583231` nor derived from it, and the account holds the identity (`github`, `583231`)

#### Scenario: Migrated Minecraft account
- **WHEN** an account keyed by a Minecraft UUID is migrated
- **THEN** its new ID is not that UUID, and its Minecraft identity holds the UUID

#### Scenario: Identity moves on
- **WHEN** a Minecraft identity is unlinked from account A and later signs in and creates account B
- **THEN** account A keeps its ID and data, and account B has a new, different ID and an empty library

### Requirement: Account audit log
The service SHALL record an audit entry for every account creation and migration, every identity added to or removed from an account, and every link code issue, redemption, failed redemption, confirmation and decline. Each entry SHALL hold the time, the account and the acting identity, and code entries also the client IP address. Code entries SHALL be kept for 90 days, the others for as long as the account exists.

#### Scenario: Registration recorded
- **WHEN** a player registers with GitHub
- **THEN** the audit log holds an account creation entry with the time, the new account and the GitHub identity

#### Scenario: Migration recorded
- **WHEN** an existing Minecraft-keyed account is migrated
- **THEN** the audit log holds a migration entry for the new account naming its Minecraft identity

#### Scenario: Link recorded
- **WHEN** an issuer confirms a link
- **THEN** the audit log holds entries for the code's issue, its redemption, the confirmation and the added identity, each with time, account and acting identity, and the code entries with the client IP address

## ADDED Requirements

### Requirement: Linking available only when configured
Link codes and pending sign-ins SHALL be available only when the service is configured with the website's link address. Without it, issuing a code SHALL fail as linking unavailable, and the account SHALL report linking as unavailable.

#### Scenario: No link address configured
- **WHEN** a signed-in player asks for a link code and no link address is configured
- **THEN** the service rejects it as linking unavailable and the account reports linking as unavailable

### Requirement: Link codes issued by the game
A signed-in account or a pending sign-in SHALL be able to issue a link code. A code SHALL be 8 characters from an alphabet without look-alike characters, shown as two groups of four, and SHALL come with the link address `<web>/link/?code=<code>`. Issuing a new code SHALL cancel the issuer's previous open code.

#### Scenario: Code issued
- **WHEN** a signed-in player asks for a link code
- **THEN** the service returns a code such as `K7QM-3XRP`, its link address and its expiry time

#### Scenario: New code replaces the old one
- **WHEN** an issuer asks for a second code while the first is still open
- **THEN** the first code can no longer be redeemed

### Requirement: Link codes are short-lived and single-use
A code SHALL be redeemable for 5 minutes after it is issued and at most once. Letter case, spaces and the dash SHALL NOT matter when it is entered. Unknown, expired, cancelled and used codes SHALL be rejected with the same answer.

#### Scenario: Expired code
- **WHEN** a code is redeemed 6 minutes after it was issued
- **THEN** the service rejects it as an invalid code and nothing is linked

#### Scenario: Used code
- **WHEN** a code that was already redeemed is redeemed again
- **THEN** the service rejects it with the same answer as an unknown code

### Requirement: Who may redeem a link code
A code issued by an account SHALL be redeemable only by a pending sign-in, whose identity then joins that account. A code issued by a pending sign-in SHALL be redeemable only by a signed-in account, which that identity then joins. Any other redeemer SHALL be rejected as not applicable, and the code SHALL stay open.

#### Scenario: GitHub identity joins a Minecraft account
- **WHEN** the game of an account shows a code and a pending GitHub sign-in redeems it on the website
- **THEN** the code waits for the issuer's confirmation, naming GitHub and the login as the redeemer

#### Scenario: Minecraft identity joins a website account
- **WHEN** a pending Minecraft sign-in shows a code and a player signed in on the website redeems it
- **THEN** the code waits for the issuer's confirmation, naming that account as the redeemer

#### Scenario: No merging
- **WHEN** a signed-in account redeems a code issued by another account
- **THEN** the service rejects it as not applicable and the code stays open

### Requirement: The issuer confirms every link
Redeeming SHALL NOT link anything by itself. The link SHALL be made only when the issuer confirms within 5 minutes of the redemption, from the same account and identity, or the same pending identity, that issued the code. Before confirming, the issuer SHALL be able to see who redeemed, and SHALL be able to decline.

#### Scenario: Confirmed
- **WHEN** the issuer confirms a redeemed code in time
- **THEN** the redeeming identity is linked to the account, or the issuing identity is linked to the redeeming account

#### Scenario: Declined
- **WHEN** the issuer declines a redeemed code
- **THEN** nothing is linked and the code can no longer be used

#### Scenario: Not confirmed in time
- **WHEN** the issuer has not confirmed 5 minutes after the redemption
- **THEN** nothing is linked and the code counts as expired

#### Scenario: Another identity tries to confirm
- **WHEN** a session of the same account signed in with a different identity than the issuer tries to confirm
- **THEN** the service rejects it and the code keeps waiting for the issuer

### Requirement: Link code rate limits
The service SHALL allow at most 10 code issues per issuer per hour. It SHALL allow at most 10 redemption attempts per redeemer and 30 per client IP address within any 10 minutes, and SHALL reject further attempts as rate limited.

#### Scenario: Guessing codes
- **WHEN** a redeemer submits an 11th redemption attempt within 10 minutes
- **THEN** the service rejects it as rate limited without checking the code

### Requirement: Unlinking identities
A signed-in account SHALL be able to unlink any of its identities except the last one. Unlinking SHALL end every session of that identity at once, in the game and on the website. The identity's next sign-in SHALL be treated as that of an unknown identity.

#### Scenario: Unlink an alt
- **WHEN** a player unlinks the Minecraft identity `SteveBuilds` from an account with two identities
- **THEN** the account keeps its data, sessions signed in as `SteveBuilds` can no longer refresh, and `SteveBuilds` signs in as an unknown identity

#### Scenario: Unlink the GitHub sign-in
- **WHEN** a player unlinks the GitHub identity of an account that also holds a Minecraft identity
- **THEN** the website sessions of that GitHub identity end at once and its next sign-in is that of an unknown identity

#### Scenario: Last identity
- **WHEN** a player tries to unlink the only identity of an account
- **THEN** the service rejects it as the last identity and nothing changes
