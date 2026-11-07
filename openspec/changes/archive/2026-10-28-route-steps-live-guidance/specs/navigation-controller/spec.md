# navigation-controller

## MODIFIED Requirements

### Requirement: Navigation state exposed as StateFlow

The navigation engine SHALL expose navigation state (active/inactive, current step index, remaining distance,
ETA, speed) as a `StateFlow` for every surface to observe, and SHALL expose the navigation position estimate
stream the map surfaces use for follow mode. The state SHALL carry the route's structured steps and the step
ahead, so a surface reads no guidance text from it.

#### Scenario: State updates on position change
- **WHEN** `NavigationListener.onPositionEstimate()` is called
- **THEN** the StateFlow SHALL emit updated position, bearing, and speed
- **AND** the position estimate stream SHALL emit the new navigation position

#### Scenario: State updates on instruction change
- **WHEN** `NavigationListener.onNextRouteStep()` is called
- **THEN** the StateFlow SHALL emit the updated step index and the step ahead
- **AND** the emitted state SHALL expose that step's manoeuvre kind, exit ordinal and street values

#### Scenario: Same state for all surfaces
- **WHEN** two surfaces observe the engine in one process
- **THEN** both receive the same navigation state emissions
