## Purpose

Lets drivers browse and select from their saved favorite locations on the Android Auto car screen using `PlaceListTemplate`, backed by the existing `FavoriteRepository`, so they can navigate to favorites without touching the phone.

## ADDED Requirements

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
