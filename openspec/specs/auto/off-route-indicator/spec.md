# auto/off-route-indicator Specification

## Purpose
Gives the car display its own statement that the vehicle is no longer on the planned route: the host-rendered instruction panel says so while the vehicle is off route, and returns to the route's manoeuvre cue once it is back on one.

## Requirements

### Requirement: The car instruction panel states when the vehicle is off the planned route

While a navigation session is active and the vehicle is known to be off the planned route, the car session SHALL make the host instruction panel state that condition, in the surface's localized wording, and SHALL NOT present a manoeuvre cue for a road the vehicle is not on. The panel SHALL keep naming the route's next manoeuvre as its next step. This requirement is the stated exception to the panel rules of `auto/navigation-view`: those describe the panel while the vehicle is on its route.

#### Scenario: Off-route state in the instruction panel

- **WHEN** navigation is active and the vehicle is known to be off the planned route
- **THEN** the instruction panel's current step SHALL state the off-route condition
- **AND** it SHALL NOT carry the previous manoeuvre cue or its last distance
- **Case**: `auto: NavigationTemplateMapperTest.theOffRouteStateReplacesTheManoeuvreCue`

#### Scenario: The coming manoeuvre stays readable

- **WHEN** the instruction panel states the off-route condition and the route still has a manoeuvre after the current position
- **THEN** the panel's next step SHALL name that manoeuvre with its own turn type
- **Case**: `auto: NavigationTemplateMapperTest.theNextStepSurvivesTheOffRouteState`

#### Scenario: A reroute in flight keeps the panel in the off-route state

- **WHEN** the vehicle is off route and a route calculation is in progress
- **THEN** the panel SHALL keep stating the off-route condition
- **AND** the calculation's progress SHALL NOT be presented in the panel
- **Case**: `auto: NavigationTemplateMapperTest.aCalculationInFlightLeavesThePanelInTheOffRouteState`

#### Scenario: The state ends when the vehicle is back on a route

- **WHEN** the vehicle is back on the planned route, or the rerouted route has been adopted
- **THEN** the instruction panel SHALL return to the route's manoeuvre cue
- **Case**: `auto: NavigationTemplateMapperTest.theManoeuvreCueReturnsWhenBackOnRoute`

#### Scenario: No off-route state outside navigation

- **WHEN** navigation is not active
- **THEN** no off-route condition SHALL be presented
- **Case**: `auto: NavigationTemplateMapperTest.noOffRouteStateWithoutNavigation`
