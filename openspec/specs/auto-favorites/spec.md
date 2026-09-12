# Auto Favorites (auto-favorites)

## Purpose

Lets drivers browse and select from their saved favorite locations on the Android Auto car screen using `PlaceListTemplate`, backed by the existing `FavoriteRepository`, so they can navigate to favorites without touching the phone.

## Requirements

### Requirement: PlaceListTemplate displays favorites
The system SHALL display a `PlaceListTemplate` on the Android Auto screen showing the user's favorite locations when they select the favorites option.

#### Scenario: Favorites list shown
- **WHEN** user selects favorites from the car screen
- **THEN** a `PlaceListTemplate` is displayed with the user's favorite locations

#### Scenario: Empty favorites state
- **WHEN** the user has no saved favorites
- **THEN** the screen shows a "No favorites saved" message

#### Scenario: Favorites list hidden on navigation start
- **WHEN** user starts navigation from a favorite selection
- **THEN** the `PlaceListTemplate` is replaced by the `NavigationTemplate`

#### Scenario: Favorites appear without re-entering the screen
- **WHEN** the favorites screen is open before the favorites store has finished loading
- **THEN** the list updates in place with the loaded favorites once the store is ready, without the user leaving and re-entering the screen

### Requirement: Favorites grouped by category
The system SHALL display favorites grouped by their category/group name, with group headers in the `PlaceListTemplate`.

#### Scenario: Group headers shown
- **WHEN** favorites from multiple groups are displayed
- **THEN** each group has a header showing the group name and color indicator

### Requirement: Favorite displays name and address
The system SHALL display each favorite's name and address/description in the `PlaceListTemplate` list item.

#### Scenario: Favorite details shown
- **WHEN** a favorite is displayed in the list
- **THEN** its name and address/description are visible

### Requirement: Favorite selection triggers destination picker
The system SHALL allow the user to select a favorite, which triggers the destination picker flow to start navigation.

#### Scenario: Select favorite
- **WHEN** user taps a favorite in the list
- **THEN** the system transitions to the destination picker with that location as the target

### Requirement: Favorite add/remove from details screen
The favorite provider SHALL support adding and removing favorite locations, and the details screen SHALL use these operations to save or remove the displayed destination. Adding SHALL persist the destination (name, coordinates) into the favorites store; removing SHALL delete the matching favorite.

#### Scenario: Add favorite from details screen
- **WHEN** the user activates "Add to Favorites" on the details screen
- **THEN** the destination SHALL be added to the favorites store
- **AND** the details screen SHALL show the "Remove from Favorites" action afterwards

#### Scenario: Remove favorite from details screen
- **WHEN** the user activates "Remove from Favorites" on the details screen
- **THEN** the matching favorite SHALL be removed from the favorites store
- **AND** the details screen SHALL show the "Add to Favorites" action afterwards

#### Scenario: Favorite state reflects the store
- **WHEN** the details screen is open
- **AND** the favorites store changes
- **THEN** the details screen SHALL reflect the destination's current favorite state
