# Spec Delta

## Purpose
The admin website on the admin host: signing in with passkeys, registering admins, looking up accounts, changing
quotas, managing admins and reading the audit log.

## ADDED Requirements

### Requirement: Admin site on the admin host
The admin site SHALL be served by the service on the admin host and SHALL talk only to the admin API on the same origin. It SHALL share no session, page or script with the player website.

#### Scenario: Opening the admin host
- **WHEN** a person opens the admin host without an admin session
- **THEN** the admin site shows the passkey sign-in and a way to enter a setup code

### Requirement: Passkey sign-in and registration in the browser
The admin site SHALL sign in with the browser's passkey prompt, register passkeys from a setup code or an invitation link, and register additional passkeys for the signed-in admin. While the admin has fewer than two passkeys, the site SHALL show only the prompt to register another one and sign-out.

#### Scenario: Register from a setup code
- **WHEN** a person enters a valid setup code and a display name and completes the browser's passkey prompt
- **THEN** the site signs in the new admin and asks for a second passkey

#### Scenario: Passkey prompt cancelled
- **WHEN** a person cancels the browser's passkey prompt
- **THEN** the site says the sign-in was cancelled and nothing is registered

### Requirement: Account lookup and quota in the browser
The admin site SHALL offer a search for accounts and an account page with the overview and a form to change the quota with a reason. Quotas SHALL be entered and shown in MiB or GiB.

#### Scenario: Change a quota
- **WHEN** an admin opens an account, enters 1 GiB and a reason, and saves
- **THEN** the account page shows the new quota and the change appears in the audit log page

### Requirement: Admin management and audit in the browser
The admin site SHALL list admins with their passkeys, create and revoke invitations and recovery invitations with a link to copy, remove admins and passkeys within the service's rules, and show the admin audit log newest first with paging.

#### Scenario: Invite an admin
- **WHEN** an admin creates an invitation
- **THEN** the site shows the invitation link once, to copy, with its expiry
