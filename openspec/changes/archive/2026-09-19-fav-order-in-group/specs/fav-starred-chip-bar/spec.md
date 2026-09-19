## Purpose

Defines the order of the starred-favorite chips at the top of the favorites sheet: group order first, then the stored favorite order inside each group.

## ADDED Requirements

### Requirement: Chip order follows the stored favorite order

The chip bar SHALL list starred favorites grouped by their group, and within a group in the stored favorite order. After a favorite is reordered inside its group, the chip bar SHALL show the new sequence.

#### Scenario: Chips mirror the group order

- **GIVEN** a group has three starred favorites
- **WHEN** the user moves the last one to the first position
- **THEN** that favorite's chip SHALL be the first chip of the group's block

#### Scenario: Chips update without reopening the sheet

- **GIVEN** the favorites sheet is open showing the chip bar
- **WHEN** a reorder is committed
- **THEN** the chip bar SHALL update to the new sequence

#### Scenario: Starring appends at the end of its group's block

- **GIVEN** a group already contributes chips to the bar
- **WHEN** another favorite of that group is starred
- **THEN** its chip SHALL appear after the group's existing chips, in stored order

#### Scenario: Chip order survives a restart

- **GIVEN** the user reordered the favorites of a group that contributes chips
- **WHEN** the app is closed and reopened
- **THEN** the chip bar SHALL show the same sequence as before
