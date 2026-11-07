# Spec Delta: auto/navigation-view

## MODIFIED Requirements

### Requirement: Route description screen

The system SHALL provide a route description screen reachable from the navigation view that lists the route's remaining driving decisions — the rows the shared row policy derives from the remaining steps — from the current decision to the destination, with the current decision marked. Each row SHALL state the distance of its own leg. A listed roundabout exit SHALL identify its exit ordinal in the row's image.

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

#### Scenario: The list is the derived decisions, not the steps

- **WHEN** the remaining route carries more published steps than driving decisions
- **THEN** the screen SHALL list one row per derived decision, the same rows the phone route list derives
- **AND** it SHALL NOT list one row per published step
- **Case**: `auto: RouteDescriptionScreenTest` "the car list has one row per derived decision"

#### Scenario: A row states its own leg

- **WHEN** a row is listed
- **THEN** its distance SHALL be that row's own leg — the run into its manoeuvre
- **AND** it SHALL NOT be the distance from the route's start
- **Case**: `auto: RouteDescriptionScreenTest` "a row's distance is its own leg"

#### Scenario: A facts-only step earns no car row

- **WHEN** a remaining step carries only informational values — a maximum speed, lanes, a way type, crossing ways, a junction or a destination
- **THEN** no row SHALL describe that step
- **AND** its leg SHALL be part of the row above it
- **Case**: `auto: RouteDescriptionScreenTest` "an informational step earns no car row"
