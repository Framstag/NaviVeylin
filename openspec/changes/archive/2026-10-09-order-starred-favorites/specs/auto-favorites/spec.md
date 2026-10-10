# Spec Delta

## MODIFIED Requirements

### Requirement: Favorites grouped by category

The system SHALL display favorites grouped by their category/group name, with group headers in the `PlaceListTemplate`, when the screen lists all favorites. The starred-favorites mode has its own ordering and header rule (requirement "Starred favorites render as one ordered list").

#### Scenario: Group headers shown

- **WHEN** favorites from multiple groups are displayed in the all-favorites mode
- **THEN** each group has a header showing the group name and color indicator

## ADDED Requirements

### Requirement: Starred favorites render as one ordered list

In its starred-favorites mode the Android Auto favorites screen SHALL render the starred favorites in the stored starred order (spec `starred-ordering`) as one ordered list without group headers, read-only, and SHALL update in place when that order changes while the screen is open. A row SHALL keep showing its name and address and SHALL keep starting the destination-picker flow when selected.

#### Scenario: Starred list follows a phone reorder

- **GIVEN** the user reordered the starred favorites on the phone
- **WHEN** the starred-favorites screen is shown on the car screen
- **THEN** the rows SHALL appear in the stored starred order
- **AND** they SHALL be presented as one list without group headers

#### Scenario: Starred list updates in place

- **GIVEN** the starred-favorites screen is open on the car screen
- **WHEN** the stored starred order changes
- **THEN** the list SHALL update in place with the new order, without leaving and re-entering the screen

#### Scenario: No reorder affordance on the car screen

- **WHEN** the starred-favorites screen is displayed
- **THEN** it SHALL offer no drag or move action for individual favorites

#### Scenario: Selecting a starred favorite still navigates

- **WHEN** the user selects a row in the starred-favorites list
- **THEN** the system SHALL transition to the destination picker with that location as the target

#### Scenario: Nothing starred

- **WHEN** no favorite is starred
- **THEN** the screen SHALL show the "no starred favorites" message with its hint
