# client/cloud-account Specification

## Purpose

Connects the game to the player's Heavymatica Cloud account using the Minecraft account they are already playing with, and keeps that connection alive without getting in the way of play.

## Requirements

### Requirement: Sign in with the current Minecraft account
The client SHALL let the player sign in to the Heavymatica Cloud from the game in one action, using the Minecraft account the game is running with. The player SHALL NOT have to enter a name, email or password.

#### Scenario: Successful sign-in
- **WHEN** a player using a Microsoft-authenticated Minecraft account chooses "Sign in"
- **THEN** the client completes the sign-in handshake and shows the player as signed in with their Minecraft name

### Requirement: Minecraft credentials never leave for Heavymatica
The client SHALL send the player's Minecraft access token only to Mojang's session service and never to the Heavymatica Cloud.

#### Scenario: Requests to the cloud
- **WHEN** any request the client sends to the Heavymatica Cloud is inspected
- **THEN** none of them contains the Minecraft access token

### Requirement: First sign-in is explicit
The client SHALL contact the Heavymatica Cloud only after the player has signed in at least once on that Minecraft account. Before that, the mod SHALL make no network calls to the cloud.

#### Scenario: Fresh install
- **WHEN** the mod is installed and the player has never signed in
- **THEN** the client makes no request to the Heavymatica Cloud until the player chooses "Sign in"

### Requirement: Session survives restarts
The client SHALL store the cloud session per Minecraft UUID on the local disk and SHALL resume it on the next game start without asking the player again, refreshing tokens as needed.

#### Scenario: Restart
- **WHEN** a signed-in player restarts the game with the same Minecraft account
- **THEN** they are signed in to the cloud without any action

#### Scenario: Different Minecraft account
- **WHEN** the game is started with a different Minecraft account than the one that signed in
- **THEN** the client does not use the other account's cloud session and shows the player as signed out

### Requirement: Expired session handled quietly
When the stored session can no longer be refreshed, the client SHALL show the player as signed out with a hint to sign in again, and SHALL NOT interrupt gameplay with a blocking screen.

#### Scenario: Refresh rejected
- **WHEN** the service rejects the client's refresh token
- **THEN** the library shows a "Signed out, sign in again" state and gameplay continues normally

### Requirement: Sign-in failures explained
The client SHALL tell the player why a sign-in failed, distinguishing at least: an account that cannot be verified (such as an offline-mode game), Mojang being unavailable, the Heavymatica Cloud being unreachable, and an unexpected error.

#### Scenario: Offline-mode game
- **WHEN** a player whose game is not authenticated with Microsoft tries to sign in
- **THEN** the client explains that cloud sign-in needs a Microsoft-authenticated Minecraft account

#### Scenario: Cloud unreachable
- **WHEN** the Heavymatica Cloud cannot be reached during sign-in
- **THEN** the client says the cloud is unreachable and that local schematics remain usable

### Requirement: Sign out
The client SHALL let a signed-in player sign out, which SHALL revoke the session at the service when reachable and SHALL delete the locally stored session and the stored listing of their cloud schematics in every case.

#### Scenario: Sign out while offline
- **WHEN** a player signs out while the cloud is unreachable
- **THEN** the local session and stored listing are deleted, and the player is shown as signed out with no cloud entries in the library

### Requirement: Account status visible
The client SHALL show, in the library, whether the player is signed in, and when signed in, their name and storage usage against quota.

#### Scenario: Usage shown
- **WHEN** a signed-in player opens the library
- **THEN** it shows their Minecraft name and storage used out of their quota
