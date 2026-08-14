## Purpose

Allow drivers to select a location on the car map and start navigation to it, providing a visual destination picker alternative to text search.

## ADDED Requirements

### Requirement: Select location on car map
The system SHALL allow the user to select a location on the car map by tapping or clicking on the map surface.

#### Scenario: Select location via tap
- **WHEN** the user taps a point on the car map
- **THEN** a selection marker appears at the tapped coordinates

#### Scenario: Select location via rotary controller
- **WHEN** the user navigates the map cursor to a point and confirms selection
- **THEN** a selection marker appears at the cursor coordinates

### Requirement: Location details on selection
The system SHALL display location details (coordinates, nearby street name) when a location is selected on the car map.

#### Scenario: Details shown on selection
- **WHEN** a location is selected on the car map
- **THEN** a details overlay shows the coordinates and nearby street name (if available)

### Requirement: Navigate to selected location
The system SHALL provide a "Navigate here" action on the selected location, wired to the existing routing pipeline (`RoutePanelViewModel.calculateRoute()` + `NavigationViewModel.startNavigation()`).

#### Scenario: Navigate to selected point
- **WHEN** the user selects a location on the car map and taps "Navigate here"
- **THEN** the system calculates a route from the current GPS position to the selected location and starts navigation

#### Scenario: Navigation screen shown after selection
- **WHEN** navigation starts from a map selection
- **THEN** the car screen transitions to the `NavigationTemplate` with turn-by-turn guidance

### Requirement: Select favorite on car map
The system MAY allow the user to tap a favorite marker on the car map to select it as a destination.

> **Deferred:** Favorite markers are baked into the native-rendered bitmap by `OSMScoutClient.renderWithRouteAndPois()`. Hit-testing a marker requires either a new JNI query method or an extra overlay layer. This is deferred to a later change.

#### Scenario: Navigate to favorite from map
- **WHEN** the user taps a favorite marker on the car map and confirms
- **THEN** the system calculates a route to the favorite location and starts navigation

### Requirement: Clear map selection
The system SHALL allow the user to clear the current map selection.

#### Scenario: Clear selection
- **WHEN** the user taps a "Clear" or "Deselect" action
- **THEN** the selection marker is removed from the map
