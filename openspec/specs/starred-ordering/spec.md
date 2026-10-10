# starred-ordering Specification

## Purpose
Lets the user control the order of starred favorites — one order that spans all groups — so the favorites that matter most come first in the chip bar and on the car screen. The order is explicit, persisted in the favorites JSON file and restored unchanged after a restart.

## Requirements

### Requirement: Starred order is user-defined and spans all groups

The starred favorites SHALL form one order that spans every group, and the user SHALL be able to change a starred favorite's position in it. The order SHALL be persisted in the favorites JSON file and SHALL be the sequence shown by every surface that lists starred favorites (phone chip bar, Android Auto starred list). A starred favorite's place in that order SHALL NOT be derived from its position inside its group or from the group's position in the group order.

#### Scenario: Move a starred favorite to a new position

- **WHEN** the user moves a starred favorite from one position in the starred order to another
- **THEN** that favorite SHALL be at the new position
- **AND** the other starred favorites SHALL keep their relative order

#### Scenario: Moving across groups

- **GIVEN** starred favorites of several groups are shown in one sequence
- **WHEN** the user moves a starred favorite ahead of a starred favorite of another group
- **THEN** it SHALL occupy that position in the sequence
- **AND** it SHALL still belong to its own group

#### Scenario: Moving to the first position

- **WHEN** the user moves a starred favorite to the top of the starred order
- **THEN** it SHALL be the first entry of the sequence

#### Scenario: Moving to the last position

- **WHEN** the user moves a starred favorite to the bottom of the starred order
- **THEN** it SHALL be the last entry of the sequence

#### Scenario: Order survives a restart

- **GIVEN** the user has reordered the starred favorites
- **WHEN** the app is closed and reopened
- **THEN** the starred favorites SHALL be listed in the reordered sequence

#### Scenario: No change when the position is unchanged

- **GIVEN** a starred favorite already occupies the target position
- **WHEN** a move to that same position is requested
- **THEN** the operation SHALL succeed
- **AND** the order SHALL be unchanged

### Requirement: Starring and unstarring change the starred order

Starring a favorite SHALL place it at the end of the starred order. Unstarring a favorite SHALL remove it from the starred order together with the position it held, without changing the relative order of the remaining entries. Starring a favorite that is already starred SHALL leave its position unchanged.

#### Scenario: Starring appends at the end

- **GIVEN** the starred order holds entries
- **WHEN** a favorite is starred
- **THEN** it SHALL be the last entry of the starred order

#### Scenario: Unstarring removes the entry

- **GIVEN** a favorite is in the middle of the starred order
- **WHEN** it is unstarred
- **THEN** it SHALL no longer appear in the starred order
- **AND** the remaining entries SHALL keep their relative order

#### Scenario: Starring again appends at the end

- **GIVEN** a favorite was unstarred and its old position is taken by another favorite
- **WHEN** it is starred again
- **THEN** it SHALL be the last entry of the starred order

#### Scenario: Starring an already starred favorite keeps its place

- **GIVEN** a starred favorite is in the middle of the starred order
- **WHEN** it is starred again
- **THEN** it SHALL keep its position

### Requirement: Move semantics for invalid or out-of-range targets

A move request naming an unknown group, an unknown favorite or a favorite that is not starred SHALL fail without changing stored state. A target position outside the order's bounds SHALL be clamped to the nearest valid position instead of failing, and a negative position SHALL mean the first position.

#### Scenario: Unknown group

- **WHEN** a move is requested for a group that does not exist
- **THEN** the operation SHALL report failure
- **AND** the stored starred order SHALL be unchanged

#### Scenario: Favorite that is not starred

- **WHEN** a move is requested for a favorite that exists but is not starred
- **THEN** the operation SHALL report failure
- **AND** the stored starred order SHALL be unchanged

#### Scenario: Target beyond the end of the order

- **WHEN** a move names a target position greater than the order's last index
- **THEN** the favorite SHALL be placed at the last position
- **AND** the operation SHALL report success

#### Scenario: Negative target position

- **WHEN** a move names a target position below the order's first index
- **THEN** the favorite SHALL be placed at the first position
- **AND** the operation SHALL report success

#### Scenario: A single starred favorite

- **WHEN** a move is requested while exactly one favorite is starred
- **THEN** the order SHALL be unchanged
- **AND** the operation SHALL report success

### Requirement: Other operations leave the starred order alone

Operations other than an explicit move of a starred favorite or a star change SHALL NOT change the stored starred order: reordering favorites inside a group, reordering the groups, adding or deleting a favorite, renaming a favorite or a group, and setting a group color all leave it as it is. Moving a favorite into another group SHALL keep its star and its place in the starred order.

#### Scenario: In-group reorder does not touch it

- **GIVEN** a group holds starred favorites
- **WHEN** the user reorders favorites inside that group
- **THEN** the starred order SHALL be unchanged

#### Scenario: Group reorder does not touch it

- **WHEN** the user moves a group to another position in the group order
- **THEN** the starred order SHALL be unchanged

#### Scenario: Cross-group move keeps the place

- **GIVEN** a starred favorite is in the middle of the starred order
- **WHEN** the user moves it into another group
- **THEN** it SHALL keep its position in the starred order

#### Scenario: Deleting a starred favorite removes it

- **WHEN** a starred favorite is deleted
- **THEN** it SHALL no longer appear in the starred order
- **AND** the remaining entries SHALL keep their relative order

### Requirement: Order is recovered from a file that carries none

A favorites file whose starred favorites carry no stored position SHALL still yield a defined total order — the group order first, and within a group the favorite names — so that no starred favorite is dropped or duplicated. The next write SHALL store explicit positions for every starred favorite, making the order stable from then on.

#### Scenario: File without stored positions

- **GIVEN** a favorites file whose starred favorites carry no position
- **WHEN** the starred favorites are listed
- **THEN** all of them SHALL appear exactly once
- **AND** the sequence SHALL follow the group order and, inside a group, the favorite names

#### Scenario: Positions are stored from the next write on

- **GIVEN** such a file has been read
- **WHEN** any write is committed
- **THEN** the file SHALL carry a position for every starred favorite
- **AND** a later reader SHALL see the same sequence

### Requirement: Reordering persists with a single write per committed move

One user-visible starred reorder SHALL result in at most one persistence write of the favorites file. A failed move SHALL NOT write anything and SHALL leave the previously stored order in place. While a starred reorder is being persisted, a further starred reorder commit SHALL be ignored rather than interleaved.

#### Scenario: One write per committed reorder

- **WHEN** the user completes one starred reorder
- **THEN** the favorites store SHALL be persisted exactly once

#### Scenario: Failed move writes nothing

- **WHEN** a starred move fails
- **THEN** the favorites file SHALL NOT be rewritten
- **AND** the order shown SHALL remain the previously stored order

#### Scenario: Second drop during an in-flight reorder

- **GIVEN** a starred reorder is being persisted
- **WHEN** the user completes another starred reorder before the first one finished
- **THEN** the second commit SHALL be ignored
- **AND** the sequence SHALL afterwards be the one of the completed move

#### Scenario: Persistence failure keeps the app usable

- **WHEN** persisting the new starred order fails
- **THEN** the user SHALL see an error message
- **AND** the app SHALL NOT crash
- **AND** the reorder SHALL NOT be reported as successful
