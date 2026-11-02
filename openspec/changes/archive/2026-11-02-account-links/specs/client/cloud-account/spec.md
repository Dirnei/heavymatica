# Spec Delta

## MODIFIED Requirements

### Requirement: Sign in with the current Minecraft account
The client SHALL let the player sign in to the Heavymatica Cloud from the game in one action, using the Minecraft account the game is running with. The player SHALL NOT have to enter a name, email or password. When that Minecraft account is already linked to a cloud account, sign-in SHALL complete without further questions.

#### Scenario: Successful sign-in
- **WHEN** a player using a Microsoft-authenticated Minecraft account chooses "Sign in"
- **THEN** the client completes the sign-in handshake and shows the player as signed in with their Minecraft name

#### Scenario: Known account, no questions
- **WHEN** a player whose Minecraft account is linked to a cloud account chooses "Sign in"
- **THEN** they are signed in to that account without being asked to create or join one

## ADDED Requirements

### Requirement: First sign-in choice
When the cloud reports that the Minecraft account has no cloud account, the client SHALL ask the player to create a new account or join an existing one, offering join only when the cloud says joining is available. It SHALL create nothing until the player chooses. Without a choice from the cloud, the account is created at once.

#### Scenario: Create
- **WHEN** a player signs in for the first time, is asked, and chooses "Create a new account"
- **THEN** a new cloud account is created and the player is signed in with an empty cloud library

#### Scenario: Cancel
- **WHEN** a player is asked and closes the question without choosing
- **THEN** no account is created and the player stays signed out

#### Scenario: Joining unavailable
- **WHEN** a player signs in for the first time and the cloud does not offer joining
- **THEN** the player is signed in to a new account without being asked

### Requirement: Joining an existing account from the game
Choosing to join SHALL show a link code for the current Minecraft account, to be entered on the website while signed in to the account to join. Once it is redeemed and the player confirms, the client SHALL sign the player in to that account.

#### Scenario: Join confirmed
- **WHEN** a player chooses "Join an existing account", enters the shown code on the website while signed in, and confirms in the game
- **THEN** the player is signed in to that account and the library shows its schematics

### Requirement: Link code from the library
A signed-in player SHALL be able to open, from the library, a link code for their account, so that another sign-in can join it on the website. The screen SHALL show the code, a QR code of the link address and the time left, and SHALL offer a new code once it has expired. It SHALL NOT be offered when the cloud reports linking as unavailable.

#### Scenario: Code with QR code
- **WHEN** a signed-in player opens the link code screen from the library
- **THEN** it shows a code such as `K7QM-3XRP`, a QR code that a phone camera opens as the link address, and a countdown from 5 minutes

#### Scenario: Code expired
- **WHEN** the countdown reaches zero before the code is redeemed
- **THEN** the screen says the code expired and offers a new code

#### Scenario: Linking unavailable
- **WHEN** the cloud reports linking as unavailable
- **THEN** the library offers no link code

### Requirement: Link codes are never entered in the game
The client SHALL show link codes but SHALL NEVER offer a way to enter one.

#### Scenario: No code field
- **WHEN** a player looks through the library, account, choice and link code screens
- **THEN** none of them has a field for entering a link code

### Requirement: Linking needs confirmation in the game
When a code shown by the client is redeemed, the client SHALL say what wants to link: a sign-in by its provider and name, or an account by its Minecraft names and web sign-ins. It SHALL link only when the player confirms. Declining, closing the screen or waiting until the time runs out SHALL link nothing.

#### Scenario: Confirm a GitHub sign-in
- **WHEN** a code shown in the library is redeemed on the website by a GitHub sign-in that has no account
- **THEN** the screen asks whether to link that GitHub sign-in, naming its login, to this account, and after "Confirm" the account lists it

#### Scenario: Decline
- **WHEN** a player chooses "Decline" after a redemption they did not expect
- **THEN** nothing is linked and the screen says the code was declined

### Requirement: Linked sign-ins shown and unlinked
The client SHALL show, on an account screen opened from the library, every sign-in linked to the account, marking the one in use. It SHALL let the player unlink any of them except the last, after a confirmation. Unlinking the sign-in in use SHALL sign the player out.

#### Scenario: Unlink an alt
- **WHEN** a player signed in as `Steve` unlinks `SteveBuilds` and confirms
- **THEN** the account screen no longer lists `SteveBuilds` and `Steve` stays signed in

#### Scenario: Only one sign-in
- **WHEN** the account has a single linked sign-in
- **THEN** the account screen offers no unlink for it
