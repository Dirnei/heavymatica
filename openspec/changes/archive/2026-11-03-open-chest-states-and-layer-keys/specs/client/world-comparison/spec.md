## MODIFIED Requirements

### Requirement: Orientation properties
A block's orientation SHALL be its values for these block-state properties: facing (all variants), axis, rotation, half, door hinge and attach face, for a single slab whether it is the top or the bottom slab, for a chest (any chest with a chest type) whether it is single or the left or right half of a double chest, and for a door, trapdoor or fence gate whether it is open. Amount and waterlogging SHALL be compared on their own. Differences in any other property, such as connections, power, lit, age, stair shape or a barrel's open state, SHALL NOT make a position wrong.

#### Scenario: Stairs facing the wrong way
- **WHEN** the schematic has oak stairs facing north and the world has oak stairs facing east
- **THEN** that position is wrong orientation

#### Scenario: Fence connections differ
- **WHEN** the schematic's oak fence connects east and west and the world's oak fence at that position connects only east
- **THEN** that position is matching

#### Scenario: Upside-down slab
- **WHEN** the schematic has a bottom stone slab and the world has a top stone slab
- **THEN** that position is wrong orientation

#### Scenario: Gate opened after placing
- **WHEN** the schematic has a closed oak fence gate and the player opens the matching oak fence gate in the world
- **THEN** that position is wrong orientation and an orange box shows around the gate

#### Scenario: Trapdoor left closed
- **WHEN** the schematic has an open spruce trapdoor and the world has the same trapdoor closed
- **THEN** that position is wrong orientation

#### Scenario: Open barrel
- **WHEN** the schematic has a closed barrel and the world has the same barrel open because a player looks into it
- **THEN** that position is matching

#### Scenario: Two single chests merged
- **WHEN** the schematic has two single chests side by side facing north and the world has a double chest there
- **THEN** both positions are wrong orientation

#### Scenario: Halves swapped
- **WHEN** the schematic has the left half of a double chest at a position and the world has the right half there
- **THEN** that position is wrong orientation

## ADDED Requirements

### Requirement: Chest halves
A position whose planned block is one half of a double chest SHALL be not enough yet when the world holds a single chest of the same block there, in the planned facing. It SHALL become matching once the other half connects to it. Not enough yet SHALL NOT count as done for progress, and the single chest SHALL NOT be counted as a material still needed.

#### Scenario: First half placed
- **WHEN** a locked placement expects a double chest facing north and the world has a single chest facing north at its left position and air at its right position
- **THEN** the left position is not enough yet and the right position is missing

#### Scenario: Second half placed
- **WHEN** the player then places a chest facing north at the right position and the game connects the two
- **THEN** both positions are matching

#### Scenario: Wrong facing
- **WHEN** a locked placement expects the left half of a double chest facing north and the world has a single chest facing east there
- **THEN** that position is wrong orientation
