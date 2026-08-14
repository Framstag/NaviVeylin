# fav-detail-save Specification

## Purpose

Provides a details sheet that appears after the user selects a search result, showing location information and an "Add to Favorites" button with a group picker.

## Requirements

### Requirement: Details sheet opens after search result selection
When the user selects a location from search results, the map SHALL center on the location, render a selection marker, and a details sheet SHALL open showing the location name and administrative region hierarchy.

#### Scenario: Details sheet shows location info
- **WHEN** user selects a search result
- **THEN** the map SHALL center on the location, a marker SHALL render, and a details sheet SHALL display the location label and admin region

#### Scenario: Details sheet dismisses
- **WHEN** user taps outside the details sheet or taps a dismiss button
- **THEN** the details sheet SHALL close but the map marker SHALL remain

### Requirement: Details sheet has "Add to Favorites" button
The details sheet SHALL contain an "Add to Favorites" button. Tapping it SHALL show a group picker (list of existing groups + "New group" option). After selecting or creating a group, the location SHALL be saved as a favorite in that group.

#### Scenario: Add to favorites with existing group
- **WHEN** user taps "Add to Favorites" and selects an existing group
- **THEN** the location SHALL be saved to that group and a confirmation (Snackbar) SHALL appear

#### Scenario: Add to favorites with new group
- **WHEN** user taps "Add to Favorites" and selects "New group"
- **THEN** a text input SHALL appear, and after entering a name the group SHALL be created and the location saved

#### Scenario: Duplicate favorite shows error
- **WHEN** user tries to add a location that already exists in the selected group
- **THEN** an error message SHALL be shown and the favorite SHALL NOT be duplicated

### Requirement: Details sheet shows "Remove from Favorites" for already-faved locations
If the selected location is already saved as a favorite in any group, the button SHALL read "Remove from Favorites" instead. Tapping it SHALL delete the favorite.

#### Scenario: Already-faved location shows remove
- **WHEN** user selects a search result that matches an existing favorite
- **THEN** the button SHALL read "Remove from Favorites"

#### Scenario: Remove favorite succeeds
- **WHEN** user taps "Remove from Favorites"
- **THEN** the favorite SHALL be deleted and the button SHALL revert to "Add to Favorites"

### Requirement: New group name collision error
When the user adds a favorite to a new group and the chosen group name already exists, the system SHALL NOT create a duplicate group or favorite, and SHALL show an error message.

#### Scenario: Duplicate new group name rejected
- **WHEN** user taps "Add to Favorites"
- **AND** selects "+ New group"
- **AND** enters a name that already exists
- **AND** confirms
- **THEN** the group SHALL NOT be duplicated
- **AND** the favorite SHALL NOT be saved
- **AND** an error message SHALL be shown
