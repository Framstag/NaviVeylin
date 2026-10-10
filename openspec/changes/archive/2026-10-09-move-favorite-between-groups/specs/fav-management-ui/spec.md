# Spec Delta

## ADDED Requirements

### Requirement: Favorite row offers a "Move to group" action

Each favorite item row SHALL offer a "Move to group" action, presented the same way as the group card's action menu so that the row's existing star, rename and delete controls keep their places. Activating it SHALL open a dialog that lists every group the favorite is not currently in, plus an option to create a new group. Confirming SHALL move the favorite into the chosen group and SHALL report the outcome; dismissing SHALL change nothing. When the favorites store holds only one group, the action SHALL NOT be offered, because there is no group to move into.

#### Scenario: Move action on the favorite row

- **WHEN** a favorite is displayed in a group detail list
- **THEN** the row SHALL offer a "Move to group" action alongside star, rename and delete
- **AND** the star, rename and delete controls SHALL keep their existing positions

#### Scenario: Destination dialog lists the other groups

- **WHEN** the user activates "Move to group" on a favorite
- **THEN** a destination dialog SHALL be shown
- **AND** it SHALL list the groups that do not hold that favorite
- **AND** it SHALL NOT list the favorite's current group

#### Scenario: Confirm moves the favorite

- **WHEN** the user selects a destination group and confirms
- **THEN** the favorite SHALL appear in that group's list
- **AND** the former group SHALL no longer list it
- **AND** the dialog SHALL close

#### Scenario: Dismiss leaves the favorite where it was

- **WHEN** the user dismisses the destination dialog without confirming
- **THEN** the favorite SHALL remain in its group
- **AND** nothing SHALL be written to the favorites store

#### Scenario: Destination group does not exist yet

- **WHEN** the user chooses the "new group" option, enters a name and confirms
- **THEN** that group SHALL be created
- **AND** the favorite SHALL be moved into it

#### Scenario: Action absent with a single group

- **GIVEN** the favorites store holds exactly one group
- **WHEN** a favorite in that group is displayed
- **THEN** the favorite row SHALL NOT offer the "Move to group" action

#### Scenario: A move that the store refuses is reported

- **WHEN** a move is refused because the destination group already holds a favorite of that name
- **THEN** the sheet SHALL report the refusal with a message naming the group and the favorite
- **AND** the list SHALL still show the favorite in its current group

### Requirement: Favorites management stays on the phone

Moving a favorite between groups SHALL be a phone-only surface. The Android Auto and Android Automotive OS favorites screen SHALL remain a browse-and-select place list without favorites-management actions, because the car templates cannot express the destination-group selection step and the car screen must stay operable while driving. Phone and car surfaces SHALL keep their existing labels and hierarchy for browsing; the car surface SHALL NOT gain a phone-derived action.

#### Scenario: Car favorites screen offers no move action

- **WHEN** the favorites screen is displayed on Android Auto or Android Automotive OS
- **THEN** it SHALL NOT offer a "Move to group" action or any other favorites-management action
- **AND** selecting a favorite SHALL keep its existing destination-picker behavior

#### Scenario: A move made on the phone is visible on the car

- **GIVEN** the user moves a favorite into another group on the phone
- **WHEN** the car favorites screen lists the favorites afterwards
- **THEN** the favorite SHALL appear under the destination group's header
