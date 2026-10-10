# Spec Delta

## ADDED Requirements

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
