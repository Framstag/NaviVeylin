# navigation-state-display Specification

## MODIFIED Requirements

### Requirement: Current speed displayed

During active navigation, the system SHALL display the current vehicle speed as an on-map speed widget. When the current speed exceeds the maximum allowed speed by 5 km/h or more, the speed value SHALL be shown in red to indicate overspeed.

#### Scenario: Speed shown

- **WHEN** navigation is active
- **THEN** the current speed SHALL be displayed (e.g., "48 km/h") in the on-map speed widget

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

When the maximum allowed speed for the current road is known, the system SHALL display it as a round sign below the current-speed widget.

#### Scenario: Max speed shown

- **WHEN** `NavigationListener.onMaxAllowedSpeed()` is called with a positive value
- **THEN** the maximum allowed speed SHALL be displayed (e.g., "50") as a round sign below the current-speed widget

#### Scenario: Max speed hidden when unknown

- **WHEN** `NavigationListener.onMaxAllowedSpeed()` is called with a negative value
- **THEN** the max speed display SHALL be hidden

### Requirement: Navigation state overlay hides on stop

When navigation stops, all navigation state displays SHALL be hidden.

#### Scenario: Overlay hidden on stop

- **WHEN** navigation stops
- **THEN** the ETA, distance, and speed displays SHALL be hidden
