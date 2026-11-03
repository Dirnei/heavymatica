# Spec Delta

## MODIFIED Requirements

### Requirement: What the player has
The items a player has SHALL be the sum over the main inventory, the hotbar, the offhand, the contents of carried shulker boxes and bundles, and the contents of any other carried item that exposes its contents through the loader's item container API (NeoForge's item capability, Fabric's item storage). Items inside those containers SHALL be counted once, also when containers are nested. Containers in the world SHALL NOT count.

#### Scenario: Planks in a shulker box
- **WHEN** the player carries 10 oak planks loose and a shulker box with 64 oak planks
- **THEN** the material list has 74 oak planks

#### Scenario: Chest nearby
- **WHEN** a chest next to the player holds 64 oak planks and the player carries none
- **THEN** the material list has 0 oak planks

#### Scenario: Same count on both loaders
- **WHEN** a player carries the same items, shulker boxes and bundles on Fabric and on NeoForge
- **THEN** the material list shows the same counts on both
