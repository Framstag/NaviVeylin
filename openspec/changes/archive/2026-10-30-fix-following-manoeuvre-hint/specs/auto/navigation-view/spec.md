## MODIFIED Requirements

### Requirement: Current and next step in host instruction panel

The system SHALL render the next-turn instruction (current step) and the following turn (next step) in the host-rendered navigation instruction panel of the `NavigationTemplate`, showing a turn-type icon, the distance to the turn and the target street name, and SHALL update them as navigation progresses. The current-step distance SHALL NOT freeze at 0 m while the step is still ahead, including after a reroute. Each step's icon SHALL identify its own exit when that step is a roundabout exit carrying an ordinal. The next step SHALL be the following manoeuvre of the route's step list, never the next route node.

#### Scenario: Current step shown during navigation

- **WHEN** navigation is active and next-turn data is available
- **THEN** the host instruction panel shows the current step with a turn-type icon, the distance to the turn and the target street name

#### Scenario: Next-next turn shown when present

- **WHEN** the route has at least one turn after the current step
- **THEN** the host instruction panel shows the next step (next-next turn) with its own turn type and target street
- **Case**: `auto: NavigationTemplateMapperTest` "the next step is the following manoeuvre, not the next route node"

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

#### Scenario: Each step carries its own exit number

- **WHEN** the current step and the next step are both roundabout exits with different ordinals
- **THEN** each step's maneuver SHALL identify its own exit
- **AND** neither SHALL show the other step's ordinal
- **Case**: `auto: NavigationTemplateMapperTest` "current and next steps carry different exit numbers"

#### Scenario: The roundabout exit is the next step before the ring is entered

- **WHEN** the current step is a roundabout entry and the route leaves that roundabout at the following manoeuvre
- **THEN** the panel's next step SHALL be that leave, carrying the exit ordinal the route takes
- **Case**: `auto: NavigationTemplateMapperTest` "the exit ordinal of the following manoeuvre is on the next step"
