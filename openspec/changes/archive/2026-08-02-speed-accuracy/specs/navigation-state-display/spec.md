## MODIFIED Requirements

### Requirement: Current speed displayed
During active navigation, the system SHALL display the current vehicle speed. When the current speed exceeds the maximum allowed speed by 5 km/h or more, the speed value SHALL be shown in red to indicate overspeed.

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
