## ADDED Requirements

### Requirement: Mistakes segment
The Options sub-wheel SHALL show a Mistakes segment after its other segments, marked while the mistake pointer is not
off. Picking it SHALL swap the wheel's segments to the Mistakes sub-wheel. In HOLD mode, releasing the wheel key on
Mistakes SHALL leave the sub-wheel open, and a click SHALL pick.

#### Scenario: Options shows Mistakes
- **WHEN** the selected placement is LOCKED and the player picks Options
- **THEN** the sub-wheel shows Mistakes as its last segment

#### Scenario: Marked while on
- **WHEN** the mistake pointer is set to mistakes and the player picks Options
- **THEN** Mistakes is marked

### Requirement: Mistakes sub-wheel
The Mistakes sub-wheel SHALL show Off, Mistakes, and Mistakes and missing, with the current mistake pointer setting
marked. Picking one SHALL set the mistake pointer to it and close the wheel. Esc or the centre SHALL close the wheel and
change nothing.

#### Scenario: Turn on with missing blocks
- **WHEN** the mistake pointer is off and the player picks Options, then Mistakes, then Mistakes and missing
- **THEN** the mistake pointer is set to mistakes and missing and the wheel is closed

#### Scenario: Turn off
- **WHEN** the mistake pointer is set to mistakes and the player picks Options, then Mistakes, then Off
- **THEN** the mistake pointer is off

#### Scenario: Close without a change
- **WHEN** the player opens the Mistakes sub-wheel and presses Esc
- **THEN** the wheel closes and the mistake pointer setting is unchanged
