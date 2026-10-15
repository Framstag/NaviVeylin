# Spec Delta

## MODIFIED Requirements

### Requirement: Calculate route

When both start and destination are set, the route panel SHALL display a "Calculate" button. Tapping it
SHALL call `OSMScoutClient.calculateRouteAsync()` with the selected start/dest coordinates and routing
profile.

#### Scenario: Calculate button enabled when both fields set
- **WHEN** both start and destination locations are set
- **THEN** the "Calculate" button SHALL be enabled
- **AND** tapping it SHALL initiate route calculation

#### Scenario: Calculate button disabled when fields missing
- **WHEN** either start or destination is not set
- **THEN** the "Calculate" button SHALL be disabled

#### Scenario: Progress indicator during calculation
- **WHEN** route calculation is in progress
- **THEN** a progress indicator SHALL be shown in the route panel
- **AND** the "Calculate" button SHALL be replaced with a "Cancel" button

#### Scenario: Progress percentage during calculation
- **WHEN** route calculation is in progress and the routing engine has reported a progress percentage
- **THEN** the route panel SHALL show that percentage alongside the progress indicator
- **AND** it SHALL show an updated percentage as the calculation progresses

#### Scenario: Route polyline rendered on map
- **WHEN** route calculation completes successfully
- **THEN** the route polyline SHALL be rendered on the map via `renderWithRoute()`
- **AND** `_route_start` and `_route_end` markers SHALL appear at the start and destination coordinates

#### Scenario: Route calculation failure
- **WHEN** route calculation fails (no route found, disconnected graph)
- **THEN** an error message SHALL be displayed in the route panel
- **AND** no route polyline SHALL be rendered

#### Scenario: Route calculation failure shows the reason's own message
- **WHEN** route calculation fails with a reason
- **THEN** the route panel SHALL show the string resource that belongs to that reason
- **AND** it SHALL NOT show one fixed message for every reason

#### Scenario: Route calculation failure offers the reason's action
- **WHEN** route calculation fails with a reason whose cause the user can act on
- **THEN** the route panel SHALL offer that action

#### Scenario: Route calculation failure of an unattachable end names the end
- **WHEN** route calculation fails because the start or the destination cannot be attached to the road
  network
- **THEN** the message SHALL name the affected end
