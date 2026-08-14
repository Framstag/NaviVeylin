# navigation-state-display Specification

## Purpose

Shows real-time navigation information during active guidance: estimated time of arrival, remaining distance, current speed, and maximum allowed speed.

## Requirements

### Requirement: ETA and remaining distance displayed
During active navigation, the system SHALL display the estimated arrival time and remaining distance to the destination.

#### Scenario: ETA shown
- **WHEN** navigation is active
- **THEN** the estimated arrival time SHALL be displayed (e.g., "ETA: 14:32")
- **AND** the remaining distance SHALL be displayed (e.g., "5.3 km")

#### Scenario: ETA updates
- **WHEN** `NavigationListener.onArrivalEstimate()` is called
- **THEN** the displayed ETA SHALL update to the new estimate

### Requirement: Current speed displayed
During active navigation, the system SHALL display the current vehicle speed. When the current speed exceeds the maximum allowed speed by 5 km/h or more, the speed value SHALL be shown in red to indicate overspeed.

#### Scenario: Speed shown
- **WHEN** navigation is active
- **THEN** the current speed SHALL be displayed (e.g., "48 km/h")

#### Scenario: Speed shown in normal color
- **WHEN** navigation is active
- **AND** the current speed is known
- **AND** the max allowed speed is unknown or current speed ≤ max allowed speed + 5
- **THEN** the current speed SHALL be displayed in the normal text color

#### Scenario: Speed shown in red when exceeding max by 5+ km/h
- **GIVEN** the max allowed speed is 50 km/h
- **WHEN** the current speed is 56 km/h (exceeds max by 6 km/h)
- **THEN** the current speed SHALL be displayed in red

#### Scenario: Speed shown in normal color when within limit
- **GIVEN** the max allowed speed is 50 km/h
- **WHEN** the current speed is 55 km/h (exceeds max by 5 km/h)
- **THEN** the current speed SHALL be displayed in the normal text color

#### Scenario: Speed updates
- **WHEN** `NavigationListener.onCurrentSpeed()` is called
- **THEN** the displayed speed SHALL update
- **AND** the color SHALL update accordingly (red if overspeed, normal otherwise)

### Requirement: Max allowed speed displayed
When the maximum allowed speed for the current road is known, the system SHALL display it alongside the current speed.

#### Scenario: Max speed shown
- **WHEN** `NavigationListener.onMaxAllowedSpeed()` is called with a positive value
- **THEN** the maximum allowed speed SHALL be displayed (e.g., "Max: 50 km/h")

#### Scenario: Max speed hidden when unknown
- **WHEN** `NavigationListener.onMaxAllowedSpeed()` is called with a negative value
- **THEN** the max speed display SHALL be hidden

### Requirement: Navigation state overlay hides on stop
When navigation stops, all navigation state displays SHALL be hidden.

#### Scenario: Overlay hidden on stop
- **WHEN** navigation stops
- **THEN** the ETA, distance, speed, and max speed displays SHALL be hidden
