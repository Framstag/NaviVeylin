# Delta: auto/navigation-view (modified: distance-to-turn must not freeze at 0 m)

## MODIFIED Requirements

### Requirement: Next turn maneuver in host instruction panel

The system SHALL render the next turn instruction in the host-rendered navigation instruction panel of the `NavigationTemplate`, showing a turn-type icon, the distance to the turn and the target street name, and SHALL update it as navigation progresses. The displayed distance SHALL remain a true remaining distance to the maneuver: it SHALL count down as the vehicle approaches and SHALL NOT display 0 m while the maneuver is still ahead and the vehicle is moving, including after a reroute.

#### Scenario: Maneuver shown during navigation

- **WHEN** navigation is active and next-turn data is available
- **THEN** the host instruction panel shows a maneuver with the next turn's type icon, distance and target street name

#### Scenario: Maneuver updates on approach

- **WHEN** the vehicle approaches the next turn and the next-turn data changes
- **THEN** the host maneuver updates to the new turn instruction

#### Scenario: Distance counts down to the maneuver

- **WHEN** navigation is active, the vehicle is moving and the next maneuver is still ahead on the route
- **THEN** the shown distance decreases as the vehicle approaches
- **AND** it SHALL NOT show 0 m while the maneuver node is still ahead of the vehicle
- **AND** when the shown distance is 0 m, the maneuver SHALL be the one the vehicle is turning into or already passing

#### Scenario: Distance correct after reroute

- **WHEN** a reroute replaces the route while the vehicle is off the old route
- **THEN** the instruction panel shows the new route's next maneuver with a non-zero remaining distance when that maneuver is still ahead
- **AND** it SHALL NOT carry over the previous route's maneuver or a frozen 0 m distance

#### Scenario: No staled instruction when reroute suppressed

- **WHEN** the vehicle is off the planned route, a reroute would be required but is suppressed (cooldown, poor accuracy, tunnel guard)
- **THEN** the instruction panel SHALL NOT keep displaying the previous maneuver with its last distance
- **AND** it SHALL either show a current, up-to-date instruction or clear the maneuver until valid data is available

#### Scenario: No maneuver when not navigating

- **WHEN** navigation is not active
- **THEN** the host instruction panel shows no maneuver

### Requirement: Current and next step in host instruction panel

The system SHALL render the next-turn instruction (current step) and the following turn (next step) in the host-rendered navigation instruction panel of the `NavigationTemplate`, showing a turn-type icon, the distance to the turn and the target street name, and SHALL update them as navigation progresses. The current-step distance SHALL NOT freeze at 0 m while the step is still ahead, including after a reroute.

#### Scenario: Current step shown during navigation

- **WHEN** navigation is active and next-turn data is available
- **THEN** the host instruction panel shows the current step with a turn-type icon, the distance to the turn and the target street name

#### Scenario: Next-next turn shown when present

- **WHEN** the route has at least one turn after the current step
- **THEN** the host instruction panel shows the next step (next-next turn) with its own turn type and target street

#### Scenario: Steps update on approach

- **WHEN** the vehicle approaches the next turn and the navigation data changes
- **THEN** the host instruction panel updates to the new current and next steps

#### Scenario: Step distance not frozen at zero after reroute

- **WHEN** a reroute replaced the route and the current step of the new route is still ahead
- **THEN** the current step's distance counts down normally from a non-zero value
- **AND** it SHALL NOT remain at 0 m while the vehicle is still approaching the step

#### Scenario: No step when not navigating

- **WHEN** navigation is not active
- **THEN** the host instruction panel shows no step information

#### Scenario: Distance rounded for display

- **WHEN** the distance to the current step is shown
- **THEN** it is rounded for display: exact up to 50 m, multiples of 50 m up to 1 km, one decimal km above (meters below 1 km, kilometers above)
