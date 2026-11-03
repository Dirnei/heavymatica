# client/easy-place Specification

## Purpose
Lets a player build a locked placement by right-clicking its ghosts: the planned block lands at the ghost's position
with the planned orientation, without aiming at a supporting face or turning around, on any server.

## Requirements

### Requirement: Easy Place setting
Easy Place SHALL be a client setting, off by default, saved in the client config so it survives restarts. It SHALL NOT
depend on the placement. Turning it on or off SHALL show an action-bar message saying whether Easy Place is now on or
off.

#### Scenario: Default
- **WHEN** a player starts the game with a fresh config
- **THEN** Easy Place is off

#### Scenario: Survives a restart
- **WHEN** the player turns Easy Place on and restarts the game
- **THEN** Easy Place is still on

### Requirement: Easy Place key
Toggling Easy Place SHALL be a key in the Heavymatica category with no default key. A bound key SHALL work whenever no
screen is open, whether or not a remote is held.

#### Scenario: Toggle while holding blocks
- **WHEN** the player has bound the Easy Place key, holds oak stairs and presses the key with no screen open
- **THEN** Easy Place toggles and the action bar says its new state

### Requirement: When Easy Place acts
A main-hand right-click SHALL be an Easy Place click when Easy Place is on, no screen is open, the player is neither
sneaking nor a spectator, and there is a ghost target within block-interaction reach that is missing or not enough yet,
while the main hand holds one of the target's candidate items as ghost picking takes them. The remote is never a
candidate. Every other right-click SHALL behave as without Easy Place, subject to placement restriction.

#### Scenario: Matching item on a ghost
- **WHEN** Easy Place is on, the player holds oak stairs and right-clicks a ghost of oak stairs within reach where the world has air
- **THEN** it is an Easy Place click

#### Scenario: Different item
- **WHEN** Easy Place is on, the player holds cobblestone and right-clicks a ghost of oak stairs
- **THEN** it is not an Easy Place click and the right-click behaves as in vanilla, subject to placement restriction

#### Scenario: Sneaking
- **WHEN** Easy Place is on and the player sneaks while right-clicking a ghost with the matching item
- **THEN** the right-click behaves as in vanilla

#### Scenario: Out of reach
- **WHEN** Easy Place is on and the targeted ghost is beyond block-interaction reach
- **THEN** the right-click behaves as in vanilla

#### Scenario: Matching block already there
- **WHEN** Easy Place is on and the line of sight hits a real block that matches its planned block
- **THEN** it is not an Easy Place click, since a matching position is never a ghost target

### Requirement: Easy Place places the planned block
An Easy Place click SHALL place the target's planned block, after the placement's swaps, rotation and mirror, at the
target's position, with the planned values of the properties a placement decides: facing, axis, half, slab type, hinge,
attachment face and the like. It SHALL NOT depend on where on the ghost the player aims or which way the player faces.
Properties a placement never decides SHALL be left to the game and the comparison.

#### Scenario: Properties left to the game
- **WHEN** a ghost plans a repeater with a delay of 3 and the player Easy Place clicks it with a repeater
- **THEN** a repeater with the planned facing and a delay of 1 is placed, and the comparison shows it as needing finishing

#### Scenario: Stairs facing away
- **WHEN** a ghost plans oak stairs facing north, upside down, and the player stands north of it facing south and right-clicks it with oak stairs
- **THEN** oak stairs facing north, upside down, stand at the ghost's position

#### Scenario: Log on its side
- **WHEN** a ghost plans an oak log on the x axis and the player right-clicks the ghost's top face with an oak log
- **THEN** an oak log on the x axis stands at the ghost's position

#### Scenario: Top slab
- **WHEN** a ghost plans a top oak slab and the player right-clicks the lower half of the ghost with oak slabs
- **THEN** a top oak slab stands at the ghost's position

#### Scenario: Completing a double slab
- **WHEN** a ghost plans a double oak slab, the world has a bottom oak slab there, and the player right-clicks it with oak slabs
- **THEN** a double oak slab stands there

#### Scenario: Wall torch
- **WHEN** a ghost plans a wall torch facing east with a solid block west of it and the player right-clicks it with a torch
- **THEN** a wall torch facing east stands at the ghost's position

#### Scenario: Vanilla server
- **WHEN** the player uses Easy Place on a dedicated server without Heavymatica
- **THEN** the blocks are placed with the planned orientation, as in single player

### Requirement: Easy Place puts the content into a placed base
When an Easy Place click targets a not-enough-yet two-item block whose base stands there, and the main hand holds the
content item that turns that base into the planned block, Easy Place SHALL use the held item on the base as a vanilla
right-click on it would: on a face the player can see, or the top face, with the player's own rotation.

#### Scenario: Planting into a placed pot
- **WHEN** Easy Place is on, a ghost plans a potted poppy, an empty flower pot stands there, and the player right-clicks it with a poppy
- **THEN** a potted poppy stands there

#### Scenario: Candle onto a placed cake
- **WHEN** Easy Place is on, a ghost plans a red candle cake, an uneaten cake stands there, and the player right-clicks it with a red candle
- **THEN** a red candle cake stands there

