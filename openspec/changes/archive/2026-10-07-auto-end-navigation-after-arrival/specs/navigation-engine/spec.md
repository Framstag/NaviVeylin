# Spec Delta

## ADDED Requirements

### Requirement: Arrival is part of the shared navigation state

The shared navigation state SHALL report whether the running navigation reached its destination. The engine SHALL set that fact when the native navigation engine reports the target reached, SHALL keep it while a reroute replaces the route, and SHALL clear it when a new navigation starts and when navigation stops.

#### Scenario: Destination reached is reported in the shared state

- **WHEN** the native navigation engine reports the target reached while navigation is active
- **THEN** the shared navigation state SHALL report the destination as reached
- **AND** guidance SHALL otherwise be unchanged: navigation stays active, the step list, route geometry and position updates are unaffected

#### Scenario: Arrival is observable without a surface

- **WHEN** the destination was reached and no surface is displaying the navigation
- **THEN** the arrival fact SHALL still be readable from the shared state until it is cleared

#### Scenario: Arrival survives a reroute

- **WHEN** a reroute replaces the route after the destination was reached
- **THEN** the shared state SHALL still report the destination as reached while the new route runs

#### Scenario: Arrival cleared on a new navigation

- **WHEN** navigation starts on a newly acquired route that is not a reroute of the running session
- **THEN** the shared state SHALL NOT report the destination as reached

#### Scenario: Arrival cleared on navigation stop

- **WHEN** navigation stops, whether the user stopped it or a surface ended it
- **THEN** the shared state SHALL NOT report the destination as reached

#### Scenario: Late arrival report after a stop

- **WHEN** the native navigation engine reports the target reached for a session that was already stopped
- **THEN** the shared state SHALL NOT report the destination as reached
