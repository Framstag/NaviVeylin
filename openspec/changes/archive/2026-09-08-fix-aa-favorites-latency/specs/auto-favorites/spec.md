## MODIFIED Requirements

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
