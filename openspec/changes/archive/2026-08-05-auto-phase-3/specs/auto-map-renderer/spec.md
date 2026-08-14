## Purpose

Render libosmscout map tiles to the Android Auto display using `MapTemplate` with a custom `Surface` renderer, providing visual map context alongside turn-by-turn navigation.

## ADDED Requirements

### Requirement: Map rendered on car display
The system SHALL render a libosmscout map on the Android Auto car display using `MapTemplate` with a custom `Surface` renderer backed by `OSMScoutClient.renderWithRouteAndPois()`.

#### Scenario: Map shown when not navigating
- **WHEN** Android Auto is connected and no navigation is active
- **THEN** the car screen shows a browsable map centered on the current GPS position (or last known position)

#### Scenario: Map shown during navigation
- **WHEN** navigation is active on Android Auto
- **THEN** the car screen shows the `NavigationTemplate` with turn-by-turn guidance (existing behavior unchanged)

### Requirement: Map renders at correct center and zoom
The system SHALL render the map at the correct geographic center, zoom level, and rotation angle matching the current viewport state.

#### Scenario: Map renders at GPS position
- **WHEN** the car map is displayed and GPS position is available
- **THEN** the map centers on the current GPS latitude/longitude at a default zoom level

#### Scenario: Map rotation follows navigation heading
- **WHEN** navigation is active and the car map is visible
- **THEN** the map rotates to match the driving direction (north-up mode available as toggle)

### Requirement: GPS position marker on car map
The system SHALL display the current GPS position as a marker on the car map, reusing the existing `LocationService.location` data.

#### Scenario: GPS marker shown
- **WHEN** GPS position is available
- **THEN** a position marker appears on the car map at the current coordinates

#### Scenario: GPS marker updates
- **WHEN** the vehicle moves more than 5 meters
- **THEN** the GPS marker position updates on the car map

### Requirement: Favorites markers on car map
The system SHALL display favorite location markers on the car map, reusing `FavoriteRepository.favorites` data.

#### Scenario: Favorites shown on map
- **WHEN** the car map is displayed and favorites exist
- **THEN** favorite location markers appear on the map

#### Scenario: Favorites update on change
- **WHEN** a favorite is added, removed, or modified on the phone
- **THEN** the car map markers update to reflect the change

### Requirement: Map re-renders on viewport change
The system SHALL re-render the map when the viewport center, zoom, or rotation changes.

#### Scenario: Re-render on pan
- **WHEN** the user pans the map
- **THEN** the map re-renders at the new center position

#### Scenario: Re-render on zoom
- **WHEN** the user zooms in or out
- **THEN** the map re-renders at the new magnification level

#### Scenario: Re-render on rotation
- **WHEN** the map rotation changes
- **THEN** the map re-renders at the new angle