#### Scenario: Another plant
- **WHEN** Easy Place is on, a ghost plans a potted poppy, an empty flower pot stands there, and the player right-clicks it with a dandelion
- **THEN** it is not an Easy Place click

### Requirement: Easy Place follows the game's rules
Easy Place SHALL place with the held item and SHALL follow the game's rules on the server: a block that needs support
SHALL only be placed where it can survive, and the server's answer SHALL be what the world shows.

#### Scenario: Server refuses
- **WHEN** an Easy Place click places a block the server does not accept, for example in a protected area
- **THEN** the world shows what the server answered and the item is not lost

### Requirement: No placement Easy Place can't make right
When no click the client can send would give the target's planned block with its planned placement properties from the
held item, an Easy Place click SHALL place nothing, send nothing to the server, and show an action-bar message that
Easy Place can't place that block there.

#### Scenario: Torch without support
- **WHEN** a ghost plans a wall torch facing east, the position west of it is air, and the player Easy Place clicks it with a torch
- **THEN** nothing is placed and the action bar says Easy Place can't place it there

### Requirement: Easy Place keeps the player's view
An Easy Place click SHALL NOT turn the player's camera, and the player's look direction on the server SHALL be the
player's own again right after the click.

#### Scenario: Camera unchanged
- **WHEN** the player looks south and Easy Place places stairs facing north
- **THEN** the player's camera still looks south afterwards

### Requirement: Easy Place repeats like vanilla
Holding the use key SHALL repeat Easy Place clicks at vanilla's use repeat rate, each on the ghost target at that
moment.

#### Scenario: Sweep along a row
- **WHEN** Easy Place is on and the player holds the use key while sweeping the crosshair along a row of missing oak plank ghosts with oak planks in hand
- **THEN** oak planks are placed along the row at vanilla's repeat rate

### Requirement: Easy Place leaves the open state to the player
Easy Place SHALL judge the block a click would place without its open state: a door, trapdoor or fence gate that the click places closed where the plan has it open, or open where the plan has it closed, SHALL be accepted as long as it matches in all else. The comparison then shows the position as wrong orientation until the player opens or closes it.

#### Scenario: Planned open gate
- **WHEN** a ghost plans an open oak fence gate facing east and the player Easy Place clicks it with an oak fence gate
- **THEN** a closed oak fence gate facing east is placed, and the position shows as wrong orientation until the player opens it

#### Scenario: Planned open trapdoor
- **WHEN** a ghost plans an open top spruce trapdoor facing north and the player Easy Place clicks it with a spruce trapdoor
- **THEN** a closed top spruce trapdoor facing north is placed

### Requirement: Easy Place connects chests as planned
An Easy Place click on a chest ghost SHALL place a chest that the comparison accepts: a single chest where a single chest is planned, and a single chest or the planned half where a half of a double chest is planned. When no click without sneaking gives an accepted chest, for example because the game would connect a planned single chest to a neighbouring chest with the same facing, Easy Place SHALL send the click while sneaking, telling the server that the player sneaks only for that click, as a vanilla client can. A single chest where a half is planned SHALL NOT be an Easy Place target; a right-click on it SHALL behave as without Easy Place.

#### Scenario: Single chest beside a chest
- **WHEN** a ghost plans a single chest facing north next to a single chest facing north that the plan keeps single, and the player Easy Place clicks it with a chest
- **THEN** a single chest facing north is placed and the neighbouring chest stays single

#### Scenario: Completing a double chest
- **WHEN** a ghost plans the right half of a double chest facing north, the world has a single chest facing north at the planned left half, and the player Easy Place clicks it with a chest
- **THEN** the two chests connect into the planned double chest

#### Scenario: First half of a double chest
- **WHEN** a ghost plans the left half of a double chest facing north with air at the other half, and the player Easy Place clicks it with a chest
- **THEN** a single chest facing north is placed

#### Scenario: Vanilla server
- **WHEN** the player Easy Place clicks a planned single chest beside a same-facing chest on a dedicated server without Heavymatica
- **THEN** a single chest is placed, as in single player

### Requirement: Active build options above the hotbar
While Easy Place or placement restriction is on, a LOCKED placement exists in the player's dimension, no screen is open and the HUD is not hidden, the client SHALL show a small row centred above the hotbar, clear of the experience bar and the held item's name. The row holds the wheel icon of each of the two options that is on, Easy Place first, each on a small glass background. The row SHALL show whether or not the remote is held. While both options are off, or no locked placement exists in the player's dimension, nothing SHALL show.

#### Scenario: Easy Place on
- **WHEN** Easy Place is on, placement restriction is off and a locked placement is in the player's dimension
- **THEN** the Easy Place icon shows above the hotbar and no restriction icon

#### Scenario: Both on
- **WHEN** Easy Place and placement restriction are on and a locked placement is in the player's dimension
- **THEN** the Easy Place and placement restriction icons show side by side above the hotbar, Easy Place first

#### Scenario: No locked placement
- **WHEN** Easy Place is on and the only placement is ANCHORED
- **THEN** no icons show above the hotbar

#### Scenario: Turned off
- **WHEN** the player turns Easy Place off from the wheel while restriction is off
- **THEN** the icon row disappears

#### Scenario: Screen open
- **WHEN** Easy Place is on and the player opens the inventory
- **THEN** no icons show above the hotbar
