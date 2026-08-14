## Purpose

Provides a "Navigate here" action on search results and favorite locations selected on the Android Auto car screen, wired to the existing routing and navigation pipeline so drivers can start turn-by-turn navigation entirely from the car.

## ADDED Requirements

### Requirement: "Navigate here" action on search results
The system SHALL provide a "Navigate here" action on each search result in the `SearchTemplate`, allowing the driver to start navigation to that location.

#### Scenario: Navigate here from search result
- **WHEN** user selects a search result
- **THEN** a "Navigate here" action is available
- **AND WHEN** user confirms the action
- **THEN** the system calculates a route to the selected location and starts navigation

### Requirement: "Navigate here" action on favorites
The system SHALL provide a "Navigate here" action on each favorite in the `PlaceListTemplate`, allowing the driver to start navigation to that location.

#### Scenario: Navigate here from favorite
- **WHEN** user selects a favorite
- **THEN** a "Navigate here" action is available
- **AND WHEN** user confirms the action
- **THEN** the system calculates a route to the selected location and starts navigation

### Requirement: Route calculation uses current GPS position as start
The system SHALL use the current GPS position as the route start point when calculating a route from the car screen.

#### Scenario: Route from current location
- **WHEN** user selects "Navigate here"
- **THEN** the route is calculated from the current GPS position to the selected destination

#### Scenario: No GPS fix shows error
- **WHEN** user selects "Navigate here" but no GPS fix is available
- **THEN** the system shows an error message indicating GPS is required

### Requirement: Route calculation uses current routing profile
The system SHALL use the currently selected routing profile (car/bicycle/pedestrian) when calculating the route from the car screen.

#### Scenario: Uses active profile
- **WHEN** user selects "Navigate here"
- **THEN** the route is calculated using the currently active routing profile

### Requirement: Navigation starts immediately after route calculation
The system SHALL start turn-by-turn navigation immediately after the route is successfully calculated from the car screen.

#### Scenario: Navigation starts on route ready
- **WHEN** route calculation completes successfully
- **THEN** turn-by-turn navigation starts automatically
- **AND** the car screen switches to the `NavigationTemplate`

#### Scenario: Route calculation failure shows error
- **WHEN** route calculation fails
- **THEN** the system shows an error message and does not start navigation

### Requirement: PaneTemplate as root screen
The system SHALL display a `PaneTemplate` as the root screen when not actively navigating, with shortcuts to search and favorites.

#### Scenario: PaneTemplate shown when idle
- **WHEN** the user is not navigating and Android Auto is connected
- **THEN** the car screen shows a `PaneTemplate` with search and favorites options

#### Scenario: PaneTemplate hidden during navigation
- **WHEN** navigation is active
- **THEN** the `NavigationTemplate` is shown instead of the `PaneTemplate`
