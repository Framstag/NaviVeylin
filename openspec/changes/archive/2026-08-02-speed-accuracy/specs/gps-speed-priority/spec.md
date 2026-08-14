## Purpose

Uses GPS-reported speed as the primary source for navigation speed display and auto-zoom, with position-difference computation as fallback when GPS speed is unavailable, ensuring accurate and responsive speed readings during routing.

## ADDED Requirements

### Requirement: SpeedAgent prefers GPS-reported speed
The `SpeedAgent` SHALL use the GPS-reported speed (`currentSpeed` from `GPSUpdateMessage`) when it is available (≥ 0), converting from m/s to km/h, instead of computing speed from position differences.

#### Scenario: GPS speed available
- **GIVEN** a `GPSUpdateMessage` with `currentSpeed = 13.9` (50 km/h in m/s) and accuracy < 100 m
- **WHEN** `SpeedAgent::Process` handles the message
- **THEN** the output `CurrentSpeedMessage` SHALL contain speed ≈ 50.0 km/h
- **AND** the position-difference computation SHALL NOT be used

#### Scenario: GPS speed unavailable
- **GIVEN** a `GPSUpdateMessage` with `currentSpeed = -1.0` (unknown)
- **WHEN** `SpeedAgent::Process` handles the message
- **THEN** the speed SHALL be computed from position differences as fallback

#### Scenario: GPS speed spike rejected
- **GIVEN** a `GPSUpdateMessage` with `currentSpeed = 60.0` (216 km/h in m/s)
- **WHEN** `SpeedAgent::Process` handles the message
- **THEN** the output `CurrentSpeedMessage` SHALL contain speed = -1.0 (rejected)
- **AND** no valid speed SHALL be reported

### Requirement: FIFO cleared when stationary
The `SpeedAgent` SHALL clear its segment FIFO when GPS-reported speed is below 0.5 m/s, preventing lingering movement history from producing false speed readings after stopping.

#### Scenario: Stop at traffic light
- **GIVEN** the vehicle was moving (FIFO has segments) and then stops
- **WHEN** a `GPSUpdateMessage` with `currentSpeed = 0.0` arrives
- **THEN** the FIFO SHALL be cleared
- **AND** the output speed SHALL be 0.0 km/h immediately

### Requirement: Kotlin side passes speed-unknown correctly
The `MapCanvasViewModel` SHALL pass `-1.0` for speed to the native `NavigationController.processLocation()` when `Location.hasSpeed()` is false, so the native code can distinguish "no GPS speed data" from "standing still".

#### Scenario: GPS provides speed
- **GIVEN** a `Location` object with `hasSpeed() = true` and `speed = 13.9` m/s
- **WHEN** `MapCanvasViewModel` feeds the location to the navigation engine
- **THEN** the speed parameter SHALL be 13.9

#### Scenario: GPS does not provide speed
- **GIVEN** a `Location` object with `hasSpeed() = false`
- **WHEN** `MapCanvasViewModel` feeds the location to the navigation engine
- **THEN** the speed parameter SHALL be -1.0
