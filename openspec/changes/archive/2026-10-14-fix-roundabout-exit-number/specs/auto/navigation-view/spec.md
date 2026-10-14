# Spec Delta

## MODIFIED Requirements

### Requirement: Next turn maneuver in host instruction panel

The system SHALL render the next turn instruction in the host-rendered navigation instruction panel of the `NavigationTemplate`, showing a turn-type icon, the distance to the turn and the target street name, and SHALL update it as navigation progresses. The displayed distance SHALL remain a true remaining distance to the maneuver: it SHALL count down as the vehicle approaches and SHALL NOT display 0 m while the maneuver is still ahead and the vehicle is moving, including after a reroute. For a roundabout exit the icon SHALL identify which exit the route takes.

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

#### Scenario: A roundabout exit maneuver carries the exit number

- **WHEN** navigation is active and the next maneuver is a roundabout exit carrying an ordinal
- **THEN** the host panel's maneuver SHALL identify that exit by its ordinal
- **AND** the ordinal SHALL be the one the panel's cue text names
- **Case**: `auto: NavigationTemplateMapperTest` "a roundabout exit maneuver carries the exit number"

### Requirement: Current and next step in host instruction panel

The system SHALL render the next-turn instruction (current step) and the following turn (next step) in the host-rendered navigation instruction panel of the `NavigationTemplate`, showing a turn-type icon, the distance to the turn and the target street name, and SHALL update them as navigation progresses. The current-step distance SHALL NOT freeze at 0 m while the step is still ahead, including after a reroute. Each step's icon SHALL identify its own exit when that step is a roundabout exit carrying an ordinal.

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

#### Scenario: Each step carries its own exit number

- **WHEN** the current step and the next step are both roundabout exits with different ordinals
- **THEN** each step's maneuver SHALL identify its own exit
- **AND** neither SHALL show the other step's ordinal
- **Case**: `auto: NavigationTemplateMapperTest` "current and next steps carry different exit numbers"

### Requirement: Route description screen

The system SHALL provide a route description screen reachable from the navigation view that lists every remaining route instruction from the current step to the destination, with the current step marked. A listed roundabout exit SHALL identify its exit ordinal in the row's image.

#### Scenario: Route description opened from navigation view

- **WHEN** the user taps the route-description action on the navigation view while navigating
- **THEN** a route description screen opens listing all remaining instructions in route order, starting with the current step

#### Scenario: Current step highlighted

- **WHEN** the route description screen is shown
- **THEN** the current step is visually marked and the list advances as the vehicle progresses

#### Scenario: List scrollable

- **WHEN** the route has more instructions than fit on the display
- **THEN** the route description list scrolls to reveal the remaining instructions

#### Scenario: Back returns to navigation view

- **WHEN** the user presses back on the route description screen
- **THEN** the screen returns to the navigation view and navigation keeps running

#### Scenario: A roundabout exit row shows its exit number

- **WHEN** the route description screen lists a roundabout exit carrying an ordinal
- **THEN** that row's image SHALL carry that exit's number
- **AND** it SHALL NOT carry another row's number
- **Case**: `auto: RouteDescriptionScreenTest` "a roundabout exit row shows its exit number"

## ADDED Requirements

### Requirement: A roundabout exit is identified by its ordinal

For a roundabout exit carrying a known ordinal the system SHALL present both identifications: the
number-bearing roundabout maneuver type with that ordinal set on it, and the app's own numbered glyph. For a
roundabout exit with no known ordinal the system SHALL use the maneuver type without a number and the app's
glyph. The system SHALL NOT present a number-bearing type without a number.

#### Scenario: A known ordinal selects the number-bearing type

- **WHEN** a roundabout exit maneuver carries exit ordinal 3
- **THEN** the host maneuver SHALL be of a number-bearing roundabout type
- **AND** it SHALL carry the ordinal to the host
- **Case**: `auto: NavigationTemplateMapperTest` "a known exit ordinal selects a number-bearing maneuver type"

#### Scenario: Both identifications are presented

- **WHEN** a roundabout exit maneuver carries exit ordinal 3
- **THEN** the number-bearing type SHALL carry the ordinal
- **AND** the app's numbered glyph SHALL also be supplied for that maneuver
- **AND** neither identification SHALL be withheld on the assumption that the host draws the other
- **Case**: `auto: NavigationTemplateMapperTest` "a roundabout exit asks the host for the number as well as our icon"

#### Scenario: An unknown ordinal falls back without throwing

- **WHEN** a roundabout exit maneuver carries no ordinal
- **THEN** building the step SHALL succeed
- **AND** the maneuver SHALL be of the roundabout type that carries no number
- **Case**: `auto: NavigationTemplateMapperTest` "an unknown exit ordinal builds a step instead of throwing"

#### Scenario: Circulation direction selects the clockwise or counter-clockwise type

- **WHEN** a roundabout exit maneuver carries a counter-clockwise circulation direction
- **THEN** the maneuver type SHALL be the counter-clockwise one
- **AND** it SHALL NOT be the clockwise one
- **Case**: `auto: NavigationTemplateMapperTest` "a counter-clockwise roundabout uses the counter-clockwise type"

#### Scenario: The cue text stays at parity with the phone

- **WHEN** the host draws its own numbered roundabout glyph for a roundabout exit
- **THEN** the step's cue text SHALL be the same string the phone shows for that instruction
- **AND** only the glyph source SHALL differ between the variants
- **Case**: `auto: NavigationTemplateMapperTest` "the host-numbered roundabout keeps the shared cue text"
