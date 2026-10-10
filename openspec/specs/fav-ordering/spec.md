# fav-ordering Specification

## Purpose

Lets a user control where a favorite sits inside its group, so the favorites that matter most are at the top of the group's list. The order is explicit, per group, persisted in the favorites JSON file and restored unchanged after a restart.

## Requirements

### Requirement: Favorite position within a group is user-defined

The system SHALL let the user change the position of a favorite within its group. The resulting order SHALL be persisted in the favorites JSON file and SHALL be the order shown by every surface that lists favorites inside a group (group detail list, Android Auto place list). The order of the starred favorites is a separate order and is not derived from this one (spec `starred-ordering`).

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

#### Scenario: The starred order is untouched

- **GIVEN** a group holds starred favorites
- **WHEN** the user reorders favorites inside that group
- **THEN** the starred order SHALL be unchanged

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

### Requirement: A favorite can be moved from one group into another

The system SHALL let the user move a favorite out of the group it belongs to and into a different group. The moved favorite SHALL keep its name, its coordinates, its attributes and its star, SHALL no longer be listed by its former group, and SHALL be listed by the destination group at a user-visible position. The two groups SHALL otherwise keep their relative orders. The new membership SHALL be persisted in the favorites JSON file and SHALL be restored unchanged after a restart. Every surface that lists favorites by group (favorites sheet group detail, starred chip bar, Android Auto place list) SHALL show the favorite under its new group.

#### Scenario: Move a favorite into another group

- **WHEN** the user moves a favorite from one group into another group
- **THEN** the destination group SHALL list that favorite
- **AND** the former group SHALL no longer list it
- **AND** every other favorite SHALL keep its group and its relative order

#### Scenario: Star and attributes survive the move

- **GIVEN** a favorite is starred and carries attributes
- **WHEN** it is moved into another group
- **THEN** it SHALL still be starred in the destination group
- **AND** its attributes and coordinates SHALL be unchanged

#### Scenario: Destination position is the user-visible position

- **WHEN** a favorite is moved into a group that already lists favorites
- **THEN** the destination group's list SHALL show the favorite at the position the move targeted
- **AND** the destination group's other favorites SHALL keep their relative order

#### Scenario: Group membership survives a restart

- **GIVEN** the user has moved a favorite into another group
- **WHEN** the app is closed and reopened
- **THEN** the favorite SHALL be listed by the destination group
- **AND** SHALL NOT be listed by its former group

#### Scenario: A starred favorite follows its new group

- **GIVEN** a favorite is starred and is shown under its group in the starred chip bar
- **WHEN** it is moved into another group
- **THEN** the starred chip bar SHALL present it with the destination group as its group
- **AND** it SHALL stay starred

### Requirement: Move-to-group semantics for invalid or refused targets

A move request naming an unknown source group or a favorite the source group does not hold SHALL fail without changing stored state. A move into a destination group that already holds a favorite of the same name SHALL be refused, leaving both groups unchanged, because favorites are identified by name within their group. A destination group that does not exist yet SHALL be created for the move instead of failing it. A move whose destination is the favorite's own group SHALL succeed without changing stored state. A target position outside the destination group's bounds SHALL be clamped to the nearest valid position instead of failing.

#### Scenario: Unknown source group

- **WHEN** a move names a source group that does not exist
- **THEN** the operation SHALL report failure
- **AND** no stored grouping SHALL change

#### Scenario: Destination group does not exist yet

- **WHEN** a move names a destination group that does not exist
- **THEN** that group SHALL be created
- **AND** the favorite SHALL be listed by it
- **AND** the move SHALL report success

#### Scenario: Favorite not held by the source group

- **WHEN** a move names a favorite the source group does not hold
- **THEN** the operation SHALL report failure
- **AND** no stored grouping SHALL change

#### Scenario: Destination already holds that name

- **GIVEN** the destination group already holds a favorite with the same name
- **WHEN** a move of that name into that group is requested
- **THEN** the operation SHALL report failure
- **AND** the source group SHALL still list the favorite
- **AND** the destination group SHALL be unchanged

#### Scenario: Destination is the favorite's own group

- **WHEN** a move names the favorite's own group as the destination
- **THEN** the operation SHALL succeed
- **AND** the stored grouping SHALL be unchanged

#### Scenario: Target position beyond the end of the destination group

- **WHEN** a move names a target position greater than the destination group's last index
- **THEN** the favorite SHALL be placed at the end of the destination group

#### Scenario: Target position before the start of the destination group

- **WHEN** a move names a target position below the destination group's first index
- **THEN** the favorite SHALL be placed at the top of the destination group

#### Scenario: Moving out of a group holding one favorite

- **GIVEN** the source group holds exactly one favorite
- **WHEN** that favorite is moved into another group
- **THEN** the source group SHALL list no favorites
- **AND** the destination group SHALL list the favorite
