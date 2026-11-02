## Purpose

Rules every Heavymatica screen and HUD panel follows so that it stays readable and usable at any window size and GUI
scale, and so that dialogs keep the player in context.

## ADDED Requirements

### Requirement: Screens fit every GUI scale
Every Heavymatica screen SHALL fit inside the game window at every GUI scale the game offers for a window of at least 854 × 480 pixels: no text or control SHALL overlap another or reach past the screen's edge. A list longer than its area SHALL scroll.

#### Scenario: Small window, large scale
- **WHEN** the game window is 854 × 480 at the largest GUI scale it offers and the player opens the library with an entry selected
- **THEN** the header, the grid, the details panel and all its actions are fully on screen and nothing overlaps

#### Scenario: Many rows
- **WHEN** the materials screen of a placement with 60 block types is open
- **THEN** the rows scroll and the header and the bar for the whole placement stay in place

### Requirement: Long text is shortened, not cut
Text that does not fit its place SHALL be shortened with `…` at the end, and hovering it SHALL show the full text.

#### Scenario: Long schematic name
- **WHEN** a card shows an entry named `Medieval Gatehouse with Drawbridge and Towers`
- **THEN** the card shows the start of the name followed by `…`, and hovering the card shows the full name

### Requirement: Dialogs over their screen
A dialog opened from a Heavymatica screen, such as the block picker, a rename prompt, a delete confirmation or the export conflict question, SHALL appear over that screen, which stays visible but dimmed. Esc or the dialog's close button SHALL close only the dialog and return to that screen without a change.

#### Scenario: Rename and cancel
- **WHEN** a player opens Rename for `Starter Hut` in the library and presses Esc
- **THEN** the rename dialog closes, the library is shown with `Starter Hut` still selected, and the name is unchanged

### Requirement: HUD panels stay apart
The placement overlay, the targeting HUD and the mistake pointer SHALL NOT overlap each other at any GUI scale the game offers for a window of at least 854 × 480 pixels.

#### Scenario: Everything shown at once
- **WHEN** a player holds the remote with a locked placement selected, looks at a wrong block and has the mistake pointer on
- **THEN** the overlay, the targeting HUD and the pointer are all fully visible and none covers another
