## MODIFIED Requirements

### Requirement: Current driving speed shown

The system SHALL display the current driving speed on the free-driving view, on the right side below the compass. When the current speed exceeds the current road's speed limit, the speed readout SHALL be shown in the warning state — a red badge background with white text — matching the overspeed visual of the navigation speed badge.

#### Scenario: Speed shown below compass

- **WHEN** the free-driving view is visible and the GPS fix reports a ground speed
- **THEN** the view shows the current speed (km/h) below the compass rose on the right side

#### Scenario: Speed hidden when unknown

- **WHEN** the GPS fix does not report a ground speed
- **THEN** the view shows no speed readout

#### Scenario: Speed derived from movement without GPS speed

- **WHEN** the GPS fix has no speed (e.g. GPX track replay) but the vehicle moved since the previous fix
- **THEN** the speed readout and auto-zoom use the speed implied by the movement between fixes

#### Scenario: Overspeed warning in free driving

- **WHEN** the free-driving view is visible and the current speed exceeds the road's speed limit
- **THEN** the speed readout is shown with a red background and white text

#### Scenario: Normal colors at or below the limit

- **WHEN** the free-driving view is visible and the current speed does not exceed the road's speed limit
- **THEN** the speed readout is shown with the normal badge background and white text
