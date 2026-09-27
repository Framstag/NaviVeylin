# Spec Delta

## MODIFIED Requirements

### Requirement: Start navigation from route summary dialog
The "Start Navigation" button in the route summary dialog SHALL start navigation through the process-scoped navigation engine with the calculated route and the selected vehicle, and the surface SHALL enable its own follow presentation for the started navigation.

#### Scenario: Start navigation with car
- **WHEN** user taps "Start Navigation" in the route summary dialog
- **AND** the route was calculated with vehicle = Car
- **THEN** the engine SHALL start navigation with the route handle and `Vehicle.CAR`
- **AND** the phone map SHALL enable GPS follow mode
- **AND** the route summary dialog SHALL switch to active navigation mode

#### Scenario: Start navigation with bicycle
- **WHEN** user taps "Start Navigation"
- **AND** the route was calculated with vehicle = Bicycle
- **THEN** the engine SHALL start navigation with the calculated route and `Vehicle.BICYCLE`

#### Scenario: Vehicle profile retained for reroute
- **WHEN** navigation was started with a vehicle profile
- **THEN** the engine retains that profile for a reroute of the same navigation

### Requirement: Start navigation from route panel
The route panel SHALL display a "Start Navigation" button when a route is calculated and navigation is not active.

#### Scenario: Start Navigation button in route panel
- **WHEN** a route is calculated
- **AND** navigation is not active
- **THEN** a "Start Navigation" button SHALL be visible in the route panel
- **AND** tapping it SHALL start navigation through the engine

### Requirement: Stop navigation
The system SHALL provide a way to stop active navigation, which SHALL release the engine's native navigation controller and SHALL leave navigation state on all surfaces.

#### Scenario: Stop via button
- **WHEN** navigation is active
- **AND** user taps "Stop Navigation"
- **THEN** the engine's native navigation controller SHALL be stopped and released
- **AND** GPS follow mode SHALL be disabled on the surface that stopped
- **AND** the route summary dialog SHALL return to summary mode

#### Scenario: Stop via route panel
- **WHEN** navigation is active
- **AND** the route panel is open
- **THEN** a "Stop Navigation" button SHALL be visible
- **AND** tapping it SHALL stop navigation

#### Scenario: Stop from a notification or car action
- **WHEN** navigation is stopped from the ongoing notification or from the car navigation view
- **THEN** the same engine stop runs
- **AND** the route panel and route drawing on the phone are cleared as well

### Requirement: GPS follow mode
During active navigation, the navigation surface's map SHALL auto-center on the current GPS location. The map SHALL rotate so the driving direction points up.

#### Scenario: Follow mode enabled on start
- **WHEN** navigation starts on a surface
- **THEN** that surface SHALL enable GPS follow mode
- **AND** the map SHALL center on the current location
- **AND** the map SHALL rotate to driving direction

#### Scenario: Follow mode updates on location change
- **WHEN** GPS location updates during navigation
- **THEN** the map SHALL re-center on the new location
- **AND** the map SHALL re-rotate to the new bearing

#### Scenario: Follow mode is not imposed on the other surface
- **WHEN** navigation starts on one surface
- **THEN** another surface's map mode, center and rotation are unchanged

### Requirement: Reroute handling
When the engine detects the vehicle is off-route, the engine SHALL recalculate the route from the current position to the destination and continue navigation on the new route.

#### Scenario: Reroute on off-route detection
- **WHEN** `NavigationListener.onRerouteRequest()` is reported by the native engine and the reroute is confirmed
- **THEN** the engine SHALL calculate a new route from the current position to the destination
- **AND** navigation SHALL continue with the new route
- **AND** the step list SHALL update with new instructions on every surface

#### Scenario: Reroute without a surface UI
- **WHEN** a reroute is confirmed while no navigation surface is displayed
- **THEN** the engine SHALL re-acquire the route on its own and keep navigating

### Requirement: Navigation state exposed as StateFlow
The navigation engine SHALL expose navigation state (active/inactive, current step index, remaining distance, ETA, speed) as a `StateFlow` for every surface to observe, and SHALL expose the navigation position estimate stream the map surfaces use for follow mode.

#### Scenario: State updates on position change
- **WHEN** `NavigationListener.onPositionEstimate()` is called
- **THEN** the StateFlow SHALL emit updated position, bearing, and speed
- **AND** the position estimate stream SHALL emit the new navigation position

#### Scenario: State updates on instruction change
- **WHEN** `NavigationListener.onNextRouteInstruction()` is called
- **THEN** the StateFlow SHALL emit the updated step index and next turn info

#### Scenario: Same state for all surfaces
- **WHEN** two surfaces observe the engine in one process
- **THEN** both receive the same navigation state emissions
