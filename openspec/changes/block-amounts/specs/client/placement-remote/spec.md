# Spec Delta

## ADDED Requirements

### Requirement: Amounts in the overlay
For a LOCKED selected placement, the overlay SHALL count too many positions in orange next to the other mistake counts. When the crosshair targets a position of the placement that is too many, it SHALL show the expected and found block and how many there are instead of how many are planned. When it targets a position that is not enough yet, it SHALL show the expected and found block and how many more are needed.

#### Scenario: Too many count
- **WHEN** the selected locked placement has 2 positions with too many candles
- **THEN** the overlay shows 2 too many in orange

#### Scenario: Looking at too many candles
- **WHEN** the crosshair targets a position of the selected locked placement that expects 3 white candles and holds 4
- **THEN** the overlay shows the expected and found candles and that there are 4 instead of 3

#### Scenario: Looking at one of three candles
- **WHEN** the crosshair targets a position of the selected locked placement that expects 3 white candles and holds 1
- **THEN** the overlay shows the expected and found candles and that 2 more are needed
