# Spec Delta

## REMOVED Requirements

### Requirement: Chip order follows the stored favorite order

**Reason**: The chip bar no longer renders group blocks; it renders the one starred order that spans all groups (`starred-ordering`). Keeping this requirement would claim the in-group order as the bar's order, which the new order contradicts — a starred favorite's chip position is now independent of where the favorite sits inside its group.

**Migration**: The bar follows the stored starred order (new requirement "Chip bar follows the stored starred order"). The in-group order stays the contract of the group detail list and of the Android Auto place list (spec `fav-ordering`); a user who wants to change the chip sequence reorders the stars directly.

## ADDED Requirements

### Requirement: Chip bar follows the stored starred order

The chip bar SHALL list the starred favorites in the stored starred order — one flat sequence that spans all groups, with no group blocks — and SHALL update in place when that order changes while the sheet is open. The group name SHALL remain visible as the chip's secondary text.

#### Scenario: Chips follow a starred reorder

- **GIVEN** a group has three starred favorites
- **WHEN** the user moves the last one to the first position in the starred order
- **THEN** that favorite's chip SHALL be the first chip of the bar

#### Scenario: Chips cross group boundaries

- **GIVEN** starred favorites of two groups are shown
- **WHEN** the user moves a favorite of the second group ahead of a favorite of the first
- **THEN** the bar SHALL show the moved chip before it
- **AND** both chips SHALL still name their own group

#### Scenario: Chips update without reopening the sheet

- **GIVEN** the favorites sheet is open showing the chip bar
- **WHEN** a starred reorder is committed
- **THEN** the chip bar SHALL update to the new sequence

#### Scenario: Starring appends at the end of the bar

- **GIVEN** the bar already shows chips
- **WHEN** another favorite is starred
- **THEN** its chip SHALL appear as the last chip

#### Scenario: Chip order survives a restart

- **GIVEN** the user reordered the starred favorites
- **WHEN** the app is closed and reopened
- **THEN** the chip bar SHALL show the same sequence as before

### Requirement: Chip bar supports drag-and-drop reordering

The chip bar SHALL let the user pick up a chip by touch-and-hold and drop it at another position in the bar, and SHALL commit only the position the chip is released at: a drag that ends where it started, and a sheet dismissed during a drag, SHALL persist nothing. A short tap SHALL keep opening the route panel, and a horizontal swipe SHALL keep scrolling the bar.

#### Scenario: Drag moves a chip

- **GIVEN** the chip bar shows several chips
- **WHEN** the user holds a chip, drags it to another position in the bar and releases it
- **THEN** the chip SHALL be at that position
- **AND** the new sequence SHALL be persisted

#### Scenario: Drag that ends where it started

- **WHEN** the user holds a chip and releases it at its original position
- **THEN** nothing SHALL be persisted

#### Scenario: Sheet dismissed during a drag

- **GIVEN** the user is dragging a chip to another position
- **WHEN** the sheet is dismissed before the chip is released
- **THEN** nothing SHALL be persisted

#### Scenario: Tap still opens the route panel

- **WHEN** the user taps a chip without holding it
- **THEN** the favorites sheet SHALL close
- **AND** the route panel SHALL open with the tapped favorite as destination

#### Scenario: Swipe still scrolls the bar

- **GIVEN** more chips than fit on screen
- **WHEN** the user swipes horizontally without holding a chip
- **THEN** the bar SHALL scroll to reveal additional chips
