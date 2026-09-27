# Spec Delta

## MODIFIED Requirements

### Requirement: Car-only navigation start
The system SHALL start navigation from the car even when the phone UI has not been opened in this process, through the same process-scoped navigation engine the phone uses.

#### Scenario: Deep link with phone UI closed
- **WHEN** a deep link or car action triggers a destination selection and the phone route panel is not available
- **THEN** the engine acquires the route itself and starts navigation
- **AND** the car renders the active navigation

#### Scenario: GPS position available
- **WHEN** a car-only navigation start occurs
- **THEN** the start position is taken from the current navigation position or the location service
- **AND** the engine holds the location subscription for the duration of the navigation

#### Scenario: Location subscription released after navigation
- **WHEN** a car-only navigation ends and no surface holds a location subscription
- **THEN** no further location updates are requested

#### Scenario: No GPS fix
- **WHEN** car-only navigation start occurs without any GPS position
- **THEN** the system surfaces a "GPS signal required" error on the car screen instead of failing silently

### Requirement: Phone stop navigation reflected on car
The system SHALL return the car screen to the root screen when navigation stops on the phone.

#### Scenario: Navigation stopped from phone
- **WHEN** the user stops navigation on the phone while the car is connected
- **THEN** the engine state leaves the navigating state
- **AND** the car session observes it and pops back to the root screen

### Requirement: Car stop navigation reflected on phone
The system SHALL stop navigation on the phone when stopped from the car, because both surfaces observe the same engine.

#### Scenario: Navigation stopped from car
- **WHEN** the user taps Stop on the car `NavigationTemplate`
- **THEN** the engine stops navigation
- **AND** the phone route panel and map leave the navigating state without a separate command path

### Requirement: Session lifecycle cleanup
The system SHALL release all session-scoped resources when the car disconnects, while leaving the
process-scoped navigation engine's own state untouched if navigation is still active.

#### Scenario: No leaks on disconnect
- **WHEN** the car session is destroyed
- **THEN** the session cancels its coroutine scope and stops observing navigation state

#### Scenario: Navigation survives a session disconnect
- **WHEN** the car session is destroyed while navigation is active
- **THEN** navigation continues in the engine
- **AND** a session started later observes the active navigation again
