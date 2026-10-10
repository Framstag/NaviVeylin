# Spec Delta

## ADDED Requirements

### Requirement: Reroute calculation progress is exposed
While a reroute is in progress the system SHALL expose the reroute calculation's progress as the same percentage an initial acquisition exposes, and SHALL present the reroute as a progress notice rather than silence.

#### Scenario: Progress during a reroute
- **WHEN** the routing engine reports progress while a reroute is being calculated
- **THEN** the navigation state SHALL carry that percentage
- **AND** the reroute SHALL still be reported as in progress

#### Scenario: Reroute progress notice
- **WHEN** a reroute is still running after the car notice delay has elapsed
- **THEN** the car session SHALL show the calculation notice (see `route-calculation-feedback`)

#### Scenario: Reroute state clears the progress
- **WHEN** the reroute completes or navigation is stopped while it runs
- **THEN** `isRerouting` SHALL be `false`
- **AND** the state SHALL report no calculation in progress
