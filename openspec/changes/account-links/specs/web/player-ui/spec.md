# Spec Delta

## MODIFIED Requirements

### Requirement: Landing page with GitHub register and sign-in
The landing page SHALL offer "Register with GitHub" and "Sign in with GitHub" and no other sign-in. After a failed or cancelled sign-in it SHALL say why. When a GitHub sign-in has no account, it SHALL ask to create a new account or join an existing one with a code from the game while linking is available, and otherwise offer to register.

#### Scenario: Only GitHub
- **WHEN** a signed-out visitor opens the website
- **THEN** the landing page offers registering and signing in with GitHub, and no Microsoft, Google or Minecraft sign-in

#### Scenario: First sign-in choice
- **WHEN** a visitor signs in with a GitHub identity that has no account and linking is available
- **THEN** the landing page asks to create a new account or join an existing one, and creates nothing until the visitor chooses

#### Scenario: Join from the choice
- **WHEN** a visitor at the first sign-in choice chooses to join
- **THEN** the website shows the link code page and says to open the link code screen in the game's library

#### Scenario: No account yet
- **WHEN** a visitor signs in with a GitHub identity that has no account and linking is unavailable
- **THEN** the landing page says there is no account for it and offers "Register with GitHub"

## ADDED Requirements

### Requirement: Link code page
The website SHALL have a link code page that the game's QR code opens with the code filled in. It SHALL need a GitHub sign-in, an account or a pending one, and SHALL return to the page with the code kept. After a code is entered it SHALL say what will be linked, wait for the confirmation in the game, and then say whether it was linked, declined or expired.

#### Scenario: QR code opened signed out
- **WHEN** a signed-out visitor opens the link address from the game's QR code on a phone
- **THEN** the website asks for a GitHub sign-in and then shows the link code page with the code filled in

#### Scenario: Waiting for the game
- **WHEN** a signed-in player submits a code that a pending Minecraft sign-in issued
- **THEN** the page says the game must now confirm, and shows "Linked" once the player confirms there

#### Scenario: Declined in the game
- **WHEN** the issuer declines the redemption in the game
- **THEN** the page says the link was declined and nothing was linked

#### Scenario: Wrong code
- **WHEN** a player submits an unknown, expired or used code
- **THEN** the page says the code is not valid and lets the player try another

#### Scenario: Joined from the website
- **WHEN** a pending GitHub sign-in submits a code from the game's library and the player confirms in the game
- **THEN** the website signs the player in to the joined account and shows its library
