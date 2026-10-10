# Spec Delta

## MODIFIED Requirements

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
