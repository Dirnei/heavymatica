## ADDED Requirements

### Requirement: Segment labels and centre hint
Every segment SHALL show its icon with its name under it on the ring. While a segment is hovered, the centre of the wheel SHALL show that segment's name and a one-line hint of what picking it does. While no segment is hovered, the centre SHALL name the selected placement and its state, or the question when the wheel asks to confirm. A segment that is greyed out SHALL say in its hint why it does nothing.

#### Scenario: Hover shows the hint
- **WHEN** the wheel is open on an anchored placement and the mouse points at Lock
- **THEN** the centre shows "Lock" and a hint saying the placement is fixed in place for building

#### Scenario: Nothing hovered
- **WHEN** the wheel opens on an anchored placement named "Castle Gate" with the mouse in the centre
- **THEN** the centre shows "Castle Gate" and its state, Anchored

#### Scenario: Greyed-out segment explains itself
- **WHEN** in capture mode with no corner set the mouse points at Capture
- **THEN** the centre's hint says both corners must be set first

### Requirement: Current option mark
On a page that offers settings, such as Layers, Options, Mistakes or Opacity, the option that is currently in effect or switched on SHALL be marked on its segment. The mark SHALL stay visible while the segment is hovered.

#### Scenario: Current layer setting
- **WHEN** a locked placement uses From-to and the player opens the Layers sub-wheel
- **THEN** From-to carries the mark and Off and Follow feet do not

#### Scenario: Hovering a marked segment
- **WHEN** the mouse points at the marked From-to segment
- **THEN** the segment is highlighted and still shows the mark

### Requirement: Wheel sounds
With the client setting `wheel.sounds` on (the default), the wheel SHALL play a quiet tick when the hovered segment changes to another segment, and a click when a segment runs or opens a sub-page or a confirm page. Moving into the centre, a greyed-out segment being picked, and closing without running anything SHALL NOT play the click. With `wheel.sounds` off, the wheel SHALL play no sounds. The sounds SHALL play only for the player who has the wheel open.

#### Scenario: Sweeping across segments
- **WHEN** with sounds on the mouse moves from Lock over Remove to Materials
- **THEN** one tick plays as Remove becomes hovered and one as Materials does

#### Scenario: Picking plays a click
- **WHEN** with sounds on the player picks Library
- **THEN** a click plays and the library opens

#### Scenario: Sounds off
- **WHEN** `wheel.sounds` is off and the player sweeps across segments and picks one
- **THEN** no wheel sound plays

### Requirement: Wheel animations setting
With the client setting `wheel.animations` on (the default), the wheel SHALL animate opening, hovering, page changes and closing, and every animation SHALL finish within 250 ms. With it off, the wheel SHALL show each state at once in its final layout. Animations SHALL NOT delay input: a segment SHALL be pointable and pickable from the moment the wheel opens, and a release or click SHALL act on the segment under the mouse direction, as without animations.

#### Scenario: Quick flick while opening
- **WHEN** in HOLD mode the player presses V, flicks towards Library and releases V within 100 ms
- **THEN** the library opens, even though the opening animation had not finished

#### Scenario: Animations off
- **WHEN** `wheel.animations` is off and the player opens the wheel and moves between segments
- **THEN** the wheel appears fully drawn at once and the highlight moves without transition

#### Scenario: Settings survive a restart
- **WHEN** the player sets `wheel.animations` and `wheel.sounds` to false and restarts the game
- **THEN** the wheel opens without animations and without sounds
