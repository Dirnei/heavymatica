# Spec Delta

## MODIFIED Requirements

### Requirement: In-hand filter
While the selected placement is LOCKED and a remote is held, the In-hand filter wheel segment or the bound in-hand filter key SHALL toggle its filter. With it on, the placement SHALL show blocks matching the held item at normal opacity, blocks matching other hotbar items at a configurable lower opacity (default one third), and no others. An item SHALL match a block it places in any state and every planned block whose have it counts toward under `client/block-equivalents`.

#### Scenario: Holding oak planks
- **WHEN** the in-hand filter is on, the player holds oak planks, and oak stairs are in the hotbar
- **THEN** the placement's oak planks show normally, its oak stairs show dimmed, and its cobblestone does not show

#### Scenario: Holding the remote
- **WHEN** the in-hand filter is on and the player holds the remote, a stick
- **THEN** only the blocks matching the hotbar's block items show, all dimmed

#### Scenario: Toggle from the wheel
- **WHEN** the selected locked placement's in-hand filter is off and the player picks In-hand filter on the wheel
- **THEN** its in-hand filter is on

#### Scenario: Holding dirt
- **WHEN** the in-hand filter is on and the player holds dirt
- **THEN** the placement's dirt, grass block, dirt path and farmland ghosts show normally

#### Scenario: Holding a log
- **WHEN** the in-hand filter is on, the player holds an oak log and the placement has stripped oak logs and stripped spruce logs
- **THEN** the stripped oak logs show normally and the stripped spruce logs do not show

#### Scenario: Holding concrete powder
- **WHEN** the in-hand filter is on and the player holds white concrete powder
- **THEN** the placement's white concrete ghosts show normally

#### Scenario: Holding concrete
- **WHEN** the in-hand filter is on, the player holds white concrete and the placement has white concrete and white concrete powder
- **THEN** the white concrete ghosts show normally and the white concrete powder ghosts do not show

#### Scenario: Holding podzol
- **WHEN** the in-hand filter is on, the player holds podzol and the placement has dirt paths and farmland
- **THEN** the dirt path ghosts show normally and the farmland ghosts do not show
