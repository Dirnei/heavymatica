# Spec Delta

## MODIFIED Requirements

### Requirement: Settings screen opens from the mods list and the library
The client SHALL offer a settings screen that opens from Heavymatica's config button in the game's mods list and from the library's settings button. On NeoForge the mods list is NeoForge's mods screen. On Fabric it is Mod Menu's mods screen when Mod Menu is installed; without Mod Menu the settings screen opens from the library only. Closing it SHALL return to the screen it was opened from.

#### Scenario: From the mods list
- **WHEN** a player opens the mods list, selects Heavymatica and chooses Config
- **THEN** the Heavymatica settings screen opens, and Done returns to the mods list

#### Scenario: From Mod Menu on Fabric
- **WHEN** a Fabric player with Mod Menu installed opens Mod Menu, selects Heavymatica and chooses its config button
- **THEN** the Heavymatica settings screen opens, and Done returns to Mod Menu

#### Scenario: Fabric without Mod Menu
- **WHEN** a Fabric player without Mod Menu opens the library and chooses its settings button
- **THEN** the Heavymatica settings screen opens, and Done returns to the library
