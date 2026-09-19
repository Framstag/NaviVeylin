# fav-ordering Specification

## Purpose

Lets a user control where a favorite sits inside its group, so the favorites that matter most are at the top of the group's list and of the starred chip bar. The order is explicit, per group, persisted in the favorites JSON file and restored unchanged after a restart.

## Requirements

### Requirement: Favorite position within a group is user-defined

The system SHALL let the user change the position of a favorite within its group. The resulting order SHALL be persisted in the favorites JSON file and SHALL be the order shown by every surface that lists favorites (group detail list, starred chip bar, Android Auto place list).

#### Scenario: Move a favorite to a new position

- **WHEN** the user moves a favorite from one position in its group to another
- **THEN** the group's list SHALL show the favorite at the new position
- **AND** the other favorites SHALL keep their relative order

#### Scenario: Order survives a restart

- **GIVEN** the user has reordered favorites in a group
- **WHEN** the app is closed and reopened
- **THEN** the group SHALL list its favorites in the reordered sequence

#### Scenario: Moving to the first position

- **WHEN** the user moves a favorite to the top of its group
- **THEN** that favorite SHALL be the first entry of the group

#### Scenario: Moving to the last position

- **WHEN** the user moves a favorite to the bottom of its group
- **THEN** that favorite SHALL be the last entry of the group

#### Scenario: No change when the position is unchanged

- **GIVEN** a favorite already occupies the target position
- **WHEN** a move to that same position is requested
- **THEN** the operation SHALL succeed
- **AND** the order SHALL be unchanged

### Requirement: Move semantics for invalid or out-of-range targets

A move request naming an unknown group or an unknown favorite within the group SHALL fail without changing stored state. A target position outside the group's bounds SHALL be clamped to the nearest valid position instead of failing.

#### Scenario: Unknown group

- **WHEN** a move is requested for a group that does not exist
- **THEN** the operation SHALL report failure
- **AND** no stored order SHALL change

#### Scenario: Unknown favorite

- **WHEN** a move is requested for a favorite that does not exist in the named group
- **THEN** the operation SHALL report failure
- **AND** no stored order SHALL change

#### Scenario: Target beyond the end of the group

- **WHEN** a move names a target position greater than the group's last index
- **THEN** the favorite SHALL be placed at the last position
- **AND** the operation SHALL report success

#### Scenario: Negative target position

- **WHEN** a move names a target position below the group's first index
- **THEN** the favorite SHALL be placed at the first position
- **AND** the operation SHALL report success

#### Scenario: Group with a single favorite

- **WHEN** a move is requested inside a group that holds exactly one favorite
- **THEN** the order SHALL be unchanged
- **AND** the operation SHALL report success

### Requirement: Order is scoped to a group

The position of a favorite SHALL be stored per group. Two groups that contain favorites with the same name SHALL keep independent orders.

#### Scenario: Same-named favorites in different groups

- **GIVEN** two groups each contain a favorite called "Home"
- **WHEN** the user moves "Home" inside the first group
- **THEN** the second group's order SHALL be unchanged

### Requirement: Order is stable across other operations

Operations other than an explicit move SHALL NOT change the stored order: adding appends to the end of the group, deleting keeps the relative order of the remaining favorites, renaming keeps the favorite at its position, and starring or unstarring does not move it.

#### Scenario: Add appends at the end

- **WHEN** a favorite is added to a group
- **THEN** it SHALL be the last entry of that group

#### Scenario: Delete preserves relative order

- **WHEN** a favorite is deleted from the middle of a group
- **THEN** the remaining favorites SHALL keep their relative order

#### Scenario: Rename keeps the position

- **WHEN** a favorite is renamed
- **THEN** it SHALL stay at its current position in the group

#### Scenario: Star toggle keeps the position

- **WHEN** a favorite is starred or unstarred
- **THEN** it SHALL stay at its current position in the group

### Requirement: Reordering persists with a single write per committed move

One user-visible reorder operation SHALL result in at most one persistence write of the favorites file. A failed move SHALL NOT write anything and SHALL leave the previously stored order in place.

#### Scenario: One write per committed reorder

- **WHEN** the user completes one reorder
- **THEN** the favorites store SHALL be persisted exactly once

#### Scenario: Failed move writes nothing

- **WHEN** a move fails
- **THEN** the favorites file SHALL NOT be rewritten
- **AND** the order shown SHALL remain the previously stored order

#### Scenario: Persistence failure keeps the app usable

- **WHEN** persisting the new order fails
- **THEN** the user SHALL see an error message
- **AND** the app SHALL NOT crash
- **AND** the reorder SHALL NOT be reported as successful
