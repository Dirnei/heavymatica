# Spec Delta

## Purpose
How administrators reach the cloud: a separate admin host, passkey-only admins, how the first and further admins get
access, the admin session and the audit trail of everything admins do.

## ADDED Requirements

### Requirement: Separate admin host
The service SHALL serve the admin site and the admin API only on its admin listener and only for the configured admin host name. Requests to admin paths on the public listener SHALL be answered as not found. The admin listener SHALL NOT serve the player website or the player API. Without an admin host configured, the admin listener SHALL stay closed.

#### Scenario: Admin path on the public host
- **WHEN** a request for an admin API path arrives on the public listener
- **THEN** the service answers not found

#### Scenario: Player API on the admin host
- **WHEN** a request for the player library arrives on the admin listener
- **THEN** the service answers not found

#### Scenario: Wrong host name
- **WHEN** a request reaches the admin listener with a host name other than the configured admin host
- **THEN** the service answers not found

### Requirement: Admins sign in with passkeys only
Admins SHALL be principals of their own, with no player account and no linked identity. An admin SHALL sign in only with a registered passkey for the admin host; admins SHALL have no password. A player session SHALL NOT grant any admin access, and an admin session SHALL NOT grant access to the player API.

#### Scenario: Passkey sign-in
- **WHEN** an admin signs in with one of their registered passkeys
- **THEN** the admin site opens with an admin session

#### Scenario: Player session on the admin API
- **WHEN** a call to the admin API carries a player's website session or access token
- **THEN** the service rejects it as unauthenticated

### Requirement: First admin through a setup code
A command run on the server SHALL print a setup code that is valid for 15 minutes and works once. Entering it on the admin site with a display name SHALL register a passkey and create a new admin. A new setup code SHALL invalidate the previous unused one.

#### Scenario: First admin
- **WHEN** an operator runs the setup command and enters the printed code on the admin site within 15 minutes
- **THEN** a new admin exists with the passkey registered during that step

#### Scenario: Expired or used setup code
- **WHEN** a setup code is entered after 15 minutes or a second time
- **THEN** the service rejects it and no admin is created

### Requirement: Further admins by invitation
An admin SHALL be able to create an invitation link that is valid for 24 hours and works once. Opening it SHALL let a new admin register with a display name and a passkey. An invitation SHALL be revocable until it is used.

#### Scenario: Invitation used
- **WHEN** a person opens a fresh invitation link and registers a passkey
- **THEN** a new admin exists and the invitation can no longer be used

#### Scenario: Revoked invitation
- **WHEN** an invitation is opened after the inviting admin revoked it
- **THEN** the service rejects it and no admin is created

### Requirement: Two passkeys per admin
An admin with fewer than two passkeys SHALL be able to do nothing but register another passkey and sign out. An admin SHALL NOT be able to remove a passkey when that would leave fewer than two.

#### Scenario: Only one passkey
- **WHEN** an admin with one passkey tries to look up an account
- **THEN** the service rejects it and asks for a second passkey

#### Scenario: Removing a needed passkey
- **WHEN** an admin with two passkeys tries to remove one
- **THEN** the service rejects it and both passkeys stay

### Requirement: Restoring access
An admin SHALL be able to create a recovery invitation for another admin, valid for 24 hours and once, that adds a passkey to that admin. A setup code from the server command SHALL also be usable to add a passkey to an existing admin.

#### Scenario: Lost passkeys
- **WHEN** an admin who lost their passkeys opens a recovery invitation from another admin and registers a new passkey
- **THEN** the passkey is added to the existing admin, who can sign in with it

### Requirement: Admin session
An admin session SHALL be a cookie bound to the admin host. It SHALL end after 30 minutes without requests and 8 hours after sign-in at the latest. Requests that change data SHALL need the same origin and the request header the website uses.

#### Scenario: Idle admin
- **WHEN** an admin makes no request for 31 minutes
- **THEN** their next request is rejected as unauthenticated

#### Scenario: Request from another site
- **WHEN** a request that changes data arrives with the admin cookie but from another origin or without the request header
- **THEN** the service rejects it as forbidden

### Requirement: Admin rate limits
The service SHALL limit passkey sign-in attempts to 10 per client IP address per minute, setup code and invitation attempts to 5 per client IP address per 10 minutes, and SHALL reject further attempts as rate limited.

#### Scenario: Guessing setup codes
- **WHEN** a client enters a sixth setup code within 10 minutes
- **THEN** the service rejects it as rate limited without checking the code

### Requirement: Admin audit log
The service SHALL record an audit entry for every admin sign-in, every failed setup code or invitation attempt, every admin created or removed, every passkey added or removed, every invitation created or revoked and every change an admin makes to an account. Each entry SHALL hold the time, the acting admin when known, the client IP address and what changed. Admins SHALL be able to read the log, newest first. Entries SHALL be kept for at least one year.

#### Scenario: Passkey recorded
- **WHEN** an admin registers a new passkey
- **THEN** the admin audit log holds an entry with the time, the admin, the client IP address and the new passkey

### Requirement: Managing admins
An admin SHALL be able to list all admins with their passkeys, remove another admin, and remove a passkey of any admin within the two-passkey rule. An admin SHALL NOT be able to remove themselves or the last admin. Removing an admin SHALL end their sessions at once.

#### Scenario: Removing an admin
- **WHEN** an admin removes another admin
- **THEN** the removed admin's sessions end and their passkeys no longer sign in
