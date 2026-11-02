## ADDED Requirements

### Requirement: Layer keys without the remote
While the selected placement is LOCKED and no screen is open, the layer keys SHALL work whether or not a remote is held: the up and down arrow keys move the from-to window, numpad + and numpad - resize the window or change the follow offset, and the cycle layer mode key, when bound, cycles the layer setting, each as with the remote held and as rebound by the player. When a layer key changes the layer setting, window or offset while no remote is held, the action bar SHALL show the new state: the layer setting, the shown layers and the placement's height. The wheel and the in-hand filter key SHALL still need the remote.

#### Scenario: Window up while holding blocks
- **WHEN** the selected placement is LOCKED in from-to with window 4 to 6, the player holds oak planks and presses the up arrow
- **THEN** the window is layers 5 to 7 and the action bar shows from-to, layers 5 to 7 and the height

#### Scenario: Follow offset while holding blocks
- **WHEN** the selected placement is LOCKED in follow feet with offset 1, the player holds a pickaxe and presses numpad +
- **THEN** its follow offset is 2

#### Scenario: Cycle while holding blocks
- **WHEN** the player has bound the cycle layer mode key, holds oak planks and presses that key on a LOCKED placement whose layer setting is off
- **THEN** its layer setting is from-to

#### Scenario: Unlocked placement
- **WHEN** the selected placement is ANCHORED, the player holds oak planks and presses the up arrow
- **THEN** the placement does not move

#### Scenario: Screen open
- **WHEN** the selected placement is LOCKED and the player presses the up arrow in a chat screen
- **THEN** its layers do not change
