# Spec Delta

## ADDED Requirements

### Requirement: Unknown blocks in the list
Can't check positions SHALL NOT need an item. The material list SHALL show them in a separate "unknown blocks" part, per block ID as the schematic shows it, with how many positions in the visible layers use it. They SHALL NOT show as shortages or under "can't carry".

#### Scenario: Missing mod
- **WHEN** a locked placement shows all its layers and has 40 `create:shaft` positions while Create is not installed
- **THEN** the material list shows `create:shaft` 40 under "unknown blocks" and no shortage for it

#### Scenario: Follows the layers
- **WHEN** the same placement shows layers 0 to 2 and 10 of the shafts are in those layers
- **THEN** the material list shows `create:shaft` 10 under "unknown blocks"
