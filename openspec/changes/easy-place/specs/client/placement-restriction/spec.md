# Spec Delta

## Purpose

Prevents building mistakes instead of flagging them afterwards: while it is on, a block placement that would contradict
a locked placement is refused before it reaches the world.

## ADDED Requirements

### Requirement: Placement restriction setting
Placement restriction SHALL be a client setting, off by default, saved in the client config so it survives restarts.
It SHALL NOT depend on the placement. Turning it on or off SHALL show an action-bar message saying whether placement
restriction is now on or off.

#### Scenario: Default
- **WHEN** a player starts the game with a fresh config
- **THEN** placement restriction is off

#### Scenario: Survives a restart
- **WHEN** the player turns placement restriction on and restarts the game
- **THEN** placement restriction is still on

### Requirement: Placement restriction key
Toggling placement restriction SHALL be a key in the Heavymatica category with no default key. A bound key SHALL work
whenever no screen is open, whether or not a remote is held.

#### Scenario: Toggle while holding blocks
- **WHEN** the player has bound the placement restriction key, holds cobblestone and presses the key with no screen open
- **THEN** placement restriction toggles and the action bar says its new state

### Requirement: Restricted positions
A position SHALL be restricted when it belongs to a LOCKED placement in the player's dimension and lies in the layers
that placement's layer setting shows. Positions of FOLLOWING and ANCHORED placements, positions in hidden layers and
positions outside every locked placement SHALL NOT be restricted.

#### Scenario: Outside the placement
- **WHEN** placement restriction is on and the player places cobblestone next to, but outside, a locked placement
- **THEN** the cobblestone is placed

#### Scenario: Hidden layer
- **WHEN** placement restriction is on, a locked placement shows only layers 1 to 3, and the player places scaffolding in layer 5 where the plan says air
- **THEN** the scaffolding is placed

#### Scenario: Anchored placement
- **WHEN** placement restriction is on and the player places cobblestone inside an anchored placement
- **THEN** the cobblestone is placed

### Requirement: What is refused
While placement restriction is on, a right-click with a block item SHALL be refused when the block it would place
lands on a restricted position and the world comparison would judge the resulting block there as wrong block, wrong
orientation, too many or extra. A result the comparison would judge matching, not enough yet or needs finishing SHALL
be allowed. When the position is restricted in more than one placement, the selected placement SHALL decide.

#### Scenario: Wrong block
- **WHEN** placement restriction is on and the player tries to place cobblestone where a visible layer plans oak planks
- **THEN** nothing is placed and the action bar names oak planks

#### Scenario: Wrong orientation
- **WHEN** placement restriction is on and the player tries to place oak stairs facing south where the plan has oak stairs facing north
- **THEN** nothing is placed

#### Scenario: Planned air
- **WHEN** placement restriction is on and the player tries to place a block where a visible layer of a locked placement plans air
- **THEN** nothing is placed and the action bar says the plan has air there

#### Scenario: Too many
- **WHEN** placement restriction is on, the plan has 2 candles at a position, the world has 2 there, and the player right-clicks them with a candle
- **THEN** no candle is added

#### Scenario: Matching block
- **WHEN** placement restriction is on and the player places oak planks where the plan has oak planks
- **THEN** the oak planks are placed

#### Scenario: Equivalent block
- **WHEN** placement restriction is on and the player places dirt where the plan has a grass block
- **THEN** the dirt is placed

#### Scenario: Half of a double slab
- **WHEN** placement restriction is on and the player places a bottom oak slab where the plan has a double oak slab
- **THEN** the slab is placed

#### Scenario: Needs finishing
- **WHEN** placement restriction is on and the player places a repeater with the right facing where the plan has a repeater with a delay of 3
- **THEN** the repeater is placed

### Requirement: Judging the click as the game would place it
The block a right-click would place SHALL be judged as the game would place it from that click: the clicked face, the
aim on it, the player's facing, and the block already there when the click adds to it, such as a slab or a candle.

#### Scenario: Aim decides the slab half
- **WHEN** placement restriction is on, the plan has a top oak slab, and the player clicks the upper half of the side of the block below with oak slabs
- **THEN** the top oak slab is placed

#### Scenario: Wrong half refused
- **WHEN** placement restriction is on, the plan has a top oak slab, and the player clicks the top face of the block below with oak slabs
- **THEN** nothing is placed

### Requirement: Refused right-clicks
A refused right-click SHALL send nothing to the server, place nothing and use no item. It SHALL show an action-bar
message naming the planned block, or saying that the plan has air there.

#### Scenario: Nothing reaches the server
- **WHEN** placement restriction refuses a cobblestone placement on a dedicated server
- **THEN** the server gets no placement and the player still has the same amount of cobblestone

### Requirement: Using blocks stays possible
Placement restriction SHALL NOT refuse a right-click on a block that acts when used, such as a chest, door, lever or
button, unless the player is sneaking; such a click SHALL behave as without placement restriction. Only items that place
blocks SHALL be judged; other items, such as buckets, SHALL behave as without placement restriction.

#### Scenario: Opening a chest
- **WHEN** placement restriction is on and the player holding cobblestone right-clicks a chest inside a locked placement
- **THEN** the chest opens

#### Scenario: Sneaking at a chest
- **WHEN** placement restriction is on and the player sneaks and right-clicks the top of a chest with cobblestone where the plan has air above the chest
- **THEN** nothing is placed

#### Scenario: Bucket
- **WHEN** placement restriction is on and the player empties a water bucket inside a locked placement
- **THEN** the water is placed

### Requirement: Restriction and Easy Place
An Easy Place click SHALL NOT be refused by placement restriction, since it places the planned block. A right-click that
is not an Easy Place click SHALL be judged by placement restriction as any other.

#### Scenario: Wrong item with both on
- **WHEN** Easy Place and placement restriction are on and the player right-clicks a ghost of oak stairs with cobblestone
- **THEN** nothing is placed and the action bar names oak stairs
