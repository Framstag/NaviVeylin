# navigation-controller Specification

## Purpose

Manages the turn-by-turn navigation lifecycle — starting, stopping, GPS follow mode, and reroute handling. Wraps the JNI `NavigationController` and `NavigationListener` for use from Kotlin/Compose.

## Requirements

### Requirement: Start navigation from route summary dialog
The "Start Navigation" button in the route summary dialog SHALL call `OSMScoutClient.startNavigationWithVehicle()` with the calculated route handle and selected vehicle.

#### Scenario: Start navigation with car
- **WHEN** user taps "Start Navigation" in the route summary dialog
- **AND** the route was calculated with vehicle = Car
- **THEN** `startNavigationWithVehicle()` SHALL be called with the route handle and `Vehicle.CAR`
- **AND** GPS follow mode SHALL be enabled
- **AND** the route summary dialog SHALL switch to active navigation mode

#### Scenario: Start navigation with bicycle
- **WHEN** user taps "Start Navigation"
- **AND** the route was calculated with vehicle = Bicycle
- **THEN** `startNavigationWithVehicle()` SHALL be called with `Vehicle.BICYCLE`

### Requirement: Start navigation from route panel
The route panel SHALL display a "Start Navigation" button when a route is calculated and navigation is not active.

#### Scenario: Start Navigation button in route panel
- **WHEN** a route is calculated
- **AND** navigation is not active
- **THEN** a "Start Navigation" button SHALL be visible in the route panel
- **AND** tapping it SHALL start navigation

### Requirement: Stop navigation
The system SHALL provide a way to stop active navigation, which SHALL call `NavigationController.stop()` and disable GPS follow mode.

#### Scenario: Stop via button
- **WHEN** navigation is active
- **AND** user taps "Stop Navigation"
- **THEN** `NavigationController.stop()` SHALL be called
- **AND** GPS follow mode SHALL be disabled
- **AND** the route summary dialog SHALL return to summary mode

#### Scenario: Stop via route panel
- **WHEN** navigation is active
- **AND** the route panel is open
- **THEN** a "Stop Navigation" button SHALL be visible
- **AND** tapping it SHALL stop navigation

### Requirement: GPS follow mode
During active navigation, the map SHALL auto-center on the current GPS location. The map SHALL rotate so the driving direction points up.

#### Scenario: Follow mode enabled on start
- **WHEN** navigation starts
- **THEN** GPS follow mode SHALL be enabled
- **AND** the map SHALL center on the current location
- **AND** the map SHALL rotate to driving direction

#### Scenario: Follow mode updates on location change
- **WHEN** GPS location updates during navigation
- **THEN** the map SHALL re-center on the new location
- **AND** the map SHALL re-rotate to the new bearing

### Requirement: Reroute handling
When the navigation engine detects the vehicle is off-route, the system SHALL recalculate the route from the current position to the destination.

#### Scenario: Reroute on off-route detection
- **WHEN** `NavigationListener.onRerouteRequest()` is called
- **THEN** a new route SHALL be calculated from current position to destination
- **AND** navigation SHALL continue with the new route
- **AND** the step list SHALL update with new instructions

### Requirement: Navigation state exposed as StateFlow
The navigation controller SHALL expose navigation state (active/inactive, current step index, remaining distance, ETA, speed) as a `StateFlow` for Compose UI to observe.

#### Scenario: State updates on position change
- **WHEN** `NavigationListener.onPositionEstimate()` is called
- **THEN** the StateFlow SHALL emit updated position, bearing, and speed

#### Scenario: State updates on instruction change
- **WHEN** `NavigationListener.onNextRouteInstruction()` is called
- **THEN** the StateFlow SHALL emit the updated step index and next turn info
