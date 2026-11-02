# Spec Delta

## Purpose

Tells clients, before anyone signs in, whether the Heavymatica Cloud currently takes sign-ins and which mod versions it
still supports, so the game can show or hide its cloud parts without a new mod release.

## ADDED Requirements

### Requirement: Public status
The service SHALL answer `GET /v1/status` without authentication, with whether sign-in is `open` or `closed` and, when configured, the lowest mod version it supports. The request SHALL NOT need or read any token, cookie or identity, and the answer SHALL be the same for every caller.

#### Scenario: Anonymous request
- **WHEN** a client without a token or cookie requests `/v1/status`
- **THEN** the service answers 200 with `signIn` set to `open` or `closed`

#### Scenario: Same answer with a session
- **WHEN** a client sends a valid bearer token with the request
- **THEN** the answer is the same as without it

### Requirement: Sign-in state is a service setting
Whether the status reports sign-in as `open` SHALL come from the service's configuration and SHALL default to `closed` when it is not set. Changing it SHALL NOT need a new mod release. The status SHALL only report the setting: it SHALL NOT change whether the sign-in endpoints accept a sign-in.

#### Scenario: Not configured
- **WHEN** the service starts without a sign-in state configured
- **THEN** `/v1/status` reports `signIn` as `closed`

#### Scenario: Closed does not refuse sign-ins
- **WHEN** the status reports `closed` and a player completes the sign-in handshake
- **THEN** the sign-in succeeds as it would while `open`

### Requirement: Minimum client version
When the service is configured with a minimum mod version, the status SHALL include it as `minClient`, a version such as `0.3.0`. Without one, the status SHALL leave `minClient` out.

#### Scenario: Minimum configured
- **WHEN** the service is configured with minimum mod version `0.3.0`
- **THEN** `/v1/status` includes `minClient` `0.3.0`

#### Scenario: No minimum
- **WHEN** no minimum mod version is configured
- **THEN** `/v1/status` has no `minClient`

### Requirement: Local runs are open
The service started for local development SHALL report sign-in as `open`, so dev clients see the cloud without extra setup.

#### Scenario: Local run
- **WHEN** the cloud is started locally through the development host
- **THEN** `http://localhost:5180/v1/status` reports `signIn` as `open`
