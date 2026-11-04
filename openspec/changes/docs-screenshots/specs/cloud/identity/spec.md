# Spec Delta

## ADDED Requirements

### Requirement: Development website sign-in
In the Development environment only, the service SHALL offer a website sign-in that takes a Minecraft profile ID and
starts a website session for the account that identity belongs to, without GitHub. It SHALL refuse an identity that
belongs to no account. Outside Development the sign-in SHALL NOT exist, and it SHALL never be reachable on the admin
listener.

#### Scenario: Known identity
- **WHEN** a browser opens the development sign-in with the profile ID of a Minecraft identity that has an account
- **THEN** the browser is signed in to the website as that account

#### Scenario: Unknown identity
- **WHEN** the profile ID belongs to no account
- **THEN** no session starts and the answer says so

#### Scenario: Production
- **WHEN** the service runs in any environment other than Development
- **THEN** the development sign-in answers 404
