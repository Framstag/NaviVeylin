# group-ordering Specification

## Purpose

Lets a user control the sequence in which their favorite groups are listed, so the groups that matter most are first on the phone grid, in the group pickers and in the car list. The order is explicit, persisted in the favorites JSON file and restored unchanged after a restart.

## Requirements

### Requirement: Group position is user-defined
The system SHALL let the user change the position of a favorite group in the group order. The resulting order SHALL be persisted in the favorites JSON file and SHALL be the order shown by every surface that lists groups.

#### Scenario: Move a group to a new position
- **WHEN** the user moves a group from one position in the group order to another
- **THEN** the group order SHALL show that group at the new position
- **AND** the other groups SHALL keep their relative order

#### Scenario: Moving a group to the first position
- **WHEN** the user moves a group to the top of the order
- **THEN** that group SHALL be the first group

#### Scenario: Moving a group to the last position
- **WHEN** the user moves a group to the bottom of the order
- **THEN** that group SHALL be the last group

#### Scenario: No change when the position is unchanged
- **GIVEN** a group already occupies the target position
- **WHEN** a move to that same position is requested
- **THEN** the operation SHALL succeed
- **AND** the order SHALL be unchanged

### Requirement: Group order survives a restart
The group order SHALL be persisted in the favorites JSON file and SHALL be restored unchanged when the app is reopened, without the user repeating the move.

#### Scenario: Reordered groups after a restart
- **GIVEN** the user has changed the group order
- **WHEN** the app is closed and reopened
- **THEN** the groups SHALL be listed in the order the user set

#### Scenario: A group added after a reorder is appended
- **GIVEN** the user has changed the group order
- **WHEN** a new group is created
- **THEN** the new group SHALL be the last group of the order
- **AND** the existing groups SHALL keep the order the user set

### Requirement: Group order is what every group-listing surface renders
The stored group order SHALL be the order in which groups are presented by every surface that lists them, so a reorder made on the phone changes how groups are ordered everywhere the groups are enumerated.

#### Scenario: The group grid follows the stored order
- **WHEN** the favorites sheet is opened
- **THEN** the group cards SHALL appear in the stored group order

#### Scenario: A group used as a single-group default is the first stored group
- **GIVEN** a surface needs one group without asking the user which one
- **WHEN** it resolves that group
- **THEN** it SHALL use the first group of the stored order
- **AND** a user who moves a group to the first position SHALL thereby change which group that surface uses

### Requirement: An order change is observable on its own
A group reorder SHALL be visible on every surface that lists groups even when nothing about the groups' contents changes, including when the store holds groups without favorites. The order SHALL therefore be published as a value whose equality depends on the sequence, not only as an ordering of a collection whose equality does not.

#### Scenario: Reordering groups that hold no favorites
- **GIVEN** the store holds several groups, none of them with favorites
- **WHEN** the user moves one group to another position
- **THEN** the grid SHALL show the new sequence
- **AND** the order SHALL be the stored order after a restart

#### Scenario: A surface that lists groups renders the new sequence
- **GIVEN** a surface is showing groups in the stored order
- **WHEN** the order changes while the group contents stay as they are
- **THEN** that surface SHALL present the groups in the new sequence
- **AND** the contents of each group SHALL be unchanged

### Requirement: Group reorder semantics for invalid or out-of-range targets
A move request naming a group that does not exist SHALL fail without changing stored state. A target position outside the order's bounds SHALL be clamped to the nearest valid position instead of failing, and a negative target position SHALL mean the first position.

#### Scenario: Unknown group
- **WHEN** a move is requested for a group that does not exist
- **THEN** the operation SHALL report failure
- **AND** no stored order SHALL change

#### Scenario: Target beyond the end of the order
- **WHEN** a move names a target position greater than the last group's index
- **THEN** the group SHALL be placed at the last position
- **AND** the operation SHALL report success

#### Scenario: Negative target position
- **WHEN** a move names a target position below the first group's index
- **THEN** the group SHALL be placed at the first position
- **AND** the operation SHALL report success

#### Scenario: Store holding a single group
- **GIVEN** the store holds exactly one group
- **WHEN** a move of that group is requested
- **THEN** the order SHALL be unchanged
- **AND** the operation SHALL report success
- **AND** the app SHALL NOT crash

### Requirement: Group order is stable across other operations
Operations other than an explicit group move SHALL NOT change the stored group order: creating a group appends it to the end, deleting a group keeps the relative order of the remaining groups, renaming a group keeps it at its position, and adding, deleting, renaming, starring, coloring or moving a favorite does not move its group.

#### Scenario: Delete preserves relative order
- **WHEN** a group is deleted from the middle of the order
- **THEN** the remaining groups SHALL keep their relative order

#### Scenario: Rename keeps the position
- **WHEN** a group is renamed
- **THEN** it SHALL stay at its position in the group order

#### Scenario: Favorite and group-attribute changes keep the position
- **WHEN** a favorite is added, deleted, renamed, starred or moved between groups, or a group's color is set
- **THEN** the group order SHALL be unchanged

### Requirement: Reordering groups persists with a single write per committed move
One user-visible group reorder SHALL result in at most one persistence write of the favorites file. A failed move SHALL NOT write anything and SHALL leave the previously stored order in place, and a write that fails SHALL be reported to the user instead of being treated as a successful move.

#### Scenario: One write per committed reorder
- **WHEN** the user completes one group reorder
- **THEN** the favorites store SHALL be persisted exactly once

#### Scenario: Failed move writes nothing
- **WHEN** a group move fails
- **THEN** the favorites file SHALL NOT be rewritten
- **AND** the order shown SHALL remain the previously stored order

#### Scenario: Persistence failure keeps the app usable
- **WHEN** persisting the new order fails
- **THEN** the user SHALL see an error message
- **AND** the app SHALL NOT crash
- **AND** the reorder SHALL NOT be reported as successful

### Requirement: Group reorder commits are serialised
While a group reorder is being persisted, a further group reorder SHALL be ignored rather than interleaved, so the two moves cannot produce an order the user did not ask for.

#### Scenario: Second move during an in-flight reorder
- **GIVEN** a group reorder is being persisted
- **WHEN** the user completes another group reorder before the first one finished
- **THEN** the second commit SHALL be dropped
- **AND** the order shown afterwards SHALL be the order of the completed move
