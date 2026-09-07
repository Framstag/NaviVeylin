## ADDED Requirements

### Requirement: Manual map panning during navigation

The system SHALL provide a pan affordance on the navigation view while navigating, letting the driver move the map and pinch-zoom during routing, with follow mode, speed-driven auto-zoom and heading-up rotation suspended while panned and re-engaged on pan exit (see `auto/map-pan`).

#### Scenario: Pan button on navigation map strip

- **WHEN** the user is navigating
- **THEN** the navigation map action strip shows a pan button beside the route-description action

#### Scenario: Map panned during navigation

- **WHEN** the driver pans while navigating
- **THEN** the map viewport moves with the gesture and stays at the panned position

#### Scenario: Follow resumes after pan

- **WHEN** the driver exits pan mode during navigation
- **THEN** the map resumes following the vehicle

## MODIFIED Requirements

### Requirement: Speed-driven auto-zoom during navigation

The system SHALL adjust the navigation map zoom with the vehicle speed while the auto-zoom setting is enabled; a manual zoom or a pan suspends auto-zoom until the vehicle crosses a speed band.

#### Scenario: Speed increases zoom out

- **WHEN** navigation is active, auto-zoom is enabled and the vehicle speeds up
- **THEN** the map zoom level adjusts to the higher speed band

#### Scenario: Speed decreases zoom in

- **WHEN** navigation is active, auto-zoom is enabled and the vehicle slows down
- **THEN** the map zoom level adjusts to the lower speed band

#### Scenario: Manual zoom suspends auto-zoom

- **WHEN** the user zooms manually during navigation
- **THEN** auto-zoom stops adjusting the zoom until the speed crosses a speed-band boundary

#### Scenario: Pan suspends auto-zoom

- **WHEN** the user pans the map during navigation
- **THEN** auto-zoom stops adjusting the zoom until the speed crosses a speed-band boundary

#### Scenario: Auto-zoom disabled by setting

- **WHEN** the auto-zoom setting is disabled
- **THEN** the navigation map zoom is not adjusted by speed
