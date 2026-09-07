## ADDED Requirements

### Requirement: Stop navigation hides route from map
When navigation is stopped, the route polyline and the `_route_start`/`_route_end` markers SHALL be removed from the map, while the route panel state (start, destination, vehicle, route summary, steps) SHALL be preserved so the user can start navigation again without recalculating.

#### Scenario: Stop removes route from map
- **WHEN** navigation is active
- **AND** the user stops navigation
- **THEN** the route polyline SHALL be removed from the map
- **AND** the `_route_start` and `_route_end` markers SHALL be removed
- **AND** the route panel SHALL retain the start, destination, vehicle, and route summary

#### Scenario: Route panel shows Start Navigation after stop
- **WHEN** navigation has been stopped
- **AND** the user opens the route planning dialog
- **THEN** the "Start Navigation" button SHALL be available
- **AND** the route summary SHALL still be shown

#### Scenario: Restarting navigation redraws the route
- **WHEN** the user stops navigation
- **AND** then starts navigation again on the same route
- **THEN** the route polyline and markers SHALL be rendered on the map again

#### Scenario: Route not redrawn after stop on screen re-entry
- **WHEN** navigation has been stopped
- **AND** the user navigates away from the map screen and back (screen recomposition)
- **THEN** the route polyline SHALL NOT reappear on the map
